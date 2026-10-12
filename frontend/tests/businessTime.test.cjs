const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const ts = require('typescript');

const source = fs.readFileSync(path.join(__dirname, '../lib/businessTime.ts'), 'utf8');
const compiled = ts.transpileModule(source, {
    compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022}
}).outputText;
const exportsObject = {};
vm.runInNewContext(compiled, {
    exports: exportsObject,
    require: () => ({BUSINESS_TIME_ZONE: 'Asia/Kolkata', IST_TIME_FIX_ENABLED: true}),
    Date, Intl, Number
});
const {parseBusinessTimestamp, formatBusinessTimestamp, parseBusinessDate} = exportsObject;

test('legacy IST timestamp and explicit offset identify the same instant', () => {
    assert.equal(parseBusinessTimestamp('2026-09-25T00:15:00').toISOString(), '2026-09-24T18:45:00.000Z');
    assert.equal(parseBusinessTimestamp('2026-09-24T18:45:00Z').toISOString(), '2026-09-24T18:45:00.000Z');
    assert.equal(parseBusinessTimestamp('2026-09-25T00:15:00+05:30').toISOString(), '2026-09-24T18:45:00.000Z');
});

test('midnight service date stays 25 September in India', () => {
    assert.equal(parseBusinessDate('2026-09-25').toISOString(), '2026-09-25T06:30:00.000Z');
    assert.equal(formatBusinessTimestamp('2026-09-25T00:15:00', {
        day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit', hour12: false
    }), '25 Sept, 00:15');
});
