const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript'),test=require('node:test');
function harness(saved=null){
 let now=1000000,calls=0,failure=false,timerId=0;
 const effects=[],timers=new Map(),events=new Map(),storage=new Map(saved?[['gokul-storefront-settings',JSON.stringify(saved)]]:[]);
 function listen(name,fn){const set=events.get(name)??new Set();set.add(fn);events.set(name,set);}
 function remove(name,fn){events.get(name)?.delete(fn);}
 const document={visibilityState:'visible',addEventListener:listen,removeEventListener:remove},navigator={onLine:true},exported={};
 const source=fs.readFileSync('hooks/useStorefrontFeatures.ts','utf8')+'\nexport const testState = () => state;';
 vm.runInNewContext(ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText,{
  exports:exported,require:name=>name==='react'?{useEffect:fn=>effects.push(fn),useSyncExternalStore:(_s,snapshot)=>snapshot()}:{apiClient:async()=>{calls++;if(failure)throw Error('cold start');return {customerHomeV2:true};}},
  Date:{now:()=>now},JSON,Promise,AbortSignal,console:{warn(){}},document,navigator,
  sessionStorage:{getItem:key=>storage.get(key)??null,setItem:(key,value)=>storage.set(key,value)},window:{addEventListener:listen,removeEventListener:remove},
  setTimeout:(fn,delay)=>{const id=++timerId;timers.set(id,{fn,at:now+delay});return id;},clearTimeout:id=>timers.delete(id)
 });
 const flush=async()=>{for(let i=0;i<6;i++)await Promise.resolve();};
 return {exported,document,navigator,timers,storage,get calls(){return calls;},fail:value=>failure=value,
  mount:async()=>{exported.useStorefrontConfiguration();const cleanup=effects.pop()();await flush();return cleanup;},
  fire:async name=>{for(const fn of events.get(name)??[])fn();await flush();},
  advance:async ms=>{now+=ms;for(const[id,t]of[...timers.entries()].filter(([,t])=>t.at<=now)){timers.delete(id);t.fn();}await flush();}
 };
}
test('cold start deduplicates consumers and automatically recovers',async()=>{
 const h=harness();h.fail(true);const a=await h.mount(),b=await h.mount();assert.equal(h.calls,1);assert.equal(h.timers.size,1);assert.ok(h.exported.testState().error);
 await h.advance(15000);assert.equal(h.calls,2);h.fail(false);await h.advance(15000);assert.equal(h.calls,3);assert.equal(h.exported.testState().error,null);assert.equal(h.exported.testState().features.customerHomeV2,true);a();b();assert.equal(h.timers.size,0);
});
test('background failures retain flags and unchanged recovery retains object identity',async()=>{
 const h=harness(),cleanup=await h.mount(),flags=h.exported.testState().features;await h.advance(60000);assert.equal(h.exported.testState().features,flags);
 h.fail(true);await h.advance(60000);assert.equal(h.exported.testState().features,flags);assert.ok(h.exported.testState().error);h.fail(false);await h.advance(15000);assert.equal(h.exported.testState().features,flags);assert.equal(h.exported.testState().error,null);cleanup();
});
test('hidden/offline tabs pause and returning online or remounting resumes one timer',async()=>{
 const h=harness();let cleanup=await h.mount();h.document.visibilityState='hidden';await h.advance(60000);assert.equal(h.calls,1);h.document.visibilityState='visible';h.navigator.onLine=false;await h.fire('visibilitychange');assert.equal(h.calls,1);
 h.navigator.onLine=true;await h.fire('online');assert.equal(h.calls,2);cleanup();cleanup=await h.mount();assert.equal(h.calls,2);assert.equal(h.timers.size,1);await h.advance(60000);assert.equal(h.calls,3);cleanup();
});
test('cached flags survive failed checks without touching cart storage',async()=>{
 const h=harness({features:{customerHomeV2:true},checkedAt:999999,error:null});h.storage.set('gokul-cart','customer cart');h.fail(true);const cleanup=await h.mount();assert.equal(h.exported.testState().features.customerHomeV2,true);assert.equal(h.storage.get('gokul-cart'),'customer cart');assert.ok(h.exported.testState().error);cleanup();
});
