import {readFileSync} from 'node:fs';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const groups=JSON.parse(readFileSync(new URL('./browser-suites.json',import.meta.url),'utf8'));
const name=process.argv[2],files=groups[name];
if(!files?.length)throw new Error(`Unknown browser suite: ${name}`);
const cwd=fileURLToPath(new URL('../../frontend/',import.meta.url));
const started=Date.now();
for(const file of files){
    console.log(`::group::${name}: ${file}`);
    const scriptStarted=Date.now();
    const result=spawnSync(process.execPath,[file],{cwd,stdio:'inherit',env:process.env,timeout:600000});
    console.log(`${file}: ${((Date.now()-scriptStarted)/1000).toFixed(1)} seconds`);
    console.log('::endgroup::');
    if(result.error)console.error(result.error);
    if(result.error||result.status!==0)process.exit(result.status||1);
}
console.log(`${name}: ${files.length} scripts passed in ${((Date.now()-started)/1000).toFixed(1)} seconds`);
