const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const vm = require('node:vm');
const ts = require('typescript');

function fixture() {
    const requests = [], exports = {}, window = new EventTarget();
    let identityChange;
    vm.runInNewContext(ts.transpileModule(fs.readFileSync('services/customerInbox.ts', 'utf8'), {
        compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022}
    }).outputText, {exports, window, AbortController, AbortSignal, Promise,
        require: name => name.includes('customerIdentityEvents') ? {
            subscribeCustomerIdentityChanges: change => {identityChange = change;}
        } : {apiClient: (path, options) => new Promise((resolve, reject) => requests.push({path, options, resolve, reject}))}
    });
    return {api: exports, requests, identityChange: () => identityChange(), window};
}

test('bell and sound share an overlapping inbox request, with no retained private result', async () => {
    const f = fixture(), a = new AbortController(), b = new AbortController();
    const first = f.api.readCustomerInbox(a.signal), second = f.api.readCustomerInbox(b.signal);
    assert.equal(f.requests.length, 1);
    assert.equal(f.requests[0].path, '/api/customer/identity/notifications');
    f.requests[0].resolve({unreadCount: 2, messages: []});
    assert.equal((await first).unreadCount, 2); await second;
    const fresh = f.api.readCustomerInbox(a.signal);
    assert.equal(f.requests.length, 2);
    f.requests[1].resolve({unreadCount: 0, messages: []}); await fresh;
});

test('one subscriber can cancel without cancelling the other; last cancellation aborts work', async () => {
    const f = fixture(), a = new AbortController(), b = new AbortController();
    const first = f.api.readCustomerInbox(a.signal), second = f.api.readCustomerInbox(b.signal);
    a.abort(); await assert.rejects(first);
    assert.equal(f.requests[0].options.signal.aborted, false);
    b.abort(); await assert.rejects(second);
    assert.equal(f.requests[0].options.signal.aborted, true);
    // Even a transport that ignores abort cannot publish this response.
    f.requests[0].resolve({unreadCount: 9, messages: []});
    await Promise.resolve();
});

for (const reason of ['identity', 'read']) test(`${reason} changes reject obsolete responses and start a fresh read`, async () => {
    const f = fixture(), a = new AbortController();
    const old = f.api.readCustomerInbox(a.signal);
    const rejected = assert.rejects(old);
    if (reason === 'identity') f.identityChange();
    else f.window.dispatchEvent(new Event('gokul-inbox-changed'));
    const fresh = f.api.readCustomerInbox(a.signal);
    f.requests[0].resolve({unreadCount: 9, messages: []}); await rejected;
    f.requests[1].resolve({unreadCount: 1, messages: []});
    assert.equal((await fresh).unreadCount, 1);
});

test('failed requests are not cached and already cancelled consumers never start requests', async () => {
    const f = fixture(), a = new AbortController();
    a.abort(); assert.throws(() => f.api.readCustomerInbox(a.signal));
    assert.equal(f.requests.length, 0);
    const active = new AbortController(), first = f.api.readCustomerInbox(active.signal);
    f.requests[0].reject(new Error('offline')); await assert.rejects(first);
    const retry = f.api.readCustomerInbox(active.signal);
    f.requests[1].resolve({unreadCount: 0, messages: []}); await retry;
});
