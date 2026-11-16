const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');
const {webcrypto} = require('node:crypto');

function load(file, globals) {
    const source = fs.readFileSync(path.join(__dirname, '..', file), 'utf8');
    const code = ts.transpileModule(source, {compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022}}).outputText;
    const exports = {};
    vm.runInNewContext(code, {exports, Response, Headers, AbortSignal, DOMException, TypeError, SyntaxError, Event, TextEncoder, crypto: webcrypto, ...globals});
    return exports;
}

function fixture(binding, {readFails = false, writeFails = false, createdRegistration = true, storageFails = false, readLocalFails = false, locks, respond} = {}) {
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
    let subscribed = !createdRegistration;
    const subscription = {endpoint: 'https://example.invalid/phone', toJSON: () => ({keys: {p256dh: 'key', auth: 'auth'}}),
        async unsubscribe() {unsubscribes++; subscribed = false; return true;}};
    const navigator = {locks, serviceWorker: {async register() {
        return {active: true, pushManager: {
            async getSubscription() {return subscribed ? subscription : null;},
            async subscribe() {subscribed = true; return subscription;}
        }};
    }}};
    const localStorage = {getItem: key => {
        if (readLocalFails) throw new Error('Storage blocked');
        return saved.get(key) ?? null;
    }, removeItem: key => saved.delete(key),
        setItem(key, value) {
            if ((typeof storageFails === 'function' ? storageFails() : storageFails)) throw new Error('Storage blocked');
            saved.set(key, value);
        }};
    const api = load('services/staffAlertsApi.ts', {navigator, localStorage, window: {dispatchEvent() {}}, Notification: {permission: 'granted'}, require: module => {
        if (module.endsWith('/orderPushBinding')) return cached;
        if (module.endsWith('/notificationAlerts')) return {applicationServerKey: () => new Uint8Array(65)};
        if (module.endsWith('/adminApi')) return {async logoutAdmin(signal, device, staffId, staged) {
            requests.push({method: 'LOGOUT', device, staffId, ...(staged ? {staged} : {})});
            return null;
        }, async adminFetch(url, authorization, init) {
            const request = {url, method: init.method, authorization, body: init.body && JSON.parse(init.body), binding};
            requests.push(request);
            if (respond) return respond(request, deviceId);
            if (!init.method) return new Response('{"staffId":12}');
            return init.method === 'DELETE' ? new Response(null, {status: 204})
                : new Response(JSON.stringify({id: deviceId, created: createdRegistration}));
        }};
        return {};
    }});
    const key = api.staffAlertDeviceKey(12);
    const restoreAs = id => api.syncStaffAlertDevice(subscription, 12, new AbortController().signal, id);
    return {requests, restore: () => {saved.set(key, deviceId); return restoreAs(deviceId);}, restoreAs,
        bind: cached.bindStaffOrderPush, switchOwner: cached.switchOrderPushOwner, read: () => binding,
        enable: () => api.enableStaffDeviceAlerts({staffId: 12, applicationServerKey: 'test'}),
        disable: () => api.disableStaffDeviceAlerts(12),
        signOut: () => api.logoutStaffAlertDevice(12, new AbortController().signal),
        recover: () => api.recoverStaffAlertSetup(subscription, 12, new AbortController().signal),
        confirm: id => api.confirmStaffAlertDevice(12, id, new AbortController().signal),
        deviceId, key, saved, unsubscribes: () => unsubscribes, subscribed: () => subscribed};
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

test('fresh setup leaves the server untouched if a blocking cache marker cannot be staged', async () => {
    const owner = {owner: '13', registrationId: 'another-phone'};
    const phone = fixture(owner, {writeFails: true});
    await assert.rejects(phone.enable(), /Storage quota exceeded/);
    assert.deepEqual(phone.requests.map(request => request.method ?? 'GET'), ['GET']);
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

test('a second setup waits for the first setup to finish rolling back', async () => {
    let tail = Promise.resolve();
    const locks = {request(name, options, work) {
        assert.equal(name, 'gokul-staff-push-setup');
        const result = tail.then(work);
        tail = result.catch(() => {});
        return result;
    }};
    let startedRollback;
    const inRollback = new Promise(resolve => {startedRollback = resolve;});
    let finishRollback;
    const releaseRollback = new Promise(resolve => {finishRollback = resolve;});
    let saves = 0;
    const phone = fixture({owner: '12', registrationId: null}, {
        locks,
        storageFails: () => ++saves === 1,
        async respond(request, id) {
            if (request.method === 'DELETE') {
                startedRollback();
                await releaseRollback;
                return new Response(null, {status: 204});
            }
            return new Response(JSON.stringify({id, created: true}));
        }
    });
    const first = assert.rejects(phone.enable(), /could not save/);
    await inRollback;
    const second = phone.enable();
    await new Promise(resolve => setImmediate(resolve));
    assert.deepEqual(phone.requests.map(request => request.method), ['POST', 'DELETE']);
    finishRollback();
    await first;
    assert.equal(await second, 'granted');
    assert.deepEqual(phone.requests.map(request => request.method), ['POST', 'DELETE', 'POST']);
    assert.equal(phone.subscribed(), true);
    assert.equal(phone.unsubscribes(), 1);
});

test('an ambiguous response is retried with the identical setup ID and endpoint', async () => {
    let attempts = 0;
    const phone = fixture({owner: '12', registrationId: null}, {
        respond(request, id) {
            if (++attempts === 1) throw new TypeError('Response lost');
            return new Response(JSON.stringify({id, created: true}));
        }
    });
    assert.equal(await phone.enable(), 'granted');
    assert.equal(phone.requests.length, 2);
    assert.deepEqual(phone.requests[1].body, phone.requests[0].body);
    assert.match(phone.requests[0].body.setupId, /^[0-9a-f-]{36}$/);
    assert.ok([...phone.saved.values()].includes(phone.deviceId));
});

test('repeated lost responses leave the endpoint available for a later setup', async () => {
    let attempts = 0;
    const phone = fixture({owner: '12', registrationId: null}, {
        respond(request, id) {
            if (++attempts <= 2) throw new TypeError('Response lost');
            assert.equal(request.method, 'POST');
            return new Response(JSON.stringify({id, created: false}));
        }
    });
    await assert.rejects(phone.enable(), /Response lost/);
    assert.equal(phone.subscribed(), true);
    assert.equal(phone.unsubscribes(), 0);
    assert.equal(await phone.enable(), 'granted');
    assert.equal(phone.requests[2].body.endpoint, phone.requests[0].body.endpoint);
    assert.equal(phone.requests[2].body.setupId, phone.requests[0].body.setupId);
});

test('fresh setup stages its ID after verifying the current account and before posting', async () => {
    const phone = fixture({owner: '', registrationId: null}, {
        respond(request) {
            if (request.method !== 'POST') return new Response('{"staffId":12}');
            return new Response(JSON.stringify({id: request.body.setupId, created: true}));
        }
    });
    await phone.enable();
    const request = phone.requests[1];
    assert.equal(request.method, 'POST');
    assert.equal(request.binding.owner, '12');
    assert.equal(request.binding.setupId, request.body.setupId);
    assert.deepEqual(phone.read(), {owner: '12', registrationId: request.body.setupId});
});

test('disable finds a matching cached registration if device storage is unreadable', async () => {
    const id = 'd1779266-3814-48cf-8f6d-8c23b832c1c3';
    const phone = fixture({owner: '12', registrationId: id}, {readLocalFails: true});
    await phone.disable();
    assert.ok(phone.requests[0].url.endsWith(id));
    assert.deepEqual(phone.read(), {owner: '', registrationId: null});

    const different = fixture({owner: '13', registrationId: id}, {readLocalFails: true});
    await assert.rejects(different.disable(), /disable could not be confirmed/);
    assert.equal(different.requests.length, 0);
});

for (const operation of ['disable', 'signOut']) {
    test(`${operation} waits for setup and acts on the newly enrolled device`, async () => {
        let finishSetup;
        const release = new Promise(resolve => {finishSetup = resolve;});
        let startedSetup;
        const started = new Promise(resolve => {startedSetup = resolve;});
        const phone = fixture({owner: '12', registrationId: null}, {
            async respond(request, id) {
                if (request.method === 'DELETE') return new Response(null, {status: 204});
                startedSetup();
                await release;
                return new Response(JSON.stringify({id, created: true}));
            }
        });
        const earlierId = '3cf811c5-ba24-40db-ad09-f105fa46c49e';
        phone.saved.set(phone.key, earlierId);
        const setup = phone.enable();
        await started;
        const action = phone[operation]();
        await new Promise(resolve => setImmediate(resolve));
        assert.deepEqual(phone.requests.map(request => request.method), ['POST']);
        finishSetup();
        assert.equal(await setup, 'granted');
        await action;
        if (operation === 'disable') {
            assert.ok(phone.requests[1].url.endsWith(phone.deviceId));
            assert.equal(phone.requests[1].authorization, 'staff-session:12');
            assert.equal(phone.saved.has(phone.key), false);
            await assert.rejects(phone.restoreAs(earlierId), /registration changed/);
            await assert.rejects(phone.restoreAs(phone.deviceId), /registration changed/);
            assert.equal(await phone.confirm(phone.deviceId), false);
            assert.equal(phone.requests.length, 2);
        } else {
            assert.deepEqual(phone.requests[1], {method: 'LOGOUT', device: phone.deviceId, staffId: 12});
            assert.equal(phone.saved.get(phone.key), phone.deviceId, 'sign-out preserves the opt-in for later restore');
        }
        assert.deepEqual(phone.read(), {owner: '', registrationId: null});
    });
}

test('a failed revoke preserves the registration and local binding for retry', async () => {
    const deviceId = 'd1779266-3814-48cf-8f6d-8c23b832c1c3';
    const binding = {owner: '12', registrationId: deviceId};
    const phone = fixture(binding, {respond: () => new Response(null, {status: 503})});
    phone.saved.set(phone.key, deviceId);
    await assert.rejects(phone.disable(), /Could not confirm/);
    assert.equal(phone.saved.get(phone.key), deviceId);
    assert.deepEqual(phone.read(), binding);
});

test('polling retains the staged ID and disable revokes both possible registrations', async () => {
    const confirmed = '3cf811c5-ba24-40db-ad09-f105fa46c49e';
    const staged = '1f9e817c-8883-46ca-ad8b-a3ea20868152';
    const binding = {owner: '12', registrationId: confirmed, setupId: staged, setupFingerprint: 'test'};
    const phone = fixture(binding);
    phone.saved.set(phone.key, confirmed);
    assert.equal(await phone.confirm(confirmed), true);
    assert.deepEqual(phone.read(), binding);
    await phone.disable();
    assert.deepEqual(phone.requests.map(request => request.url.split('/').at(-1)), [staged, confirmed]);
    assert.deepEqual(phone.read(), {owner: '', registrationId: null});
});

test('sign-out retains a lost setup response for same-device recovery', async () => {
    let attempts = 0;
    const phone = fixture({owner: '12', registrationId: null}, {
        respond(request) {
            if (!request.method) return new Response('{"staffId":12}');
            if (++attempts <= 2) throw new TypeError('Response lost');
            return new Response(JSON.stringify({id: request.body.registrationId}));
        }
    });
    await assert.rejects(phone.enable(), /Response lost/);
    const staged = phone.requests[0].body.setupId;
    await phone.signOut();
    assert.equal(phone.requests[2].staged, staged);
    assert.equal(phone.read().signedOut, true);
    assert.equal(phone.read().setupId, staged);
    assert.equal(await phone.recover(), staged);
    assert.equal(phone.requests[3].binding.signedOut, true, 'recovery verifies sign-in before opening the binding');
    assert.equal(phone.requests[4].binding.signedOut, undefined);
    assert.deepEqual(phone.read(), {owner: '12', registrationId: staged});
    assert.equal(phone.saved.get(phone.key), staged);
});
