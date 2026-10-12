import {readFileSync} from 'node:fs';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const groups=JSON.parse(readFileSync(new URL('./browser-suites.json',import.meta.url),'utf8'));
const name=process.argv[2],files=groups[name];
if(!files?.length)throw new Error(`Unknown browser suite: ${name}`);
const cwd=fileURLToPath(new URL('../../frontend/',import.meta.url));
const started=Date.now();
// Repeat critical recovery scripts; fail immediately rather than hiding a flake with retries.
const recoveryScripts=new Set(['tests/printer-setup.browser.mjs','tests/payment-return-history.browser.mjs']);
const repeat=Number(process.env.BROWSER_RECOVERY_REPEATS??1);
if(!Number.isInteger(repeat)||repeat<1||repeat>3)throw new Error('Invalid recovery repeat count');
for(const file of files)for(let iteration=1;iteration<=(recoveryScripts.has(file)?repeat:1);iteration++){
    console.log(`::group::${name}: ${file} (iteration ${iteration})`);
    const scriptStarted=Date.now();
    const result=spawnSync(process.execPath,[file],{cwd,stdio:'inherit',env:process.env,timeout:600000});
    console.log(`${file}: ${((Date.now()-scriptStarted)/1000).toFixed(1)} seconds`);
    console.log('::endgroup::');
    if(result.error)console.error(result.error);
    if(result.error||result.status!==0)process.exit(result.status||1);
}
console.log(`${name}: ${files.length} scripts passed in ${((Date.now()-started)/1000).toFixed(1)} seconds`);
