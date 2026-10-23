const assert=require('node:assert/strict'),test=require('node:test'),fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript');
class ApiError extends Error{constructor(status){super();this.status=status;}}
function fixture(){
 let identity,requests=[],timers=new Map(),nextTimer=0;
 const document=new EventTarget();document.visibilityState='visible';const window=new EventTarget(),navigator={onLine:true};
 const exports={};vm.runInNewContext(ts.transpileModule(fs.readFileSync('lib/activeOrderStore.ts','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText,{
  exports,Set,AbortController,AbortSignal,document,window,navigator,
  setTimeout:(fn,ms)=>{const id=++nextTimer;timers.set(id,{fn,ms});return id;},clearTimeout:id=>timers.delete(id),
  require:path=>path.includes('orderApi')?{getVerifiedOrderPage:(before,signal,filters)=>new Promise((resolve,reject)=>requests.push({signal,filters,resolve,reject}))}:path.includes('apiClient')?{ApiError}:{subscribeCustomerIdentityChanges:fn=>{identity=fn;return()=>{identity=null;};}}
 });
 return {...exports,requests,timers,document,window,navigator,identity:()=>identity(),flush:()=>new Promise(resolve=>setImmediate(resolve))};
}
test('active-order store shares one bounded request and clears on identity change, ignoring stale responses',async()=>{
 const f=fixture(),stop=f.subscribeActiveOrder(()=>{}),second=f.subscribeActiveOrder(()=>{});
 assert.equal(f.requests.length,1);assert.equal(f.requests[0].filters.limit,'1');assert.equal(f.requests[0].filters.status,'ACTIVE');
 f.identity();assert.equal(f.requests[0].signal.aborted,true);assert.equal(f.getActiveOrder().order,null);
 f.requests[0].resolve({orders:[{orderNumber:'OLD',orderStatus:'CONFIRMED'}]});await f.flush();assert.equal(f.getActiveOrder().order,null);
 f.requests[1].resolve({orders:[{orderNumber:'NEW',orderStatus:'PREPARING'}]});await f.flush();assert.equal(f.getActiveOrder().order.orderNumber,'NEW');assert.equal([...f.timers.values()][0].ms,15000);
 stop();assert.equal(f.getActiveOrder().order.orderNumber,'NEW');second();assert.equal(f.getActiveOrder().order,null);assert.equal(f.timers.size,0);
});
test('hidden/offline stores stop requests, guest responses clear data, errors mark old data stale',async()=>{
 const f=fixture(),stop=f.subscribeActiveOrder(()=>{});
 f.requests[0].resolve({orders:[{orderNumber:'ONE',orderStatus:'CONFIRMED'}]});await f.flush();
 f.document.visibilityState='hidden';f.document.dispatchEvent(new Event('visibilitychange'));assert.equal(f.timers.size,0);assert.equal(f.getActiveOrder().stale,true);
 f.document.visibilityState='visible';f.document.dispatchEvent(new Event('visibilitychange'));assert.equal(f.requests.length,2);
 f.requests[1].reject(new Error('offline'));await f.flush();assert.equal(f.getActiveOrder().stale,true);
 f.navigator.onLine=false;f.window.dispatchEvent(new Event('offline'));assert.equal(f.timers.size,0);
 f.navigator.onLine=true;f.window.dispatchEvent(new Event('online'));f.requests.at(-1).reject(new ApiError(401));await f.flush();assert.equal(f.getActiveOrder().order,null);assert.equal([...f.timers.values()][0].ms,60000);stop();
});
