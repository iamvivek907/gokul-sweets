const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const test = require('node:test');
const ts = require('typescript');
const source = fs.readFileSync(path.join(__dirname, '../lib/storefrontPresentation.ts'), 'utf8');
const compiled = ts.transpileModule(source, {
    compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022}
}).outputText;
const exported = {};
vm.runInNewContext(compiled, {exports: exported, Intl});

test('weight and piece availability remains correctly labelled at the boundary', () => {
    assert.equal(exported.formatAvailableAmount(250, 'GRAM'), '250 g');
    assert.equal(exported.formatAvailableAmount(10000, 'GRAM'), '10 kg');
    assert.equal(exported.formatAvailableAmount(1, 'PIECE'), '1 piece');
    assert.equal(exported.formatAvailableAmount(3, 'PIECE'), '3 pieces');
});

test('availability copy never presents a preview as a confirmed pickup', () => {
    assert.match(exported.describePickupAvailability(undefined, true), /Checking/);
    assert.match(exported.describePickupAvailability(undefined, false), /Choose/);
    assert.match(exported.describePickupAvailability({available: true}, false), /confirmed at checkout/);
    assert.match(exported.describePickupAvailability({available: false, code: 'QUANTITY_TOO_LARGE', availableQuantity: 1500, unit: 'GRAM'}, false), /1.5 kg/);
    assert.match(exported.describePickupAvailability({available: false, code: 'PICKUP_WINDOW', availableQuantity: null, unit: 'GRAM'}, false), /another date/);
});
