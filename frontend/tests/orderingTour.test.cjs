const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const ts = require('typescript');
const source = ts.transpileModule(fs.readFileSync(require('node:path').join(__dirname, '../lib/orderingTour.ts'), 'utf8'), {
    compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022}
}).outputText;
function load(storage = new Map(), blocked = false) {
    const exports = {}, listeners = new Map();
    const window = {
        localStorage: {getItem: key => {if (blocked) throw Error('blocked'); return storage.get(key);}, setItem: (key, value) => {if (blocked) throw Error('blocked'); storage.set(key, value);}},
        addEventListener: (name, fn) => listeners.set(name, fn), removeEventListener: name => listeners.delete(name), dispatchEvent: event => listeners.get(event.type)?.(event)
    };
    vm.runInNewContext(source, {exports, window, Event});
    return exports;
}
test('skip or start is remembered across page loads and notifies the mounted invitation', () => {
    const storage = new Map(), tour = load(storage); let changes = 0;
    const unsubscribe = tour.subscribeOrderingTour(() => changes++);
    assert.equal(tour.orderingTourSeen(), false);
    tour.dismissOrderingTour();
    assert.equal(changes, 1);
    assert.equal(load(storage).orderingTourSeen(), true);
    unsubscribe();
});
test('blocked storage still remembers dismissal for the current app session', () => {
    const tour = load(new Map(), true);
    assert.equal(tour.orderingTourSeen(), false);
    assert.doesNotThrow(() => tour.dismissOrderingTour());
    assert.equal(tour.orderingTourSeen(), true);
    assert.equal(tour.orderingTourServerSnapshot(), true);
});
