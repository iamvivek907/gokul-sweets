const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');

function load(file, globals) {
    const source = fs.readFileSync(path.join(__dirname, '..', file), 'utf8');
    const code = ts.transpileModule(source, {compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022}}).outputText;
    const exports = {};
    vm.runInNewContext(code, {exports, Response, Headers, AbortSignal, ...globals});
    return exports;
}

function fixture(binding, {readFails = false, writeFails = false, createdRegistration = true, storageFails = false} = {}) {
    const store = {
        async match() { return binding ? new Response(JSON.stringify(binding)) : undefined; },
        async put(key, value) {
            if (writeFails) throw new Error('Storage quota exceeded');
            binding = await value.json();
        },
        async delete() { binding = null; return true; }
    };
    const cached = load('lib/orderPushBinding.ts', {caches: {async open() {
        if (readFails) throw new Error('CacheStorage unavailable');
        return store;
    }}});
    const requests = [];
    const saved = new Map();
    const deviceId = 'd1779266-3814-48cf-8f6d-8c23b832c1c3';
    let unsubscribes = 0;
    const subscription = {endpoint: 'https://example.invalid/phone', toJSON: () => ({keys: {p256dh: 'key', auth: 'auth'}}),
        async unsubscribe() {unsubscribes++; return true;}};
    const navigator = {serviceWorker: {async register() {
        return {active: true, pushManager: {
            async getSubscription() {return createdRegistration ? null : subscription;},
            async subscribe() {return subscription;}
        }};
    }}};
    const localStorage = {getItem: key => saved.get(key) ?? null, removeItem: key => saved.delete(key),
        setItem(key, value) {if (storageFails) throw new Error('Storage blocked'); saved.set(key, value);}};
    const api = load('services/staffAlertsApi.ts', {navigator, localStorage, Notification: {permission: 'granted'}, require: module => {
        if (module.endsWith('/orderPushBinding')) return cached;
        if (module.endsWith('/notificationAlerts')) return {applicationServerKey: () => new Uint8Array(65)};
        if (module.endsWith('/adminApi')) return {async adminFetch(url, authorization, init) {
            requests.push({url, method: init.method, authorization, body: init.body && JSON.parse(init.body), binding});
            return init.method === 'DELETE' ? new Response(null, {status: 204})
                : new Response(JSON.stringify({id: deviceId, created: createdRegistration}));
        }};
        return {};
    }});
    return {requests, restore: () => api.syncStaffAlertDevice(subscription, 12, new AbortController().signal, 'staff-phone'),
        bind: cached.bindStaffOrderPush, switchOwner: cached.switchOrderPushOwner, read: () => binding,
        enable: () => api.enableStaffDeviceAlerts({staffId: 12, applicationServerKey: 'test'}),
        deviceId, saved, unsubscribes: () => unsubscribes};
}

test('restores a staff phone even when CacheStorage is unavailable', async () => {
    const phone = fixture(null, {readFails: true});
    await phone.restore();
    assert.equal(phone.requests.length, 1);
    assert.equal(phone.requests[0].authorization, 'staff-session:12');
    assert.equal(phone.requests[0].body.expectedStaffId, 12);
});

test('a failed cache write removes only a matching empty marker before server restore', async () => {
    const phone = fixture({owner: '12', registrationId: null}, {writeFails: true});
    await phone.restore();
    assert.equal(phone.requests[0].binding, null);

    const signedOut = fixture({owner: '', registrationId: null}, {writeFails: true});
    const signal = new AbortController().signal;
    await assert.rejects(signedOut.switchOwner('staff', '12', signal, true, ''), /Storage quota/);
    await signedOut.restore();
    assert.equal(signedOut.requests[0].binding, null);

    const other = fixture({owner: '13', registrationId: 'another-phone'}, {writeFails: true});
    await assert.rejects(other.restore(), /active staff account changed/);
    assert.equal(other.requests.length, 0);
    assert.deepEqual(other.read(), {owner: '13', registrationId: 'another-phone'});
});

test('a stale staff tab cannot stage, confirm, or switch across a newer handoff', async () => {
    const phone = fixture({owner: '12', registrationId: null});
    const signal = new AbortController().signal;
    await phone.switchOwner('staff', '13', signal, true, '12');
    assert.equal(await phone.bind('13', 'current-phone', signal), true);

    assert.equal(await phone.switchOwner('staff', '12', signal, true, '12'), false);
    await assert.rejects(phone.restore(), /active staff account changed/);
    assert.equal(await phone.bind('12', 'staff-phone', signal), false);
    assert.equal(phone.requests.length, 0);
    assert.deepEqual(phone.read(), {owner: '13', registrationId: 'current-phone'});
});

test('failed setup revokes the new registration and unsubscribes without saving its ID', async () => {
    const owner = {owner: '13', registrationId: 'another-phone'};
    const phone = fixture(owner, {writeFails: true});
    await assert.rejects(phone.enable(), /active staff account changed/);
    assert.deepEqual(phone.requests.map(request => request.method), ['POST', 'DELETE']);
    assert.ok(phone.requests[1].url.endsWith(phone.deviceId));
    assert.equal(phone.requests[1].authorization, 'staff-session:12');
    assert.equal(phone.unsubscribes(), 1);
    assert.equal(phone.saved.size, 0);
    assert.deepEqual(phone.read(), owner);
});

test('a failed local save clears the new binding and preserves a reused registration', async () => {
    const phone = fixture({owner: '12', registrationId: null}, {storageFails: true});
    await assert.rejects(phone.enable(), /could not save/);
    assert.deepEqual(phone.requests.map(request => request.method), ['POST', 'DELETE']);
    assert.deepEqual(phone.read(), {owner: '12', registrationId: null});

    const existing = fixture({owner: '12', registrationId: null}, {storageFails: true, createdRegistration: false});
    await assert.rejects(existing.enable(), /could not save/);
    assert.deepEqual(existing.requests.map(request => request.method), ['POST']);
    assert.equal(existing.unsubscribes(), 0);
    assert.deepEqual(existing.read(), {owner: '12', registrationId: existing.deviceId});
});
