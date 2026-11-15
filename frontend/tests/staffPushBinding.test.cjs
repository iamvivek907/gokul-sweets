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
    vm.runInNewContext(code, {exports, Response, Headers, ...globals});
    return exports;
}

function fixture(binding, {readFails = false, writeFails = false} = {}) {
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
    const api = load('services/staffAlertsApi.ts', {require: module => {
        if (module.endsWith('/orderPushBinding')) return cached;
        if (module.endsWith('/adminApi')) return {async adminFetch(url, authorization, init) {
            requests.push({authorization, body: JSON.parse(init.body), binding});
            return new Response('{"id":"staff-phone"}');
        }};
        return {};
    }});
    const subscription = {endpoint: 'https://example.invalid/phone', toJSON: () => ({keys: {p256dh: 'key', auth: 'auth'}})};
    return {requests, restore: () => api.syncStaffAlertDevice(subscription, 12, new AbortController().signal, 'staff-phone'),
        bind: cached.bindStaffOrderPush, switchOwner: cached.switchOrderPushOwner, read: () => binding};
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

    await phone.switchOwner('staff', '12', signal, true, '12');
    await assert.rejects(phone.restore(), /active staff account changed/);
    assert.equal(await phone.bind('12', 'staff-phone', signal), false);
    assert.equal(phone.requests.length, 0);
    assert.deepEqual(phone.read(), {owner: '13', registrationId: 'current-phone'});
});
