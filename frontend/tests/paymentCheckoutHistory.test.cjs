const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const ts = require('typescript');
test('hosted PhonePe checkout replaces the merchant history entry', async () => {
    const source = ts.transpileModule(fs.readFileSync(require('node:path').join(__dirname, '../lib/paymentCheckout.ts'), 'utf8'), {
        compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022}
    }).outputText;
    const exports = {}, destinations = [];
    vm.runInNewContext(source, {exports, require: () => ({}), window: {location: {
        replace: url => destinations.push(url), assign: () => assert.fail('must not add a stale Pay entry')
    }}});
    const result = await exports.openPaymentCheckout({provider: 'PHONEPE', paymentUrl: 'https://gateway.invalid/checkout'});
    assert.equal(result.kind, 'dismissed');
    assert.deepEqual(destinations, ['https://gateway.invalid/checkout']);
    assert.equal((await exports.openPaymentCheckout({provider: 'PHONEPE', paymentUrl: null})).kind, 'failed');
    assert.equal(destinations.length, 1, 'a missing checkout link never navigates');
});
