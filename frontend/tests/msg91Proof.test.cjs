const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const ts = require('typescript');

const source = fs.readFileSync(path.join(__dirname, '../lib/msg91Proof.ts'), 'utf8');
const compiled = ts.transpileModule(source, {
    compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022}
}).outputText;
const exportsObject = {};
vm.runInNewContext(compiled, {exports: exportsObject});

test('only a bounded provider proof reaches server-side exchange', () => {
    const {proofFromWidget} = exportsObject;
    assert.equal(proofFromWidget({accessToken: 'provider-token'}), 'provider-token');
    assert.equal(proofFromWidget({type: 'success', message: 'header.payload.signature'}), 'header.payload.signature');
    assert.equal(proofFromWidget({type: 'success', message: 'OTP verified successfully'}), null);
    assert.equal(proofFromWidget({type: 'error', accessToken: 'provider-token'}), null);
    assert.equal(proofFromWidget({data: {'access-token': 'provider-jwt'}}), 'provider-jwt');
    assert.equal(proofFromWidget('provider-token'), 'provider-token');
    assert.equal(proofFromWidget({phone: '919876543210'}), null);
    assert.equal(proofFromWidget({type: 'error', message: 'provider-jwt'}), null);
    assert.equal(proofFromWidget({accessToken: ''}), null);
    assert.equal(proofFromWidget({accessToken: 'a'.repeat(4097)}), null);
});
