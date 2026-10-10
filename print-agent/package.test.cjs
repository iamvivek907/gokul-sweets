const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {execFileSync} = require('node:child_process');

test('installer ZIP and tracked payload are identical for LF and Windows CRLF checkouts', () => {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'gokul-package-'));
    try {
        const archives = [], payloads = [];
        for (const ending of ['\n', '\r\n']) {
            const checkout = path.join(root, archives.length.toString());
            const agent = path.join(checkout, 'print-agent');
            fs.mkdirSync(agent, {recursive: true});
            fs.mkdirSync(path.join(checkout, 'frontend/lib'), {recursive: true});
            fs.copyFileSync(path.join(__dirname, 'package.cjs'), path.join(agent, 'package.cjs'));
            for (const name of ['agent.py', 'background.py', 'requirements.txt', 'install.ps1', 'install.cmd']) {
                const source = fs.readFileSync(path.join(__dirname, name), 'utf8').replace(/\r\n?/g, '\n');
                fs.writeFileSync(path.join(agent, name), source.replace(/\n/g, ending));
            }
            execFileSync(process.execPath, [path.join(agent, 'package.cjs')]);
            archives.push(fs.readFileSync(path.join(agent, 'dist/gokul-print-agent.zip')));
            payloads.push(fs.readFileSync(path.join(checkout, 'frontend/lib/generatedPrintInstaller.ts')));
        }
        assert.deepEqual(archives[1], archives[0]);
        assert.deepEqual(payloads[1], payloads[0]);
        assert.ok(archives[0].includes(Buffer.from('@echo off\r\npowershell.exe')));
    } finally {
        fs.rmSync(root, {recursive: true, force: true});
    }
});
