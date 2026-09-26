const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const ts = require('typescript');

const source = fs.readFileSync(path.join(__dirname, '../lib/checkoutQuoteContext.ts'), 'utf8');
const compiled = ts.transpileModule(source, {
    compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022}
}).outputText;
const exported = {};
vm.runInNewContext(compiled, {exports: exported, Number});
const {pendingCheckoutAction} = exported;

test('an unexpired unpaid checkout keeps its order-bound quote', () => {
    assert.equal(pendingCheckoutAction({orderStatus: 'PENDING_PAYMENT', paymentStatus: null}, 20_000, 10_000), 'reuse');
});

test('an expired or invalid deadline replaces the checkout before a new quote is signed', () => {
    const order = {orderStatus: 'PENDING_PAYMENT', paymentStatus: null};
    assert.equal(pendingCheckoutAction(order, 10_000, 10_000), 'replace');
    assert.equal(pendingCheckoutAction(order, Number.NaN, 10_000), 'replace');
});

test('an active payment or already paid order must not generate a second checkout', () => {
    assert.equal(pendingCheckoutAction({orderStatus: 'PENDING_PAYMENT', paymentStatus: 'PENDING'}, 0, 10_000), 'payment');
    assert.equal(pendingCheckoutAction({orderStatus: 'CONFIRMED', paymentStatus: 'PAID'}, 0, 10_000), 'paid');
});
