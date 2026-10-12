const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const vm = require('node:vm');
const ts = require('typescript');
const flush = () => new Promise(resolve => setImmediate(resolve));

function bell() {
    const requests = [], counts = [], timers = [], exports = {};
    const window = new EventTarget(), document = new EventTarget(), navigator = {onLine: true};
    document.visibilityState = 'visible';
    window.setInterval = callback => {timers.push(callback); return 1;}; window.clearInterval = () => {};
    let cleanup, identityChange;
    class ApiError extends Error {constructor(status) {super('Rejected'); this.status = status;}}
    const modules = {
        react: {useEffect: effect => {cleanup = effect();}, useState: () => [null, value => counts.push(value)]},
        'react/jsx-runtime': {jsx: () => null, jsxs: () => null},
        '@/hooks/useStorefrontFeatures': {useStorefrontFeatures: () => ({notificationInbox: true})},
        '@/services/apiClient': {ApiError, apiClient: () => {throw new Error('Unexpected extra identity read');}},
        '@/services/customerInbox': {readCustomerInbox: signal => new Promise((resolve, reject) => requests.push({signal, resolve, reject}))},
        '@/lib/customerIdentityEvents': {subscribeCustomerIdentityChanges: callback => {identityChange = callback; return () => {};}}
    };
    vm.runInNewContext(ts.transpileModule(fs.readFileSync('components/customer/CustomerNotificationBell.tsx', 'utf8'), {
        compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022, jsx: ts.JsxEmit.ReactJSX}
    }).outputText, {exports, AbortController, window, document, navigator, require: name => modules[name] ?? {default: () => null}});
    exports.default();
    return {requests, counts, timers, window, document, navigator, ApiError, cleanup: () => cleanup(), identityChange: () => identityChange()};
}

test('idle guest polling pauses after rejection and resumes after sign-in or returning to the app', async () => {
    const f = bell();
    f.requests[0].reject(new f.ApiError(401)); await flush();
    for (let minute = 0; minute < 10; minute++) f.timers[0]();
    assert.equal(f.requests.length, 1);
    f.window.dispatchEvent(new Event('focus')); assert.equal(f.requests.length, 2);
    f.requests[1].reject(new f.ApiError(401)); await flush();
    f.identityChange(); assert.equal(f.requests.length, 3);
    f.requests[2].resolve({unreadCount: 2}); await flush();
    assert.equal(f.counts.at(-1), 2);
    f.timers[0](); assert.equal(f.requests.length, 4);
    f.requests[3].resolve({unreadCount: 1}); await flush(); f.cleanup();
});

test('account/read changes discard late responses and hidden/offline tabs avoid polling', async () => {
    const f = bell();
    f.identityChange(); assert.equal(f.requests[0].signal.aborted, true);
    f.requests[0].resolve({unreadCount: 99}); await flush();
    assert.equal(f.counts.includes(99), false);
    f.requests[1].resolve({unreadCount: 2}); await flush();
    f.document.visibilityState = 'hidden'; f.timers[0](); assert.equal(f.requests.length, 2);
    f.document.visibilityState = 'visible'; f.navigator.onLine = false; f.timers[0](); assert.equal(f.requests.length, 2);
    f.navigator.onLine = true; f.window.dispatchEvent(new Event('gokul-inbox-changed'));
    f.requests[2].resolve({unreadCount: 0}); await flush(); assert.equal(f.counts.at(-1), 0);
    f.timers[0](); f.cleanup();
    assert.equal(f.requests[3].signal.aborted, true);
    const before = f.counts.length; f.requests[3].resolve({unreadCount: 77}); await flush();
    assert.equal(f.counts.length, before);
});
