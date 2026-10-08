const assert=require('node:assert/strict'),test=require('node:test'),fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript');
function load(file,require,extra={}){const exports={};vm.runInNewContext(ts.transpileModule(fs.readFileSync(file,'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText,{exports,require,Date,Intl,Set,...extra});return exports;}
const freshness=load('lib/pickupFreshness.ts',()=>{}),options=load('lib/menuPickupOptions.ts',()=>freshness);
const {findSoonestMenuPickup}=load('lib/soonestMenuPickup.ts',path=>path.includes('Freshness')?freshness:options);
const today='2026-10-08',tomorrow='2026-10-09',now=()=>new Date(`${today}T00:01:00+05:30`);
const slot=(date,id,time='08:00:00',extra={})=>({slot:{id,branchId:1,slotDate:date,startTime:time,endTime:'21:00:00',active:true,remainingCapacity:10,priorityEnabled:false,...extra},issues:[]});
const data=(dates)=>({today,maximumDate:'2026-10-15',dates});
const day=(date,slots)=>({date,slots});
const signal=()=>new AbortController().signal;
test('soonest verifies service/stock rather than trusting discovery, and crosses into tomorrow',async()=>{
 const calls=[];const discovery=data([day(tomorrow,[slot(tomorrow,3)]),day(today,[slot(today,2,'11:00:00'),slot(today,1)])]);
 const first=await findSoonestMenuPickup(discovery,[1],today,7,async date=>{calls.push(date);return data([day(date,date===today?[{...slot(date,1),issues:[{productId:1,available:false}]},{...slot(date,2),issues:[{productId:1,available:false}]}]:[slot(date,3)])]);},signal(),now);
 assert.deepEqual(calls,[today,tomorrow]);assert.equal(first.date,tomorrow);assert.equal(first.slot.id,3);assert.equal(first.pickupType,'NORMAL');
});
test('earliest eligible time wins and later dates are never queried unnecessarily',async()=>{
 let calls=0;const value=data([day(today,[slot(today,2,'11:00:00'),slot(today,1)]),day(tomorrow,[slot(tomorrow,3)])]);
 const first=await findSoonestMenuPickup(value,[1,2],today,7,async()=>{calls++;return value;},signal(),now);
 assert.equal(first.slot.id,1);assert.equal(calls,1);
});
test('priority, elapsed slots, dates beyond window and server maximum are excluded',async()=>{
 let calls=0;const value=data([day(today,[slot(today,1,'08:00:00',{remainingCapacity:0,priorityEnabled:true,priorityRemainingCapacity:10})]),day('2026-10-20',[slot('2026-10-20',2)])]);
 assert.equal(await findSoonestMenuPickup(value,[1],today,7,async()=>{calls++;return value;},signal(),now),null);assert.equal(calls,0);
 const late=()=>new Date(`${today}T21:30:00+05:30`);
 assert.equal(await findSoonestMenuPickup(data([day(today,[slot(today,1)])]),[1],today,7,async()=>{throw Error('not expected');},signal(),late),null);
});
test('an outage cannot be mistaken for an empty day or skipped to a later date',async()=>{
 const value=data([day(today,[slot(today,1)]),day(tomorrow,[slot(tomorrow,2)])]);let calls=0;
 await assert.rejects(findSoonestMenuPickup(value,[1],today,7,async()=>{calls++;throw Error('offline');},signal(),now),/offline/);assert.equal(calls,1);
});
test('abort during verification never returns a candidate',async()=>{
 const c=new AbortController();const value=data([day(today,[slot(today,1)])]);
 await assert.rejects(findSoonestMenuPickup(value,[1],today,7,async()=>{c.abort();return value;},c.signal,now),{name:'AbortError'});
});
function storage(){const map=new Map(),events=[];const localStorage={getItem:key=>map.get(key)??null,setItem:(key,value)=>map.set(key,value),removeItem:key=>map.delete(key)};
 const window={localStorage,dispatchEvent:event=>events.push(event.type)};
 const mode=load('lib/menuPickupMode.ts',()=>{},{localStorage,window,Event});
 const cart=load('lib/cartStorage.ts',()=>mode,{localStorage,window,Event});
 return {map,mode,cart};}
test('legacy/date-only/manual/malformed preferences stay fixed; a new empty menu can automate',()=>{
 const {mode}=storage();assert.equal(mode.isSoonestPickup('',1,'',false),true);
 for(const [raw,pickup,preference] of [['','saved',true],['','',true],['broken','',false],[JSON.stringify({branchId:1,mode:'fixed',pickup:''}),'',false]]) assert.equal(mode.isSoonestPickup(raw,1,pickup,preference),false);
});
test('automatic mode survives refresh but cannot attach to a different saved pickup',()=>{
 const {mode}=storage();mode.setMenuPickupMode(1,true,'slot-a');const persisted=mode.getMenuPickupModeSnapshot();
 assert.equal(mode.isSoonestPickup(persisted,1,'slot-a',true),true);assert.equal(mode.isSoonestPickup(persisted,1,'slot-b',true),false);
});
test('first addition fixes pickup; reductions and clearing cart never reactivate automation',()=>{
 const {mode,cart}=storage();mode.setMenuPickupMode(1,true,'slot-a');
 cart.saveCart({branchId:1,items:[{product:{id:1},quantity:1}]});
 assert.equal(mode.isSoonestPickup(mode.getMenuPickupModeSnapshot(),1,'slot-a',true),false);
 cart.saveCart({branchId:1,items:[]});cart.clearStoredCart();
 assert.equal(mode.isSoonestPickup(mode.getMenuPickupModeSnapshot(),1,'slot-a',true),false);
});
test('an empty new branch is not blocked by another branch’s mode',()=>{
 const {mode}=storage();mode.setMenuPickupMode(1,false,'old');assert.equal(mode.isSoonestPickup(mode.getMenuPickupModeSnapshot(),2,'',false),true);
});
