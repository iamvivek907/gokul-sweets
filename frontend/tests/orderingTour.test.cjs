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
        localStorage: {getItem: key => {if (blocked) throw Error('blocked'); return storage.get(key);}, setItem: (key, value) => {if (blocked) throw Error('blocked'); storage.set(key, value);}, removeItem: key => {if (blocked) throw Error('blocked'); storage.delete(key); }},
        addEventListener: (name, fn) => listeners.set(name, fn), removeEventListener: name => listeners.delete(name), dispatchEvent: event => listeners.get(event.type)?.(event)
    };
    vm.runInNewContext(source, {exports, window, localStorage: window.localStorage, Event});
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

test('walkthrough follows actual actions and reload recovers progress without another invitation', () => {
    const storage = new Map(), tour = load(storage);
    tour.startWalkthrough(null, false, false, true);
    let state = tour.parseWalkthrough(tour.getWalkthroughSnapshot());
    assert.equal(state.step, 'branch');
    assert.equal(tour.followWalkthrough(state, 1, false, false, true), 'menu');
    tour.updateWalkthrough('menu', 1);
    assert.equal(load(storage).parseWalkthrough(load(storage).getWalkthroughSnapshot()).step, 'menu');
    state = tour.parseWalkthrough(tour.getWalkthroughSnapshot());
    assert.equal(tour.followWalkthrough(state, 1, true, false, true), 'pickup');
    tour.updateWalkthrough('pickup', 1);
    tour.confirmWalkthroughPickup(2);
    assert.equal(tour.parseWalkthrough(tour.getWalkthroughSnapshot()).step, 'pickup');
    tour.confirmWalkthroughPickup(1);
    state = tour.parseWalkthrough(tour.getWalkthroughSnapshot());
    assert.equal(state.step, 'add');
    assert.equal(tour.followWalkthrough(state, 1, true, false, true), 'add');
    assert.equal(tour.followWalkthrough(state, 1, true, true, true), 'cart');
    tour.stopWalkthrough();
    assert.equal(load(storage).getWalkthroughSnapshot(), '');
    assert.equal(load(storage).orderingTourSeen(), true);
});
test('completed or dismissed walkthrough stops in other tabs and never starts tomorrow', () => {
    const storage = new Map(), first = load(storage), second = load(storage);
    first.startWalkthrough(1, true, false, true);
    assert.ok(second.parseWalkthrough(second.getWalkthroughSnapshot()));
    second.stopWalkthrough();
    assert.equal(first.getWalkthroughSnapshot(), '');
    assert.equal(load(storage).orderingTourSeen(), true);
    first.startWalkthrough(1, true, false, true);
    const raw = first.getWalkthroughSnapshot();
    assert.equal(first.parseWalkthrough(raw, Date.now() + 86400000), null);
    assert.equal(load(storage).orderingTourSeen(), true);
});
test('leaving Menu shows navigation help and refresh preserves the actual ordering step', () => {
    for (const step of ['pickup', 'add', 'cart']) {
        const storage = new Map(), tour = load(storage);
        tour.startWalkthrough(1, true, false, true);
        tour.updateWalkthrough(step, 1);
        const before = tour.parseWalkthrough(tour.getWalkthroughSnapshot());
        assert.equal(tour.followWalkthrough(before, 1, false, step === 'cart', true), 'menu');
        tour.updateWalkthrough('menu', 1);
        const restored = load(storage).parseWalkthrough(load(storage).getWalkthroughSnapshot());
        assert.equal(restored.step, step);
        assert.equal(restored.expiresAt, before.expiresAt);
        assert.equal(tour.followWalkthrough(restored, 1, true, step === 'cart', true), step);
        tour.updateWalkthrough('menu', 2);
        assert.equal(tour.parseWalkthrough(tour.getWalkthroughSnapshot()).step, 'menu', 'another branch resets progress');
    }
});
test('blocked storage supports an interactive visit and bounded malformed sessions stay inactive', () => {
    const tour = load(new Map(), true);
    tour.startWalkthrough(null, false, false, true);
    assert.equal(tour.parseWalkthrough(tour.getWalkthroughSnapshot()).step, 'branch');
    tour.stopWalkthrough(); assert.equal(tour.getWalkthroughSnapshot(), '');
    for (const value of [null, {}, {step:'pay',branchId:1,expiresAt:Date.now()+1000}, {step:'branch',branchId:0,expiresAt:Date.now()+1000}, {step:'branch',branchId:1,expiresAt:Date.now()+86400000}]) {
        assert.equal(tour.parseWalkthrough(JSON.stringify(value)), null);
    }
});
