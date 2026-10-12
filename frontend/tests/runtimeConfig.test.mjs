import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import vm from 'node:vm';
import ts from 'typescript';
import {fileURLToPath} from 'node:url';

const source = fs.readFileSync(path.join(path.dirname(fileURLToPath(import.meta.url)), '../lib/constants.ts'), 'utf8');
const compiled = ts.transpileModule(source, {
    compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022}
}).outputText;

function config(env) {
    const exportsObject = {};
    vm.runInNewContext(compiled, {exports: exportsObject, process: {env}, URL});
    return exportsObject;
}

test('local development has a local default and one admin/customer API origin', () => {
    const result = config({NODE_ENV: 'development'});
    assert.equal(result.API_BASE_URL, 'http://localhost:8080');
    assert.equal(result.ADMIN_API_BASE_URL, result.API_BASE_URL);
});

test('production requires a configured HTTPS origin', () => {
    assert.throws(() => config({NODE_ENV: 'production'}), /NEXT_PUBLIC_API_URL is required/);
    assert.throws(() => config({NODE_ENV: 'production', NEXT_PUBLIC_API_URL: 'http://localhost:8080'}), /HTTPS origin/);
    assert.equal(config({NODE_ENV: 'production', NEXT_PUBLIC_API_URL: 'https://api-dev.gokulsweets.in'}).API_BASE_URL,
        'https://api-dev.gokulsweets.in');
});

test('mismatched, malformed or credential-bearing origins fail instead of sending requests elsewhere', () => {
    assert.throws(() => config({NODE_ENV: 'production', NEXT_PUBLIC_API_URL: 'https://api-dev.gokulsweets.in',
        NEXT_PUBLIC_API_BASE_URL: 'https://api.gokulsweets.in'}), /must match/);
    assert.throws(() => config({NODE_ENV: 'production', NEXT_PUBLIC_API_URL: 'not-an-origin'}), /absolute URL/);
    assert.throws(() => config({NODE_ENV: 'production', NEXT_PUBLIC_API_URL: 'https://a.example/api'}), /HTTPS origin/);
    assert.throws(() => config({NODE_ENV: 'production', NEXT_PUBLIC_API_URL: 'https://user:pass@a.example'}), /HTTPS origin/);
});
