const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const ts = require('typescript');
const code=ts.transpileModule(fs.readFileSync(path.join(__dirname,'../services/menuApi.ts'),'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText;
function setup(constrained=false,onCombinedSignal=()=>{}){
 let now=0,nextTimer=0;const timers=new Map(),requests=[],images=[];const exports={};
 const advance=milliseconds=>{now+=milliseconds;for(const [id,timer] of timers){if(timer.at<=now){timers.delete(id);timer.callback();}}};
 vm.runInNewContext(code,{exports,process:{env:{}},Date:{now:()=>now},DOMException,AbortController,AbortSignal:{timeout:AbortSignal.timeout,any:signals=>{const signal=AbortSignal.any(signals);onCombinedSignal(signal);return signal;}},Image:class{set src(value){images.push(value);}},
  setTimeout:(callback,delay)=>{const id=++nextTimer;timers.set(id,{callback,at:now+delay});return id;},clearTimeout:id=>timers.delete(id),
  require:name=>name.includes('mobileConnection')?{constrainedPhoneConnection:()=>constrained}:name.includes('apiClient')?{apiClient:(_,options)=>new Promise((resolve,reject)=>{
   requests.push({resolve,signal:options.signal});options.signal?.addEventListener('abort',()=>reject(new DOMException('Request aborted','AbortError')),{once:true});
  })}:{getMockMenu:()=>[]}});
 return {api:exports,resolve:(value=[])=>requests.forEach(request=>request.resolve(value)),advance,
  get images(){return images;},get requests(){return requests.length;},get timers(){return timers.size;},signals:()=>requests.map(request=>request.signal)};
}
test('caller abort immediately rejects a stalled shared prefetch without a second request',async()=>{
 const fixture=setup(),warming=fixture.api.warmMenu(1),controller=new AbortController();
 const result=fixture.api.getMenu(1,controller.signal);
 controller.abort();
 await assert.rejects(Promise.race([result,new Promise((_,reject)=>setTimeout(()=>reject(new Error('Abort did not settle promptly')),100))]),error=>error.name==='AbortError');
 assert.equal(fixture.requests,1);
 assert.equal(fixture.signals()[0].aborted,false,'consumer cancellation must not abort the shared request');
 assert.equal(fixture.timers,1,'the request-level deadline stays armed');
 fixture.resolve([]);await warming;
 assert.equal(fixture.timers,0,'settlement releases the consumed request deadline');
});

test('consumed stalled warmup is deduplicated and aborted at its original deadline',async()=>{
 const f=setup(),warming=f.api.warmMenu(1),controller=new AbortController();
 const consuming=f.api.getMenu(1,controller.signal);controller.abort();
 await assert.rejects(consuming,error=>error.name==='AbortError');
 await f.api.warmMenu(1);assert.equal(f.requests,1,'a consumed in-flight request still prevents duplicate warmups');
 f.advance(14_999);assert.equal(f.signals()[0].aborted,false);assert.equal(f.timers,1);
 f.advance(1);assert.equal(f.signals()[0].aborted,true);await warming;assert.equal(f.timers,0);
 const next=f.api.warmMenu(1);assert.equal(f.requests,2);f.resolve();await next;
});

test('consumed active warmups count toward the request bound and remain evictable',async()=>{
 const f=setup(),first=f.api.warmMenu(1),controller=new AbortController();
 const consuming=f.api.getMenu(1,controller.signal);controller.abort();await assert.rejects(consuming);
 const others=[2,3,4].map(id=>f.api.warmMenu(id));
 assert.equal(f.signals()[0].aborted,true);assert.equal(f.timers,3);
 f.resolve();await Promise.all([first,...others]);f.advance(15_000);assert.equal(f.timers,0);
});

test('request deadline releases a waiting consumer into one fresh live fetch',async()=>{
 const f=setup(),warming=f.api.warmMenu(1),result=f.api.getMenu(1);
 f.advance(15_000);await warming;
 assert.equal(f.requests,2,'the timed-out prefetch falls back to a single live menu request');
 f.resolve([{products:[]}]);assert.equal((await result).length,1);assert.equal(f.timers,0);
});
test('warm menu settlement removes the consumer abort listener',async()=>{
 const listeners=new Set();
 const fixture=setup(false,signal=>{
  const add=signal.addEventListener.bind(signal),remove=signal.removeEventListener.bind(signal);
  signal.addEventListener=(type,listener,options)=>{listeners.add(listener);add(type,listener,options);};
  signal.removeEventListener=(type,listener)=>{listeners.delete(listener);remove(type,listener);};
 }),warming=fixture.api.warmMenu(1),controller=new AbortController();
 const result=fixture.api.getMenu(1,controller.signal);assert.equal(listeners.size,1);
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

test('a warmed service decision retains its receipt time when consumed later',async()=>{
 const f=setup(),warming=f.api.warmMenu(1);
 f.resolve([{id:1,products:[{id:1,serviceAvailability:{evaluatedAt:'2026-10-05T05:29:50Z',nextChangeAt:'2026-10-05T05:30:00Z',available:true}}]}]);
 await warming;f.advance(9000);const menu=await f.api.getMenu(1);
 assert.equal(menu[0].products[0].serviceAvailability.receivedMonotonic,0,'consumption must not restart the service deadline');
 assert.equal(f.requests,1);
});

test('constrained phone connections retain menu warmup without eagerly downloading product artwork',async()=>{
 const f=setup(true),warming=f.api.warmMenu(1);f.resolve([{products:[{id:1,imageUrl:'https://images.invalid/large.jpg'}]}]);await warming;assert.equal(f.images.length,0);assert.equal((await f.api.getMenu(1)).length,1);assert.equal(f.requests,1);
});
