const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const ts = require('typescript');

const storage = new Map();
const window = {localStorage: {
    getItem: key => storage.get(key) ?? null,
    setItem: (key, value) => storage.set(key, value),
    removeItem: key => storage.delete(key)
}};
const source = fs.readFileSync(path.join(__dirname, '../lib/paymentGatewayVisit.ts'), 'utf8');
const compiled = ts.transpileModule(source, {
    compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022}
}).outputText;
const exported = {};
vm.runInNewContext(compiled, {exports: exported, window});
const {hasOpenedPaymentGateway, markPaymentGatewayOpened, clearPaymentGatewayVisit} = exported;

test('a pending payment does not poll before Pay, including after a page reload', () => {
    assert.equal(hasOpenedPaymentGateway('A', 42), false);
    markPaymentGatewayOpened('A', 42);
    assert.equal(hasOpenedPaymentGateway('A', 42), true);
    assert.equal(hasOpenedPaymentGateway('A', 43), false);
    assert.equal(hasOpenedPaymentGateway('B', 42), false);
    clearPaymentGatewayVisit('A');
    assert.equal(hasOpenedPaymentGateway('A', 42), false);
});

test('blocked browser storage leaves automatic provider polling disabled', () => {
    const blocked = {localStorage: {getItem() {throw Error('blocked');}, setItem() {throw Error('blocked');}, removeItem() {throw Error('blocked');}}};
    const isolated = {};
    vm.runInNewContext(compiled, {exports: isolated, window: blocked});
    assert.doesNotThrow(() => isolated.markPaymentGatewayOpened('A', 42));
    assert.equal(isolated.hasOpenedPaymentGateway('A', 42), false);
    assert.doesNotThrow(() => isolated.clearPaymentGatewayVisit('A'));
});
