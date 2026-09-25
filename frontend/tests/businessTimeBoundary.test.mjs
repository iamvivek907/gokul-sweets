import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import vm from 'node:vm';
import ts from 'typescript';
import {fileURLToPath} from 'node:url';

const source = fs.readFileSync(path.join(path.dirname(fileURLToPath(import.meta.url)), '../lib/businessTime.ts'), 'utf8');
const compiled = ts.transpileModule(source, {
    compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022}
}).outputText;
const exportsObject = {};
vm.runInNewContext(compiled, {
    exports: exportsObject,
    require: () => ({BUSINESS_TIME_ZONE: 'Asia/Kolkata', IST_TIME_FIX_ENABLED: true}),
    Date, Intl, Number
});
const {formatBusinessTime, parseBusinessTimestamp, parseBusinessDate} = exportsObject;

test('India date and offset survive UTC year rollover without shifting on a foreign browser', () => {
    assert.equal(parseBusinessTimestamp('2027-01-01T00:00:00').toISOString(), '2026-12-31T18:30:00.000Z');
    assert.equal(parseBusinessTimestamp('2026-12-31T18:30:00Z').toISOString(), '2026-12-31T18:30:00.000Z');
    assert.equal(parseBusinessDate('2027-01-01').toISOString(), '2027-01-01T06:30:00.000Z');
});

test('pickup wall-time formatting is independent of device day and daylight-saving changes', () => {
    assert.equal(formatBusinessTime('00:05'), '12:05 am');
    assert.equal(formatBusinessTime('23:55:00'), '11:55 pm');
    assert.equal(formatBusinessTime('25:05'), '25:05');
});
