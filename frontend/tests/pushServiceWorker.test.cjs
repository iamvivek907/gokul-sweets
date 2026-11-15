const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
function worker(clients = [], cacheFailure = false) {
    const listeners = {}, shown = [], opened = [], entries = new Map(), active = new Map();
    const self = {location: {origin: 'https://dev.gokulsweets.in'}, addEventListener(name, listener) {listeners[name] = listener;},
        registration: {
            async showNotification(title, options) {
                shown.push({title, ...options});
                active.set(options.tag, {tag: options.tag, close() {active.delete(options.tag);}});
            },
            async getNotifications() {return [...active.values()];}
        },
        clients: {async matchAll() {if(clients instanceof Error)throw clients;return clients;}, async openWindow(url) {opened.push(url);}}};
    const caches = {async open() {if (cacheFailure) throw new Error("Cache unavailable"); return {async match(key) {const value = entries.get(key.url); return value ? new Response(value) : undefined;},
        async put(key, response) {entries.set(key.url, await response.text());}};}};
    vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../public/sw.js'), 'utf8'), {self, caches, URL, Request, Response, Promise, Array, String});
    return {listeners, shown, opened, active, entries};
}
test('replayed/concurrent push uses one generic notification and exact safe inbox destination', async () => {
    const runtime = worker();
    const promises = [];
    for (let i = 0; i < 2; i++) runtime.listeners.push({data: {json: () => ({eventId: '42', title: 'Secret payment', body: 'Customer phone', url: 'https://attacker.example'})}, waitUntil(value) {promises.push(value);}});
    await Promise.all(promises);
    assert.equal(runtime.shown.length, 1);
    assert.equal(runtime.shown[0].title, 'Gokul Sweets');
    assert.equal(runtime.shown[0].body.includes('Customer phone'), false);
    assert.equal(runtime.shown[0].data.url, '/profile#account-notifications');
    let clicked;
    runtime.listeners.notificationclick({notification: {close() {}}, waitUntil(value) {clicked = value;}});
    await clicked;
    assert.deepEqual(runtime.opened, ['https://dev.gokulsweets.in/profile#account-notifications']);
});
test('malformed push cannot navigate arbitrary URLs or cause duplicate alarms', () => {
    const runtime = worker();
    runtime.listeners.push({data: {json: () => ({eventId: 'bad'})}, waitUntil() {throw new Error('Unexpected push');}});
    assert.equal(runtime.shown.length, 0);
});
test('closed-app push shows stage-specific copy and opens only the exact local order', async () => {
    const runtime = worker();
    let pending;
    runtime.listeners.push({data: {json: () => ({eventId: '43', title: 'Your order is ready for pickup', body: 'Order GS-43 · Main branch. Ready for your booked pickup.', url: '/orders/GS-43'})}, waitUntil(value) {pending = value;}});
    await pending;
    assert.equal(runtime.shown[0].title, 'Your order is ready for pickup');
    assert.match(runtime.shown[0].body, /Main branch/);
    assert.equal(runtime.shown[0].badge, '/notification-badge.svg');
    runtime.listeners.notificationclick({notification: {data: runtime.shown[0].data, close() {}}, waitUntil(value) {pending = value;}});
    await pending;
    assert.deepEqual(runtime.opened, ['https://dev.gokulsweets.in/orders/GS-43']);
});
test('staff push has a distinct event namespace and safe exact admin destination', async () => {
    const runtime = worker(); let pending;
    for (const payload of [{eventId: '44', title: 'Customer ready', body: 'Collect order', url: '/orders/GS-44'},
        {eventId: 'staff:44', title: 'Preparation is due', body: 'Branch A: start preparing GS-44', url: '/admin/orders/GS-44'}]) {
        runtime.listeners.push({data: {json: () => payload}, waitUntil(value) {pending = value;}}); await pending;
    }
    assert.equal(runtime.shown.length, 2);
    assert.equal(runtime.shown[1].renotify, true); assert.equal(runtime.shown[1].requireInteraction, true);
    assert.equal(runtime.shown[1].title, 'Preparation is due'); assert.equal(runtime.shown[1].data.staff, true);
    runtime.listeners.notificationclick({notification: {data: runtime.shown[1].data, close() {}}, waitUntil(value) {pending = value;}}); await pending;
    assert.deepEqual(runtime.opened, ['https://dev.gokulsweets.in/admin/orders/GS-44']);
});

test('order stages replace the same device alert and optional review uses a safe destination', async () => {
    const runtime = worker(); let pending;
    for (const [eventId, url] of [['81','/orders/GS-81'], ['82','/orders/GS-81#order-review'], ['83','/orders/GS-83']]) {
        runtime.listeners.push({data: {json: () => ({eventId, title:'Order update',body:'Details',url})}, waitUntil(value) {pending=value;}});
        await pending;
    }
    assert.equal(runtime.shown[0].tag, runtime.shown[1].tag);
    assert.notEqual(runtime.shown[1].tag, runtime.shown[2].tag);
    assert.equal(runtime.shown[1].renotify, false);
    runtime.listeners.notificationclick({notification:{data:runtime.shown[1].data,close(){}},waitUntil(value){pending=value;}});
    await pending;
    assert.deepEqual(runtime.opened,['https://dev.gokulsweets.in/orders/GS-81#order-review']);
});

test('staff push wakes only admin clients without losing notification dedupe on client failure', async () => {
    const messages=[];
    for(const clients of [
        [{url:'https://dev.gokulsweets.in/admin/orders',postMessage(value){messages.push(value.type);}},
         {url:'https://dev.gokulsweets.in/menu',postMessage(){throw new Error('Customer must not receive staff wake');}},
         {url:'https://dev.gokulsweets.in/admin/closed',postMessage(){throw new Error('Client closed');}}],
        new Error('Client discovery unavailable')]) {
        const runtime=worker(clients);
        for(let i=0;i<2;i++) {
            let pending;
            runtime.listeners.push({data:{json:()=>({eventId:'staff:99',title:'Preparation is due',body:'Start KOT',url:'/admin/orders/GS-99'})},waitUntil(value){pending=value;}});
            await pending;
        }
        assert.equal(runtime.shown.length,1);
        assert.equal(runtime.shown[0].silent,false);
    }
    assert.deepEqual(messages,['gokul-staff-alert']);
});
test('marketing pushes retain playful copy, deduplicate retries and open only their branch', async () => {
    const runtime = worker();
    const pending = [];
    const payload = {eventId: 'marketing:42', title: 'Chai needs a plus-one ☕', body: 'Explore Gokul snacks.', url: '/branches/1'};
    for (let i = 0; i < 2; i++) runtime.listeners.push({data: {json: () => payload}, waitUntil(value) {pending.push(value);}});
    await Promise.all(pending);
    assert.equal(runtime.shown.length, 1);
    assert.equal(runtime.shown[0].title, payload.title);
    assert.equal(runtime.shown[0].data.marketing, true);
    assert.equal(runtime.shown[0].tag, 'gokul-marketing:42');
    let clicked;
    runtime.listeners.notificationclick({notification: {data: runtime.shown[0].data, close() {}}, waitUntil(value) {clicked = value;}});
    await clicked;
    assert.deepEqual(runtime.opened, ['https://dev.gokulsweets.in/branches/1']);
});
test('marketing IDs cannot collide with order IDs or open unsafe destinations', async () => {
    const runtime = worker();
    for (const url of ['https://evil.example', '/admin/orders/1', '//evil.example', '/branches/1?redirect=evil', '/orders/1']) {
        let pending;
        runtime.listeners.push({data: {json: () => ({eventId: 'marketing:7', title: 'No', url})}, waitUntil(value) {pending = value;}});
        await pending;
    }
    assert.equal(runtime.shown.length, 0);
    let pending;
    runtime.listeners.push({data: {json: () => ({eventId: '7', title: 'Order ready', url: '/orders/7'})}, waitUntil(value) {pending = value;}});
    await pending;
    runtime.listeners.push({data: {json: () => ({eventId: 'marketing:7', title: 'Snack time', url: '/branches/1'})}, waitUntil(value) {pending = value;}});
    await pending;
    assert.equal(runtime.shown.length, 2);
});


test('cache failure still shows a pickup alert and wakes only customer clients', async () => {
    const wakes = [];
    const runtime = worker([
        {url: 'https://dev.gokulsweets.in/menu', postMessage: value => wakes.push(value.type)},
        {url: 'https://dev.gokulsweets.in/admin/orders', postMessage: value => wakes.push(value.type)}
    ], true);
    let pending;
    runtime.listeners.push({data: {json: () => ({eventId: '431', title: 'Ready for pickup', url: '/orders/GS-431'})}, waitUntil(value) {pending = value;}});
    await pending;
    assert.equal(runtime.shown[0].title, 'Ready for pickup');
    assert.deepEqual(wakes, ['gokul-customer-alert']);
});

test('separate staff reminders for the same order have distinct device tags, and retries deduplicate', async () => {
    const runtime = worker();
    for (const eventId of ['staff:501', 'staff:502', 'staff:502']) {
        let pending;
        runtime.listeners.push({data: {json: () => ({eventId, title: 'Start preparation', url: '/admin/orders/GS-501'})}, waitUntil(value) {pending = value;}});
        await pending;
    }
    assert.equal(runtime.shown.length, 2);
    assert.notEqual(runtime.shown[0].tag, runtime.shown[1].tag);
});


test('a late new-order alert cannot replace a newer preparation reminder', async () => {
    for (const cacheFailure of [false, true]) {
        const runtime = worker([], cacheFailure);
        const send = async (id, title, order = 'GS-001') => {
            let pending;
            runtime.listeners.push({
                data: {json: () => ({eventId: `staff:${id}`, title, url: `/admin/orders/${order}`})},
                waitUntil(value) {pending = value;}
            });
            await pending;
        };
        await send('10000000000000000000', 'Preparation is due');
        await send('9999999999999999999', 'New paid order');
        assert.deepEqual(runtime.shown.map(notice => notice.title), ['Preparation is due']);
        const notice = runtime.active.values().next().value;
        if (!cacheFailure) notice.close();
        if (!cacheFailure) await send('9999999999999999998', 'Another delayed reminder');
        assert.equal(runtime.shown.length, 1);
        await send('9999999999999999998', 'Another order', 'GS-002');
        assert.equal(runtime.shown.length, 2);
        await send('10000000000000000001', 'Preparation is overdue');
        assert.equal([...runtime.active.keys()].filter(tag => tag.includes('GS-001')).length, 1);
    }
});


test('a queued alert from the previous account stays hidden after a shared-phone switch', async () => {
    const runtime = worker();
    const key = 'https://dev.gokulsweets.in/.__gokul-order-push/staff';
    const send = async (id, registrationId) => {
        let pending;
        runtime.listeners.push({
            data: {json: () => ({eventId: `staff:${id}`, title: 'Prepare order', url: '/admin/orders/GS-901', registrationId})},
            waitUntil(value) {pending = value;}
        });
        await pending;
    };
    runtime.entries.set(key, JSON.stringify({owner: 'previous', registrationId: 'old-device'}));
    await send(1, 'old-device');
    runtime.entries.set(key, JSON.stringify({owner: 'current', registrationId: null}));
    await send(2, 'old-device');
    await send(3);
    runtime.entries.set(key, JSON.stringify({owner: 'current', registrationId: 'new-device'}));
    await send(4, 'new-device');
    assert.deepEqual(runtime.shown.map(notice => notice.data.registrationId), ['old-device', 'new-device']);
});
