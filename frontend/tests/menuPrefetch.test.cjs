const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const ts = require('typescript');
const code=ts.transpileModule(fs.readFileSync(path.join(__dirname,'../services/menuApi.ts'),'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText;
function setup(){
 let resolve,requests=0;
 const request=new Promise(done=>{resolve=done;});const exports={};
 vm.runInNewContext(code,{exports,process:{env:{}},Date,DOMException,Image:class{},require:name=>name.includes('apiClient')?{apiClient:()=>{requests++;return request;}}:{getMockMenu:()=>[]}});
 return {api:exports,resolve,get requests(){return requests;}};
}
test('caller abort immediately rejects a stalled shared prefetch without a second request',async()=>{
 const fixture=setup(),warming=fixture.api.warmMenu(1),controller=new AbortController();
 const result=fixture.api.getMenu(1,controller.signal);
 controller.abort();
 await assert.rejects(Promise.race([result,new Promise((_,reject)=>setTimeout(()=>reject(new Error('Abort did not settle promptly')),100))]),error=>error.name==='AbortError');
 assert.equal(fixture.requests,1);
 fixture.resolve([]);await warming;
});
test('warm menu settlement removes the consumer abort listener',async()=>{
 const fixture=setup(),warming=fixture.api.warmMenu(1),controller=new AbortController(),listeners=new Set();
 const signal=controller.signal,add=signal.addEventListener.bind(signal),remove=signal.removeEventListener.bind(signal);
 signal.addEventListener=(type,listener,options)=>{listeners.add(listener);add(type,listener,options);};
 signal.removeEventListener=(type,listener)=>{listeners.delete(listener);remove(type,listener);};
 const result=fixture.api.getMenu(1,signal);assert.equal(listeners.size,1);
 fixture.resolve([]);await result;await warming;assert.equal(listeners.size,0);assert.equal(fixture.requests,1);
});
