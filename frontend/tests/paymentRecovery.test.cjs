const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const ts = require('typescript');
const source = ts.transpileModule(fs.readFileSync(require('node:path').join(__dirname, '../lib/paymentRecovery.ts'), 'utf8'), {
    compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022}
}).outputText;
const known = {paymentId: 1, orderNumber: 'A', provider: 'PHONEPE', paymentStatus: 'PENDING', paymentUrl: 'https://checkout.invalid/old'};
function load(current, {opened = false, refreshError, refreshed = current} = {}) {
    const calls = [];
    class ApiError extends Error {constructor(status) {super('provider unavailable'); this.status = status;}}
    const modules = {
        '@/services/paymentApi': {
            getPaymentForOrder: async number => {calls.push(['lookup', number]); return {payment: current};},
            refreshPayment: async id => {calls.push(['refresh', id]); if (refreshError) throw new ApiError(refreshError); return refreshed;}
        },
        '@/services/apiClient': {ApiError},
        '@/lib/paymentGatewayVisit': {hasOpenedPaymentGateway: () => opened},
        '@/lib/paymentPolling': {isTemporaryPaymentFailure: status => status >= 500}
    };
    const exports = {};
    vm.runInNewContext(source, {exports, require: name => {assert.ok(modules[name], name); return modules[name];}});
    return {...exports, calls};
}

test('stale pending history is reconciled to paid or cancelled without opening or polling', async () => {
    for (const paymentStatus of ['PAID', 'EXPIRED', 'FAILED', 'REFUND_PENDING', 'REFUNDED']) {
        const service = load({...known, paymentStatus});
        assert.equal((await service.refreshKnownPayment(known, true)).paymentStatus, paymentStatus);
        assert.deepEqual(service.calls, [['lookup', 'A']]);
    }
});
test('unopened pending checkout still reads the backend but does not poll the provider', async () => {
    const service = load({...known, amount: 200});
    assert.equal((await service.refreshKnownPayment(known, true)).amount, 200);
    assert.deepEqual(service.calls, [['lookup', 'A']]);
});
test('replacement attempts never inherit an old gateway URL or SDK credentials', async () => {
    const current = {...known, paymentId: 2, paymentUrl: null};
    const service = load(current);
    assert.equal((await service.refreshKnownPayment(known, true)).paymentUrl, null);
    assert.equal(service.mergePaymentResponse({...current, provider: 'RAZORPAY'}, known).paymentUrl, null);
});
test('missing or inaccessible payment cannot be recovered from a cached pending record', async () => {
    const service = load(null);
    await assert.rejects(service.refreshKnownPayment(known, true), /check My Orders/);
    assert.deepEqual(service.calls, [['lookup', 'A']]);
});
test('opened checkout refresh keeps the same attempt on a temporary outage', async () => {
    const service = load({...known, paymentUrl: null}, {opened: true, refreshError: 503});
    assert.equal((await service.refreshKnownPayment(known, true)).paymentId, 1);
    assert.deepEqual(service.calls, [['lookup', 'A'], ['refresh', 1]]);
});
test('deliberate Pay cannot fall back to pending when provider confirmation fails', async () => {
    const service = load({...known, paymentUrl: null}, {opened: true, refreshError: 503});
    await assert.rejects(service.refreshKnownPayment(known, true, {allowTemporaryFallback: false}), /provider unavailable/);
    assert.deepEqual(service.calls, [['lookup', 'A'], ['refresh', 1]]);
});
test('deliberate Pay verifies a settled provider attempt even when its browser marker is missing', async () => {
    for (const paymentStatus of ['PAID', 'EXPIRED', 'FAILED']) {
        const service = load(known, {refreshed: {...known, paymentStatus}});
        assert.equal((await service.refreshKnownPayment(known, true, {allowTemporaryFallback: false})).paymentStatus, paymentStatus);
        assert.deepEqual(service.calls, [['lookup', 'A'], ['refresh', 1]]);
    }
});
test('missing gateway marker cannot bypass provider failure on deliberate Pay', async () => {
    const service = load(known, {refreshError: 503});
    await assert.rejects(service.refreshKnownPayment(known, true, {allowTemporaryFallback: false}), /provider unavailable/);
    assert.deepEqual(service.calls, [['lookup', 'A'], ['refresh', 1]]);
});
