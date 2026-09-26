const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const ts = require('typescript');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '../lib/entryIntent.ts'), 'utf8');
const compiled = ts.transpileModule(source, {
    compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022}
}).outputText;
const entry = {};
vm.runInNewContext(compiled, {exports: entry});

test('the enabled entrance appears for each new home opening and advances after a choice', () => {
    assert.equal(entry.shouldShowIntentGateway(false, false), false);
    assert.equal(entry.shouldShowIntentGateway(true, false), true);
    assert.equal(entry.shouldShowIntentGateway(true, true), false);
    assert.equal(entry.shouldShowIntentGateway(true, false), true);
});

test('the home route does not bypass the entrance for a saved branch or tab storage', () => {
    const home = fs.readFileSync(path.join(__dirname, '../components/menu/HomeEntry.tsx'), 'utf8');
    assert.match(home, /shouldShowIntentGateway\(features\.preHomeIntentGateway, entered\)/);
    assert.doesNotMatch(home, /hasChosenEntryIntent|sessionStorage|Boolean\(branch\)/);
});
