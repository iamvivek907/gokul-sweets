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
 const checkout=load('lib/checkoutStorage.ts',()=>mode,{localStorage,window,Event});
 return {map,mode,cart,checkout,localStorage,window};}
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
test('retained foreign pickup does not block a new branch, while local preferences and malformed pickups stay fixed',()=>{
 const {mode}=storage();const pickup=JSON.stringify({date:today,slot:{branchId:1,id:8}});
 for(const automatic of [true,false]){
  mode.setMenuPickupMode(1,automatic,pickup);const raw=mode.getMenuPickupModeSnapshot();
  assert.equal(mode.isSoonestPickup(raw,2,pickup,false),true);
  assert.equal(mode.isSoonestPickup(raw,2,pickup,true),false,'current-branch date preference stays fixed');
  assert.equal(mode.isSoonestPickup(raw,1,pickup,true),automatic,'same-branch mode is preserved');
  assert.equal(mode.isSoonestPickup(raw,2,'broken',false),false);
 }
 assert.equal(mode.isSoonestPickup('',2,pickup,false),true,'legacy foreign branch pickup is scoped too');
 assert.equal(mode.isSoonestPickup('',1,pickup,false),false);
});

test('completed paid pickup releases automatic mode for the next order, without erasing a later choice',()=>{
 const {mode,map}=storage();const pickup=JSON.stringify({date:today,slot:{branchId:1,id:8}});
 map.set('gokul-selected-pickup-slot',pickup);map.set('gokul-pickup-intent',JSON.stringify({branchId:1,date:today}));mode.setMenuPickupMode(1,false,pickup);
 assert.equal(mode.releaseCompletedMenuPickup(1,8,pickup),true);map.delete('gokul-selected-pickup-slot');
 assert.equal(mode.isSoonestPickup(mode.getMenuPickupModeSnapshot(),1,'',false),true);
 map.set('gokul-selected-pickup-slot',pickup);map.set('gokul-pickup-intent',JSON.stringify({branchId:1,date:tomorrow}));mode.setMenuPickupMode(1,false,pickup);
 assert.equal(mode.releaseCompletedMenuPickup(1,8,pickup),false);assert.equal(JSON.parse(map.get('gokul-pickup-intent')).date,tomorrow);
 assert.equal(mode.releaseCompletedMenuPickup(1,9,pickup),false);
});

for(const changed of [false,true])test(`paid cleanup ${changed?'preserves a newer cart and its fixed choice':'starts a fresh soonest ordering session'}`,()=>{
 const {mode,map,cart,checkout,localStorage,window}=storage();
 const items=[{product:{id:1,saleMode:'UNIT',categoryName:'Breakfast'},quantity:1}];
 const pickup=JSON.stringify({date:today,slot:{branchId:1,id:8}});
 map.set('gokul-selected-pickup-slot',pickup);map.set('gokul-pickup-intent',JSON.stringify({branchId:1,date:today}));
 cart.saveCart({branchId:1,items:changed?[{...items[0],quantity:2}]:items});
 const fingerprint=items=>JSON.stringify(items.map(item=>({id:item.product.id,quantity:item.quantity})));
 const pending={orderNumber:'paid-1',branchId:1,pickupSlotId:8,cartFingerprint:fingerprint(items)};
 const recovery=load('lib/paidCartRecovery.ts',path=>{
  if(path.includes('menuPickupMode'))return mode;
  if(path.includes('checkoutStorage'))return checkout;
  if(path.includes('cartFingerprint'))return {createCartFingerprint:fingerprint};
  if(path.includes('cartStorage'))return cart;
  if(path.includes('pendingOrder'))return {getPendingOrderSnapshot:()=>JSON.stringify(pending),parsePendingOrder:JSON.parse,clearPendingOrder:()=>{}};
  return {getPendingPaymentSnapshot:()=>'',parsePendingPayment:()=>null,clearPendingPayment:()=>{}};
 },{localStorage,window,Event});
 assert.equal(recovery.reconcilePaidCart('paid-1'),!changed);
 if(changed){assert.equal(cart.parseCart(cart.getCartSnapshot()).items[0].quantity,2);assert.equal(mode.isSoonestPickup(mode.getMenuPickupModeSnapshot(),1,pickup,true),false);}
 else {assert.equal(cart.parseCart(cart.getCartSnapshot()).items.length,0);assert.equal(mode.isSoonestPickup(mode.getMenuPickupModeSnapshot(),1,'',false),true);}
});

test('item picker rejects an expired or replaced pickup, including a different date with the same time',()=>{
 const {checkout}=storage();const picker=load('lib/menuPickerPickup.ts',path=>path.includes('Freshness')?freshness:checkout);
 const raw=JSON.stringify({date:today,slot:slot(today,8).slot,pickupType:'NORMAL'});
 assert.equal(picker.pickerPickupMatches(raw,raw,now()),true);
 assert.equal(picker.pickerPickupMatches(raw,raw,new Date(`${today}T08:00:01+05:30`)),false);
 const replacement=JSON.stringify({date:tomorrow,slot:slot(tomorrow,9).slot,pickupType:'NORMAL'});
 assert.equal(picker.pickerPickupMatches(raw,replacement,now()),false);
 assert.equal(picker.pickerPickupMatches('',replacement,now()),false);
});
for(const scenario of ['defer','paid','later-pickup','other-cart','other-checkout','not-paid','wrong-order'])test(`payment-only recovery: ${scenario}`,()=>{
 const {mode,map,cart,checkout,localStorage,window}=storage();
 const items=[{product:{id:1,saleMode:'UNIT',categoryName:'Breakfast'},quantity:1}];
 const fingerprint=items=>JSON.stringify(items.map(item=>({id:item.product.id,quantity:item.quantity})));
 const old=JSON.stringify({date:today,slot:{branchId:1,id:8}}),later=JSON.stringify({date:tomorrow,slot:{branchId:1,id:9}});
 const pickup=scenario==='later-pickup'?later:old;
 map.set('gokul-selected-pickup-slot',pickup);map.set('gokul-pickup-intent',JSON.stringify({branchId:1,date:scenario==='later-pickup'?tomorrow:today}));
 cart.saveCart({branchId:1,items:scenario==='other-cart'?[{...items[0],quantity:2}]:items});
 const payment={orderNumber:'paid-1',cartFingerprint:fingerprint(items)};map.set('payment',JSON.stringify(payment));
 const recovery=load('lib/paidCartRecovery.ts',path=>{
  if(path.includes('menuPickupMode'))return mode;if(path.includes('checkoutStorage'))return checkout;
  if(path.includes('cartFingerprint'))return {createCartFingerprint:fingerprint};if(path.includes('cartStorage'))return cart;
  if(path.includes('pendingOrder'))return {getPendingOrderSnapshot:()=>scenario==='other-checkout'?JSON.stringify({orderNumber:'new-checkout',branchId:1,pickupSlotId:9}):'',parsePendingOrder:value=>value?JSON.parse(value):null,clearPendingOrder:()=>{}};
  return {getPendingPaymentSnapshot:()=>map.get('payment')??'',parsePendingPayment:value=>value?JSON.parse(value):null,clearPendingPayment:()=>map.delete('payment')};
 },{localStorage,window,Event});
 const owner=scenario==='defer'?undefined:{orderNumber:scenario==='wrong-order'?'other':'paid-1',paymentStatus:scenario==='not-paid'?'PENDING':'PAID',branchId:1,pickupSlotId:8};
 const result=recovery.reconcilePaidCart('paid-1',owner);
 if(scenario==='paid'){assert.equal(result,true);assert.equal(cart.parseCart(cart.getCartSnapshot()).items.length,0);assert.equal(map.get('gokul-selected-pickup-slot'),undefined);assert.equal(mode.isSoonestPickup(mode.getMenuPickupModeSnapshot(),1,'',false),true);}
 else if(scenario==='later-pickup'){assert.equal(result,true);assert.equal(map.get('gokul-selected-pickup-slot'),later);assert.equal(JSON.parse(map.get('gokul-pickup-intent')).date,tomorrow);}
 else {assert.equal(result,false);assert.equal(cart.parseCart(cart.getCartSnapshot()).items.length,1);assert.equal(map.get('gokul-selected-pickup-slot'),pickup);if(!['other-cart','other-checkout'].includes(scenario))assert.ok(map.has('payment'),'defer cleanup rather than discard the recovery fingerprint');}
});
