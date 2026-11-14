const assert=require('node:assert/strict'),test=require('node:test'),fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript');
class ApiError extends Error {
 constructor(status,code,retryAfterMs=null){
  super(code);Object.assign(this,{status,code,retryAfterMs});
 }
}
function fixture(respond,timers={setTimeout,clearTimeout}){const calls=[],box={exports:{},TypeError,AbortController,DOMException,...timers,require:()=>({ApiError,apiClient:async(path,options)=>{const body=JSON.parse(options.body);calls.push({path,body,signal:options.signal});return respond(body,calls.length);}})};vm.runInNewContext(ts.transpileModule(fs.readFileSync('services/availabilityApi.ts','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText,box);return {...box.exports,calls};}
const items=Array.from({length:215},(_,i)=>({productId:i+1,quantity:i===0?7:1,weightGrams:null}));
const response=body=>({fulfilmentType:'PICKUP',today:'2026-10-07',maximumDate:'2026-10-10',dates:[{date:body.startDate,available:true,items:body.items.map(p=>({productId:p.productId,available:true})),slots:[{slot:{id:1,remainingCapacity:10},normalAvailable:true,priorityAvailable:false,issues:[]}]}]});
test('full catalogue uses bounded two-request waves and preserves actual cart quantities',async()=>{const f=fixture(response);const data=await f.checkMenuAvailability(1,'2026-10-08',1,items);assert.deepEqual(f.calls.map(c=>c.body.items.length),[100,100,15]);assert.equal(f.calls[0].body.items[0].quantity,7);assert.ok(f.calls.every(c=>c.path.endsWith('?menuPreview=true&compact=true&versioned=true')));assert.deepEqual(Array.from(data.dates[0].items,p=>p.productId),items.map(p=>p.productId));});
test('issues from later batches and pickup-window restrictions survive merging',async()=>{const f=fixture((body,n)=>{const data=response(body);if(n===2){const slot=data.dates[0].slots[0];slot.issues=[{productId:113,available:false}];slot.code='PICKUP_WINDOW';slot.normalAvailable=false;slot.reason='Closed';data.dates[0].available=false;}return data;});const data=await f.checkMenuAvailability(1,'2026-10-08',1,items);assert.equal(data.dates[0].slots[0].issues[0].productId,113);assert.equal(data.dates[0].slots[0].code,'PICKUP_WINDOW');assert.equal(data.dates[0].slots[0].normalAvailable,false);assert.equal(data.dates[0].available,false);});
test('failed batch rejects incomplete preview and stops later requests',async()=>{const f=fixture((body,n)=>{if(n===2)throw Error('offline');return response(body)});await assert.rejects(f.checkMenuAvailability(1,'2026-10-08',1,items),/offline/);assert.equal(f.calls.length,2);});
test('failed preview cancels its in-flight sibling and leaves the parent signal usable',async()=>{
 const parent=new AbortController();
 const f=fixture((_,n)=>{
  if(n===2)throw Error('batch failed');
  const signal=f.calls[0].signal;
  return new Promise((_,reject)=>signal.addEventListener('abort',()=>reject(signal.reason),{once:true}));
 });
 await assert.rejects(f.checkMenuAvailability(1,'2026-10-08',1,items,parent.signal),/batch failed/);
 assert.equal(f.calls.length,2);
 assert.equal(f.calls[0].signal.aborted,true);
 assert.equal(parent.signal.aborted,false);
});
test('repeated completed previews release their parent abort listeners',async()=>{
 const {getEventListeners}=require('node:events');
 const parent=new AbortController();
 const f=fixture(response);
 for(let index=0;index<100;index++){
  await f.checkMenuAvailability(1,'2026-10-08',1,items.slice(0,2),parent.signal,1);
 }
 assert.equal(getEventListeners(parent.signal,'abort').length,0);
});
test('context cancellation stops subsequent batches',async()=>{const controller=new AbortController();const f=fixture(body=>{controller.abort();return response(body)});await assert.rejects(f.checkMenuAvailability(1,'2026-10-08',1,items,controller.signal),{name:'AbortError'});assert.equal(f.calls.length,1);});
test('authoritative cart request remains a single whole-cart check',async()=>{const f=fixture(response);await f.checkCartAvailability(1,'2026-10-08',1,items.slice(0,2));assert.equal(f.calls.length,1);assert.equal(f.calls[0].path,'/api/branches/1/availability');});
test('every saved-pickup preview batch uses the same selected slot',async()=>{
 const f=fixture(response);
 const result=await f.checkMenuAvailability(1,'2026-10-08',1,items,undefined,42);
 assert.equal(f.calls.length,3);
 assert.ok(f.calls.every(call=>call.path.endsWith('&slotId=42')));
 assert.equal(result.dates[0].items.length,215);
});
test('a selected slot missing from any batch is removed from the merged preview',async()=>{
 const f=fixture((body,n)=>{
  const data=response(body);
  if(n===2)data.dates[0].slots=[];
  return data;
 });
 const result=await f.checkMenuAvailability(1,'2026-10-08',1,items,undefined,1);
 assert.equal(result.dates[0].slots.length,0);
});
test('a later capacity change closes the affected pickup type across batches',async()=>{
 const f=fixture((body,n)=>{
  const data=response(body),slot=data.dates[0].slots[0];
  slot.slot={...slot.slot,remainingCapacity:n===2?0:10,priorityEnabled:true,priorityRemainingCapacity:2};
  slot.normalAvailable=n!==2;
  slot.priorityAvailable=true;
  return data;
 });
 const result=await f.checkMenuAvailability(1,'2026-10-08',1,items,undefined,1);
 const slot=result.dates[0].slots[0];
 assert.equal(slot.slot.remainingCapacity,0);
 assert.equal(slot.slot.priorityRemainingCapacity,2);
 assert.equal(slot.priorityAvailable,true);
});
test('compact issues restore exact slot decisions across independent batch catalogs',async()=>{
 const f=fixture((body,n)=>{const data=response(body),day=data.dates[0];
  const issue={...day.items[0],available:false,code:n===1?'NOT_READY':'SERVICE_WINDOW',reason:'Choose another time',expectedReadyAt:'2026-10-08T12:00:00'};
  data.issueCatalog=[issue];day.slots[0].issueIndexes=[0,0];delete day.slots[0].issues;
  day.slots.push({...day.slots[0],slot:{id:2},issueIndexes:[]});return data;
 });
 const data=await f.checkMenuAvailability(1,'2026-10-08',1,items);
 assert.deepEqual(Array.from(data.dates[0].slots[0].issues,i=>[i.productId,i.code]),[[1,'NOT_READY'],[1,'NOT_READY'],[101,'SERVICE_WINDOW'],[101,'SERVICE_WINDOW'],[201,'SERVICE_WINDOW'],[201,'SERVICE_WINDOW']]);
 assert.equal(data.dates[0].slots[1].issues.length,0);assert.equal('issueCatalog' in data,false);assert.equal('issueIndexes' in data.dates[0].slots[0],false);
});
test('invalid compact issue indexes reject the entire preview before another batch',async()=>{
 for(const index of [-1,1,0.5]){
  const f=fixture(body=>{const data=response(body);data.issueCatalog=[{productId:1}];data.dates[0].slots[0].issueIndexes=[index];return data;});
  await assert.rejects(f.checkMenuAvailability(1,'2026-10-08',1,items),/Invalid menu preview issue index/);assert.equal(f.calls.length,2);
 }
});

test('merged preview expires at the earliest batch boundary',async()=>{const f=fixture((body,n)=>({...response(body),recheckAt:n===2?'2026-10-08T10:00:00Z':'2026-10-08T10:00:00.100Z'}));const data=await f.checkMenuAvailability(1,'2026-10-08',1,items);assert.equal(data.recheckAt,'2026-10-08T10:00:00Z');});

test('at most two requests run together and no partial wave is published',async()=>{let active=0,peak=0;const releases=[];const f=fixture(body=>{active++;peak=Math.max(peak,active);return new Promise(resolve=>releases.push(()=>{active--;resolve(response(body))}))});let done=false;const pending=f.checkMenuAvailability(1,'2026-10-08',1,items).then(data=>{done=true;return data});await new Promise(r=>setImmediate(r));assert.equal(f.calls.length,2);assert.equal(done,false);releases.shift()();await new Promise(r=>setImmediate(r));assert.equal(f.calls.length,2);releases.shift()();await new Promise(r=>setImmediate(r));assert.equal(f.calls.length,3);releases.shift()();const data=await pending;assert.equal(peak,2);assert.equal(data.dates[0].items.length,215)});
test('cutoff deadlines use server observation and monotonic receipt, without the phone clock',()=>{const f=fixture(response);assert.equal(f.menuPreviewDeadline({recheckAt:'2026-10-22T08:00:10Z',observedAt:'2026-10-22T08:00:00Z',receivedMonotonic:500}),10500);assert.equal(f.menuPreviewDeadline({recheckAt:'2026-10-22T08:00:00Z',observedAt:'2026-10-22T08:00:10Z',receivedMonotonic:500}),500);assert.equal(f.menuPreviewDeadline({recheckAt:'2026-10-22T08:00:00Z'}),null)});
test('merged batches retain the earliest server-relative deadline as a matched tuple',async()=>{const f=fixture((body,n)=>({...response(body),recheckAt:n===2?'2026-10-22T08:00:20Z':'2026-10-22T08:00:10Z',observedAt:n===2?'2026-10-22T08:00:19Z':'2026-10-22T08:00:00Z'}));const data=await f.checkMenuAvailability(1,'2026-10-22',1,items);assert.equal(data.recheckAt,'2026-10-22T08:00:20Z');assert.equal(data.observedAt,'2026-10-22T08:00:19Z');assert.equal(f.menuPreviewDeadline(data),1000)});
test('aborting while a batch fails leaves every rejection observed',async()=>{const controller=new AbortController();const f=fixture(()=>{controller.abort();throw Error('network failed')});await assert.rejects(f.checkMenuAvailability(1,'2026-10-22',1,items,controller.signal));assert.equal(f.calls.length,1)});

test('meal recipe claims never leak into split stock reads; all amounts are preserved',async()=>{const f=fixture(response);const input=items.map((item,index)=>index===0?{...item,mealSelections:[{mealId:1,optionIds:['later-batch']}]}:item);await f.checkMenuAvailability(1,'2026-10-08',1,input);assert.equal(f.calls.length,3);assert.equal(f.calls[0].body.items[0].quantity,7);assert.ok(f.calls.every(call=>call.body.items.every(item=>!('mealSelections' in item))));assert.equal(input[0].mealSelections.length,1,'pricing input remains intact');});

test('a transient batch retry reuses the successful batch and publishes a complete response',async()=>{
 const f=fixture((body,n)=>{if(n===2)throw new TypeError('temporary network failure');return response(body)});
 const data=await f.checkMenuAvailability(1,'2026-10-08',1,items);
 assert.deepEqual(f.calls.map(call=>call.body.items[0].productId),[1,101,101,201]);
 assert.equal(data.dates[0].items.length,215);
});

test('malformed availability stops the preview before unusable slot data can reach the menu',async()=>{
 const f=fixture((body,n)=>{const data=response(body);if(n===2)data.dates[0].slots=[null];return data;});
 await assert.rejects(f.checkMenuAvailability(1,'2026-10-08',1,items),/We couldn’t check pickup availability/);
 assert.equal(f.calls.length,2);
});

test('connection failures and a temporary server error recover before a menu error is shown',async()=>{
 const f=fixture(response);
 let attempts=0;
 const value=await f.retryMenuRead(async()=>{
  attempts++;
  if(attempts===1)throw new ApiError(0,'CONNECTION_FAILED');
  if(attempts===2)throw new ApiError(503,'UNAVAILABLE');
  return 'ready';
 });
 assert.equal(value,'ready');
 assert.equal(attempts,3);
});
test('persistent failures stop after two retries and validation failures stop immediately',async()=>{
 const f=fixture(response);
 for(const [status,expected] of [[503,3],[400,1]]){
  let attempts=0;
  await assert.rejects(f.retryMenuRead(async()=>{attempts++;throw new ApiError(status,'FAILED');}),/FAILED/);
  assert.equal(attempts,expected);
 }
});
test('navigation cancels retry backoff and removes its abort listener',async()=>{
 const f=fixture(response),controller=new AbortController();
 const {getEventListeners}=require('node:events');
 let attempts=0;
 const pending=f.retryMenuRead(async()=>{attempts++;throw new ApiError(0,'CONNECTION_FAILED');},controller.signal);
 await new Promise(resolve=>setImmediate(resolve));
 controller.abort();
 await assert.rejects(pending,{name:'AbortError'});
 assert.equal(attempts,1);
 assert.equal(getEventListeners(controller.signal,'abort').length,0);
});

test('a stalled attempt is cancelled and retried before the overall deadline', async()=>{
 const f=fixture(response), parent=new AbortController();
 const {getEventListeners}=require('node:events');
 const signals=[];
 const value=await f.retryMenuRead(signal=>{
  signals.push(signal);
  if(signals.length===2)return Promise.resolve('ready');
  return new Promise((_,reject)=>signal.addEventListener('abort',()=>reject(signal.reason),{once:true}));
 },parent.signal,25);
 assert.equal(value,'ready');
 assert.equal(signals.length,2);
 assert.equal(signals[0].reason.name,'TimeoutError');
 assert.equal(signals[1].aborted,false);
 assert.equal(getEventListeners(parent.signal,'abort').length,0);
});

test('cancelling a stalled attempt aborts the request without a retry', async()=>{
 const f=fixture(response), parent=new AbortController();
 const {getEventListeners}=require('node:events');
 const signals=[];
 const pending=f.retryMenuRead(signal=>{
  signals.push(signal);
  return new Promise((_,reject)=>signal.addEventListener('abort',()=>reject(signal.reason),{once:true}));
 },parent.signal);
 parent.abort();
 await assert.rejects(pending,{name:'AbortError'});
 assert.equal(signals.length,1);
 assert.equal(signals[0].aborted,true);
 assert.equal(getEventListeners(parent.signal,'abort').length,0);
});

test('a slow successful menu read retains the original eight-second window',async()=>{
 const scheduled = new Map();
 const f=fixture(response,{
  setTimeout(callback,delay){const token=Symbol();scheduled.set(token,{callback,delay});return token;},
  clearTimeout(token){scheduled.delete(token);}
 });
 let resolveRead, attemptSignal;
 const pending=f.retryMenuRead(signal=>{
  attemptSignal=signal;
  return new Promise(resolve=>{resolveRead=resolve;});
 });
 for(const timer of scheduled.values())if(timer.delay<=6500)timer.callback();
 assert.equal(attemptSignal.aborted,false);
 resolveRead('ready');
 assert.equal(await pending,'ready');
 assert.equal(scheduled.size,0);
 assert.ok(f.MENU_READ_RETRY_DEADLINE_MS>=16000+180,'a timeout leaves a full retry window');
});

test('cart pickup recovery retries the same whole cart without using the menu preview',async()=>{
 const f=fixture((body,attempt)=>{
  if(attempt===1)throw new ApiError(503,'UNAVAILABLE');
  return response(body);
 });
 await f.retryMenuRead(signal=>f.checkCartAvailability(1,'2026-10-08',1,items.slice(0,2),signal));
 assert.equal(f.calls.length,2);
 assert.equal(f.calls[0].path,'/api/branches/1/availability');
 assert.equal(f.calls[1].path,f.calls[0].path);
 assert.deepEqual(f.calls[1].body,f.calls[0].body);
 assert.equal(f.calls[1].body.items[0].quantity,7);
});

test('a multi-batch preview that expires before completion is replaced before publication',async()=>{
 let now=0;
 const f=fixture(async(body,n)=>{
  if(n===2){await new Promise(resolve=>setImmediate(resolve));now=6000;}
  const data=response(body);
  return {...data,observedAt:'2026-10-08T10:00:00Z',recheckAt:n===1?'2026-10-08T10:00:02Z':'2026-10-08T10:00:30Z',
   dates:data.dates.map(day=>({...day,items:day.items.map(item=>({...item,available:n<=2}))}))};
 },{setTimeout,clearTimeout,performance:{now:()=>now}});
 const signal=new AbortController().signal;
 const result=await f.readFreshMenuPreview(()=>f.checkMenuAvailability(1,'2026-10-08',1,items.slice(0,101),signal),signal);
 assert.equal(f.calls.length,4);
 assert.equal(result.dates[0].items[0].available,false);
});

test('repeated expired previews stop after one refresh',async()=>{
 const f=fixture(response,{setTimeout,clearTimeout,performance:{now:()=>2000}});
 const signal=new AbortController().signal;
 let attempts=0;
 await assert.rejects(f.readFreshMenuPreview(async()=>{
  attempts++;
  return {observedAt:'2026-10-08T10:00:00Z',recheckAt:'2026-10-08T10:00:02Z',receivedMonotonic:0};
 },signal),/changed while checking/);
 assert.equal(attempts,2);
});
