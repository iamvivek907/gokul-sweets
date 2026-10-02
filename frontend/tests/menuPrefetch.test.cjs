const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const ts = require('typescript');
const code=ts.transpileModule(fs.readFileSync(path.join(__dirname,'../services/menuApi.ts'),'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText;
function setup(){
 let now=0,nextTimer=0;const timers=new Map(),requests=[];const exports={};
 const advance=milliseconds=>{now+=milliseconds;for(const [id,timer] of timers){if(timer.at<=now){timers.delete(id);timer.callback();}}};
 vm.runInNewContext(code,{exports,process:{env:{}},Date:{now:()=>now},DOMException,AbortController,Image:class{},
  setTimeout:(callback,delay)=>{const id=++nextTimer;timers.set(id,{callback,at:now+delay});return id;},clearTimeout:id=>timers.delete(id),
  require:name=>name.includes('apiClient')?{apiClient:(_,options)=>new Promise((resolve,reject)=>{
   requests.push({resolve,signal:options.signal});options.signal?.addEventListener('abort',()=>reject(new DOMException('Request aborted','AbortError')),{once:true});
  })}:{getMockMenu:()=>[]}});
 return {api:exports,resolve:(value=[])=>requests.forEach(request=>request.resolve(value)),advance,
  get requests(){return requests.length;},get timers(){return timers.size;},signals:()=>requests.map(request=>request.signal)};
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

test('unconsumed prefetch expires and allows a fresh warmup for the same branch',async()=>{
 const f=setup(),first=f.api.warmMenu(1);f.resolve();await first;
 assert.equal(f.timers,1);f.advance(15_000);assert.equal(f.timers,0);
 const second=f.api.warmMenu(1);assert.equal(f.requests,2);f.resolve();await second;
 const result=await f.api.getMenu(1);assert.deepEqual(Array.from(result),[]);assert.equal(f.requests,2);assert.equal(f.timers,0);
});
test('warmup cache caps unused branches and aborts the oldest request',async()=>{
 const f=setup(),warmings=[1,2,3,4].map(id=>f.api.warmMenu(id));
 assert.equal(f.timers,3);assert.equal(f.signals()[0].aborted,true);
 f.resolve();await Promise.all(warmings);
 await f.api.getMenu(2);assert.equal(f.requests,4);
 const fresh=f.api.warmMenu(1);assert.equal(f.requests,5);f.resolve();await fresh;
 f.advance(15_000);assert.equal(f.timers,0);
});
test('late completion of an expired request cannot remove its replacement',async()=>{
 const f=setup(),first=f.api.warmMenu(1);f.advance(15_000);
 const second=f.api.warmMenu(1);await first;f.resolve();await second;
 await f.api.getMenu(1);assert.equal(f.requests,2);assert.equal(f.timers,0);
});
