const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const ts = require('typescript');
const vm = require('node:vm');

function load(storage) {
    const source = fs.readFileSync(path.join(__dirname, '../lib/entryIntent.ts'), 'utf8');
    const compiled = ts.transpileModule(source, {
        compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022}
    }).outputText;
    const exports = {};
    vm.runInNewContext(compiled, {exports, sessionStorage: storage});
    return exports;
}

test('only a new visitor sees the enabled entrance', () => {
    const {shouldShowIntentGateway} = load(null);
    assert.equal(shouldShowIntentGateway(false, false, false), false);
    assert.equal(shouldShowIntentGateway(true, true, false), false);
    assert.equal(shouldShowIntentGateway(true, false, true), false);
    assert.equal(shouldShowIntentGateway(true, false, false), true);
});

test('choosing any path suppresses the entrance for the rest of the tab', () => {
    const values = new Map();
    const storage = {getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, value)};
    const entry = load(storage);
    assert.equal(entry.hasChosenEntryIntent(), false);
    entry.rememberEntryIntent();
    assert.equal(entry.hasChosenEntryIntent(), true);
});

test('blocked storage does not trap a customer at the entrance', () => {
    const entry = load({getItem() {throw Error('blocked');}, setItem() {throw Error('blocked');}});
    assert.equal(entry.hasChosenEntryIntent(), false);
    assert.doesNotThrow(() => entry.rememberEntryIntent());
});
