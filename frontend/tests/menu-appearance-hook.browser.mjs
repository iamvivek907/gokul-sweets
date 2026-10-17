import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {mkdtemp, readFile, writeFile, rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import path from 'node:path';
const require=createRequire(import.meta.url);
const {chromium}=require(process.env.PLAYWRIGHT_MODULE??'playwright');
const ts=require('typescript');
const {webpack}=require('next/dist/compiled/webpack/webpack');
const temporary=await mkdtemp(path.join(tmpdir(),'menu-appearance-hook-'));
let browser;
try {
 // Real React keeps the hook mounted; the menu's branch guard can mask stale state by remounting.
 const source=await readFile(new URL('../hooks/useBranchMenuAppearance.ts',import.meta.url),'utf8');
 await writeFile(path.join(temporary,'hook.js'),ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.ESNext,target:ts.ScriptTarget.ES2022}}).outputText);
 await writeFile(path.join(temporary,'api.js'),`export function apiClient(url,options){return new Promise((resolve,reject)=>window.appearanceReads.push({url,signal:options.signal,resolve,reject}));}`);
 await writeFile(path.join(temporary,'entry.js'),`
 import React,{useState} from 'react';
 import {createRoot} from 'react-dom/client';
 import {flushSync} from 'react-dom';
 import {useBranchMenuAppearance} from './hook.js';
 window.appearanceReads=[];
 function Probe(){const [id,setId]=useState(1);window.selectAppearanceBranch=id=>flushSync(()=>setId(id));const config=useBranchMenuAppearance(id);return React.createElement('output',{id:'appearance'},JSON.stringify(config));}
 flushSync(()=>createRoot(document.getElementById('root')).render(React.createElement(Probe)));
 `);
 await new Promise((resolve,reject)=>webpack({mode:'development',devtool:false,entry:path.join(temporary,'entry.js'),output:{path:temporary,filename:'bundle.js'},resolve:{alias:{react:require.resolve('react'),'react-dom':path.dirname(require.resolve('react-dom/package.json')),'@/services/apiClient':path.join(temporary,'api.js')}}},(error,stats)=>error||stats.hasErrors()?reject(error??new Error(stats.toString())):resolve()));
 browser=await chromium.launch({headless:true});
 const page=await browser.newPage();
 await page.setContent('<div id="root"></div>');
 await page.addScriptTag({path:path.join(temporary,'bundle.js')});
 const current=async()=>JSON.parse(await page.locator('#appearance').textContent());
 const readCount=async count=>page.waitForFunction(count=>window.appearanceReads.length===count,count);
 const select=async id=>page.evaluate(id=>window.selectAppearanceBranch(id),id);
 const config={banners:[{key:'old-A'}],categories:[{id:1,order:7}]};
 await readCount(1);await page.evaluate(config=>window.appearanceReads[0].resolve(config),config);
 await page.waitForFunction(()=>document.querySelector('#appearance').textContent.includes('old-A'));
 assert.deepEqual(await current(),config);
 await select(2);await readCount(2);assert.equal(await current(),null);
 assert.equal(await page.evaluate(()=>window.appearanceReads[0].signal.aborted),true);
 await page.evaluate(()=>window.appearanceReads[1].reject(new Error('B unavailable')));
 await select(1);await readCount(3);
 assert.equal(await current(),null,'returning to A must discard its previous banner AND category ordering while pending');
 await page.evaluate(()=>window.appearanceReads[2].reject(new Error('A unavailable')));
 assert.equal(await current(),null,'a failed read must not restore A’s previous appearance');
 // A superseded response must be ignored even if the API does not honor abort.
 await select(2);await readCount(4);await select(1);await readCount(5);
 const fresh={banners:[{key:'new-A'}],categories:[]};
 await page.evaluate(config=>window.appearanceReads[4].resolve(config),fresh);
 await page.waitForFunction(()=>document.querySelector('#appearance').textContent.includes('new-A'));
 await page.evaluate(()=>window.appearanceReads[3].resolve({banners:[{key:'late-B'}],categories:[]}));
 assert.deepEqual(await current(),fresh);
 assert.equal(await page.evaluate(()=>window.appearanceReads[3].signal.aborted),true);
 await select(null);assert.equal(await current(),null);
 await select(1);await readCount(6);assert.equal(await current(),null);
 await page.evaluate(()=>window.appearanceReads[5].resolve({banners:null,categories:[]}));
 assert.equal(await current(),null,'invalid data must not restore the old appearance');
 console.log('Menu appearance hook: pending/failed A → B → A, late responses, null branch and invalid data passed');
} finally {await browser?.close();await rm(temporary,{recursive:true,force:true});}
