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

function fixture(binding, {readFails = false, writeFails = false, cancellationDeleteFails = false, createdRegistration = true, storageFails = false, readLocalFails = false, locks, respond} = {}) {
    let cancellations;
    const isCancellation = key => key.endsWith('/staff-cancellations');
    const store = {
        async match(key) {
            const value = isCancellation(key) ? cancellations : binding;
            return value ? new Response(JSON.stringify(value)) : undefined;
        },
        async put(key, value) {
            if (typeof writeFails === 'function' ? writeFails() : writeFails) throw new Error('Storage quota exceeded');
            if (isCancellation(key)) cancellations = await value.json();
            else binding = await value.json();
        },
        async delete(key) {
            if (isCancellation(key) && cancellationDeleteFails) throw new Error('Cache deletion failed');
            if (isCancellation(key)) cancellations = undefined;
            else binding = null;
            return true;
        }
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
        if (typeof readLocalFails === 'function' ? readLocalFails() : readLocalFails) throw new Error('Storage blocked');
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
        retryCancellation: () => api.retryStaffAlertCancellation(12, new AbortController().signal),
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

test('a saved registration waits when a staged marker blocks delivery and cannot be updated', async () => {
    const setupId = '1f9e817c-8883-46ca-ad8b-a3ea20868152';
    const marker = {owner: '12', registrationId: null, setupId, setupFingerprint: 'stored'};
    let writeFails = true;
    const phone = fixture(marker, {writeFails: () => writeFails});
    await assert.rejects(phone.restore(), /could not save its alert registration/);
    assert.equal(phone.requests.length, 0);
    assert.deepEqual(phone.read(), marker);

    writeFails = false;
    assert.equal((await phone.restoreAs(phone.deviceId)).id, phone.deviceId);
    assert.equal(phone.requests[0].binding.registrationId, phone.deviceId);
    assert.equal(phone.requests[0].binding.pending, true);
    assert.equal(phone.requests[0].binding.setupId, setupId);
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
            if (request.body.registrationId) return new Response(null, {status: 404});
            return new Response(JSON.stringify({id, created: false}));
        }
    });
    await assert.rejects(phone.enable(), /Response lost/);
    assert.equal(phone.subscribed(), true);
    assert.equal(phone.unsubscribes(), 0);
    assert.equal(await phone.enable(), 'granted');
    assert.equal(phone.requests[2].body.endpoint, phone.requests[0].body.endpoint);
    assert.equal(phone.requests[2].body.registrationId, phone.requests[0].body.setupId);
    assert.equal(phone.requests[3].body.setupId, phone.requests[0].body.setupId);
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

test('a pending cancellation of a staged alias also blocks fallback to the saved registration', async () => {
    const setupId = '1f9e817c-8883-46ca-ad8b-a3ea20868152';
    const setupFingerprint = require('node:crypto').createHash('sha256')
        .update(JSON.stringify(['https://example.invalid/phone', 'key', 'auth'])).digest('hex');
    let storageBlocked = true;
    const phone = fixture({owner: '12', registrationId: null, setupId, setupFingerprint}, {
        readLocalFails: () => storageBlocked,
        respond(request) {
            assert.equal(request.method, 'DELETE');
            assert.ok(request.url.endsWith(setupId));
            return new Response(null, {status: 503});
        }
    });
    phone.saved.set(phone.key, phone.deviceId);
    await assert.rejects(phone.disable(), {status: 503});
    storageBlocked = false;

    assert.equal(await phone.recover(), null);
    assert.equal(await phone.confirm(phone.deviceId), false);
    await assert.rejects(phone.restoreAs(phone.deviceId), /alert registration changed/);
    assert.equal(phone.requests.length, 1);
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

test('disable cancels the staged setup and revokes the active registration', async () => {
    const confirmed = '3cf811c5-ba24-40db-ad09-f105fa46c49e';
    const staged = '1f9e817c-8883-46ca-ad8b-a3ea20868152';
    const binding = {owner: '12', registrationId: confirmed, setupId: staged, setupFingerprint: 'test'};
    const phone = fixture(binding);
    phone.saved.set(phone.key, confirmed);
    await phone.disable();
    assert.deepEqual(phone.requests.map(request => request.url.split('/').at(-1)), [staged, confirmed]);
    assert.deepEqual(phone.read(), {owner: '', registrationId: null});
    assert.equal(phone.saved.has(phone.key), false);
});

test('pending cancellation blocks recovery and a lost DELETE can be retried', async () => {
    let attempts = 0;
    const phone = fixture({owner: '12', registrationId: null}, {respond(request) {
        if (!request.method) return new Response('{"staffId":12}');
        if (request.method === 'DELETE') {
            if (++attempts === 1) throw new TypeError('DELETE response lost');
            return new Response(null, {status: 204});
        }
        if (attempts === 0) throw new TypeError('POST response lost');
        return Response.json({id: request.body.setupId, created: true});
    }});
    await assert.rejects(phone.enable(), /POST response lost/);
    const {setupId} = phone.read();
    await assert.rejects(phone.disable(), /DELETE response lost/);

    assert.equal(await phone.recover(), null);
    assert.equal(attempts, 1);
    assert.equal(phone.requests.length, 3);
    await phone.retryCancellation();
    assert.deepEqual(phone.read(), {owner: '', registrationId: null});
    assert.equal(await phone.enable(), 'granted');
    assert.notEqual(phone.read().registrationId, setupId);
});

test('Disable stays pending during this visit if neither storage system can save it', async () => {
    const setupId = '1f9e817c-8883-46ca-ad8b-a3ea20868152';
    const setupFingerprint = require('node:crypto').createHash('sha256')
        .update(JSON.stringify(['https://example.invalid/phone', 'key', 'auth'])).digest('hex');
    let offline = true;
    const phone = fixture({owner: '12', registrationId: null, setupId, setupFingerprint}, {
        writeFails: true, storageFails: true, respond(request) {
            assert.equal(request.method, 'DELETE', 'background recovery must not restore the staged registration');
            if (offline) throw new TypeError('offline');
            return new Response(null, {status: 204});
        }
    });

    await assert.rejects(phone.disable(), /Could not confirm Disable or save it on this browser/);
    assert.equal(await phone.recover(), null);
    assert.equal(phone.requests.length, 1);
    offline = false;
    await phone.retryCancellation();
    await phone.retryCancellation();
    assert.equal(phone.requests.length, 2, 'confirmed revocation drops the in-memory cancellation');
});

test('background recovery binds the canonical ID before releasing catch-up alerts', async () => {
    let setupAttempts = 0;
    let writeFails = false;
    const phone = fixture({owner: '12', registrationId: null}, {respond(request, deviceId) {
        if (request.body.setupId && ++setupAttempts <= 2) throw new TypeError('POST response lost');
        assert.ok(request.body.registrationId);
        if (request.body.registrationId !== deviceId) {
            assert.equal(request.binding.setupId, request.body.registrationId);
            return Response.json({id: deviceId, bindingRequired: true});
        }
        assert.equal(request.binding.registrationId, deviceId);
        assert.equal(request.binding.pending, true);
        return Response.json({id: deviceId});
    }, writeFails: () => writeFails});
    await assert.rejects(phone.enable(), /POST response lost/);
    assert.notEqual(phone.read().setupId, phone.deviceId);

    writeFails = true;
    await assert.rejects(phone.recover(), /could not save its alert registration/);
    assert.equal(phone.requests.length, 3, 'the catch-up request waits for a readable binding');
    writeFails = false;
    assert.equal(await phone.recover(), phone.deviceId);
    assert.equal(phone.requests.length, 5);
    assert.deepEqual(phone.read(), {owner: '12', registrationId: phone.deviceId});
    assert.equal(phone.saved.get(phone.key), phone.deviceId);
});

test('manual Enable resumes an existing alias only after saving the canonical ID', async () => {
    let setupAttempts = 0;
    const phone = fixture({owner: '12', registrationId: null}, {respond(request, deviceId) {
        if (request.body.setupId && ++setupAttempts <= 2) throw new TypeError('POST response lost');
        assert.ok(request.body.registrationId);
        if (request.body.registrationId !== deviceId) return Response.json({id: deviceId, bindingRequired: true});
        assert.equal(request.binding.registrationId, deviceId);
        assert.equal(request.binding.pending, true);
        return Response.json({id: deviceId});
    }});
    await assert.rejects(phone.enable(), /POST response lost/);
    assert.equal(await phone.enable(), 'granted');
    assert.equal(phone.requests.length, 4);
    assert.deepEqual(phone.read(), {owner: '12', registrationId: phone.deviceId});
});

for (const status of [200, 404, 410, 503]) {
    test(`manual Enable first restores the saved device after sign-out (restore status ${status})`, async () => {
        const savedId = '3cf811c5-ba24-40db-ad09-f105fa46c49e';
        const phone = fixture({owner: '12', registrationId: savedId}, {
            createdRegistration: false,
            respond(request) {
                if (!request.method) return Response.json({staffId: 12});
                if (request.body.registrationId) {
                    assert.equal(request.body.registrationId, savedId);
                    assert.equal(request.binding.registrationId, savedId);
                    assert.equal(request.binding.pending, true);
                    return status === 200 ? Response.json({id: savedId}) : new Response(null, {status});
                }
                assert.ok(request.body.setupId);
                return Response.json({id: request.body.setupId, created: true});
            }
        });
        phone.saved.set(phone.key, savedId);
        await phone.signOut();

        if (status === 503) {
            await assert.rejects(phone.enable(), {status: 503});
        } else {
            assert.equal(await phone.enable(), 'granted');
        }
        const newSetups = phone.requests.filter(request => request.body?.setupId);
        assert.equal(newSetups.length, [404, 410].includes(status) ? 1 : 0);
        assert.equal(phone.saved.get(phone.key), newSetups[0]?.body.setupId ?? savedId);
    });
}

test('a stale cancellation marker does not block a replacement registration or its recovery', async () => {
    const old = '3cf811c5-ba24-40db-ad09-f105fa46c49e';
    const phone = fixture({owner: '12', registrationId: old}, {cancellationDeleteFails: true});
    phone.saved.set(phone.key, old);
    await phone.disable();
    assert.equal(await phone.enable(), 'granted');
    assert.equal(await phone.confirm(phone.deviceId), true);
    await phone.retryCancellation();
    assert.equal(await phone.restoreAs(phone.deviceId).then(result => result.id), phone.deviceId);
    assert.deepEqual(phone.read(), {owner: '12', registrationId: phone.deviceId});
    assert.ok(phone.requests.filter(req => req.method === 'DELETE').every(req => req.url.endsWith(old)));
});

for (const restoreStatus of [404, 410]) {
    test(`explicit Enable replaces a server-cancelled staged ID after restore returns ${restoreStatus}`, async () => {
        let cancelledId;
        const phone = fixture({owner: '12', registrationId: null}, {respond(request) {
            if (!cancelledId) throw new TypeError('POST response lost');
            if (request.body.registrationId === cancelledId) return new Response(null, {status: restoreStatus});
            if (request.body.setupId === cancelledId) return new Response(null, {status: 410});
            return Response.json({id: request.body.setupId, created: true});
        }});
        await assert.rejects(phone.enable(), /POST response lost/);
        cancelledId = phone.read().setupId;
        assert.equal(await phone.enable(), 'granted');
        assert.notEqual(phone.read().registrationId, cancelledId);
    });
}

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

test('manual enable after sign-out retries a temporary restore failure with the same staged ID', async () => {
    let registerAttempts = 0;
    let restoreAttempts = 0;
    const phone = fixture({owner: '12', registrationId: null}, {respond(request) {
        if (!request.method) return new Response('{"staffId":12}');
        if (request.body.setupId && ++registerAttempts <= 2) throw new TypeError('Response lost');
        assert.ok(request.body.registrationId);
        if (++restoreAttempts === 1) return new Response(null, {status: 503});
        return new Response(JSON.stringify({id: request.body.registrationId}));
    }});
    await assert.rejects(phone.enable(), /Response lost/);
    const {setupId, setupFingerprint} = phone.read();
    await phone.signOut();

    await assert.rejects(phone.enable(), {status: 503});
    assert.equal(phone.requests[3].binding.signedOut, true);
    assert.equal(phone.requests[4].body.registrationId, setupId);
    assert.equal(phone.read().setupId, setupId);
    assert.equal(phone.read().setupFingerprint, setupFingerprint);
    assert.equal(phone.subscribed(), true);
    assert.equal(phone.unsubscribes(), 0);

    assert.equal(await phone.enable(), 'granted');
    assert.equal(phone.requests[5].body.registrationId, setupId);
    assert.deepEqual(phone.read(), {owner: '12', registrationId: setupId});
    assert.equal(phone.saved.get(phone.key), setupId);
});
