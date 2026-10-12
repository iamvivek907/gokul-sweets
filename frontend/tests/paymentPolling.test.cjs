const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const ts = require('typescript');

const source = fs.readFileSync(path.join(__dirname, '../lib/paymentPolling.ts'), 'utf8');
const compiled = ts.transpileModule(source, {
    compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022}
}).outputText;
const exported = {};
vm.runInNewContext(compiled, {exports: exported, Math, Number, Date});
const {nextPaymentPollDelayMs, retryAfterDelayMs, isTemporaryPaymentFailure} = exported;

test('pending payment checks start slowly and repeated provider failures back off', () => {
    assert.equal(nextPaymentPollDelayMs(0, 0), 15_000);
    assert.equal(nextPaymentPollDelayMs(2, 0), 30_000);
    assert.equal(nextPaymentPollDelayMs(0, 1), 30_000);
    assert.equal(nextPaymentPollDelayMs(0, 2), 60_000);
    assert.equal(nextPaymentPollDelayMs(0, 5), 120_000);
    assert.ok(nextPaymentPollDelayMs(0, 2, 1) > 60_000);
});

test('Retry-After is honoured for seconds and HTTP dates without unbounded delays', () => {
    const now = Date.parse('2026-09-26T06:00:00Z');
    assert.equal(retryAfterDelayMs('45', now), 45_000);
    assert.equal(retryAfterDelayMs('Sat, 26 Sep 2026 06:02:00 GMT', now), 120_000);
    assert.equal(retryAfterDelayMs('1000', now), 300_000);
    assert.equal(retryAfterDelayMs('invalid', now), null);
});

test('only network, rate-limit and server responses trigger automatic retry', () => {
    for (const status of [0, 429, 502, 503]) assert.equal(isTemporaryPaymentFailure(status), true);
    for (const status of [400, 401, 404]) assert.equal(isTemporaryPaymentFailure(status), false);
});

test('a final provider response stops the active payment timer immediately', async () => {
    const hookSource = fs.readFileSync(path.join(__dirname, '../hooks/usePaymentPolling.ts'), 'utf8');
    const hookCode = ts.transpileModule(hookSource, {
        compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022}
    }).outputText;
    const effects = [];
    const timers = new Map();
    let nextId = 0;
    const hookExports = {};
    vm.runInNewContext(hookCode, {
        exports: hookExports,
        require: name => name === 'react'
            ? {useRef: value => ({current: value}), useEffect: callback => {effects.push(callback);}}
            : {nextPaymentPollDelayMs: () => 15_000},
        window: {setTimeout: callback => {const id = ++nextId; timers.set(id, callback); return id;},
            clearTimeout: id => timers.delete(id), addEventListener() {}, removeEventListener() {}},
        document: {hidden: false, addEventListener() {}, removeEventListener() {}},
        navigator: {onLine: true}, Date, Math
    });
    let checks = 0;
    hookExports.usePaymentPolling(true, 42, 'PENDING', Date.now() + 60_000, false,
        async () => {checks++; return {success: true, permanent: true};});
    effects.forEach(effect => effect());
    assert.equal(timers.size, 1);
    const callback = [...timers.values()][0];
    timers.clear();
    callback();
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(checks, 1);
    assert.equal(timers.size, 0);
});

test('three consecutive provider errors pause automatic checks without declaring payment failed', async () => {
    const hookSource = fs.readFileSync(path.join(__dirname, '../hooks/usePaymentPolling.ts'), 'utf8');
    const hookCode = ts.transpileModule(hookSource, {
        compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022}
    }).outputText;
    const effects = [];
    const timers = new Map();
    let nextId = 0;
    const hookExports = {};
    vm.runInNewContext(hookCode, {
        exports: hookExports,
        require: name => name === 'react'
            ? {useRef: value => ({current: value}), useEffect: callback => {effects.push(callback);}}
            : {MAX_AUTOMATIC_PAYMENT_FAILURES: 3, nextPaymentPollDelayMs: () => 30_000},
        window: {setTimeout: callback => {const id = ++nextId; timers.set(id, callback); return id;},
            clearTimeout: id => timers.delete(id), addEventListener() {}, removeEventListener() {}},
        document: {hidden: false, addEventListener() {}, removeEventListener() {}},
        navigator: {onLine: true}, Date, Math
    });
    let checks = 0;
    let paused = 0;
    hookExports.usePaymentPolling(true, 42, 'PENDING', Date.now() + 300_000, false,
        async () => {checks++; return {success: false, retryAfterMs: 30_000};}, () => {paused++;});
    effects.forEach(effect => effect());
    for (let i = 0; i < 3; i++) {
        assert.equal(timers.size, 1);
        const [id, callback] = timers.entries().next().value;
        timers.delete(id);
        callback();
        await new Promise(resolve => setImmediate(resolve));
    }
    assert.equal(checks, 3);
    assert.equal(paused, 1);
    assert.equal(timers.size, 0);
});
