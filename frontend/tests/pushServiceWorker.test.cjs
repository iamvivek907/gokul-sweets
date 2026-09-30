const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
function worker() {
    const listeners = {}, shown = [], opened = [], entries = new Map();
    const self = {location: {origin: 'https://dev.gokulsweets.in'}, addEventListener(name, listener) {listeners[name] = listener;},
        registration: {async showNotification(title, options) {shown.push({title, ...options});}},
        clients: {async matchAll() {return [];}, async openWindow(url) {opened.push(url);}}};
    const caches = {async open() {return {async match(key) {const value = entries.get(key.url); return value ? new Response(value) : undefined;},
        async put(key, response) {entries.set(key.url, await response.text());}};}};
    vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../public/sw.js'), 'utf8'), {self, caches, URL, Request, Response, Promise, Array, String});
    return {listeners, shown, opened};
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
