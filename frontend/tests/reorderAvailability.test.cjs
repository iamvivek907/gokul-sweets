const assert=require('node:assert/strict'),test=require('node:test'),fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript');
function load(stock,slots){
 const exports={},calls=[];
 const require=path=>path==='./availabilityApi'?{
  availabilityItems:items=>items.map(item=>({productId:item.product.id,quantity:item.quantity,weightGrams:item.weightGrams})),
  checkCartAvailability:async()=>{throw new Error('Legacy reorder must not use smart availability');}
 }:path==='./inventoryApi'?{checkInventory:async(...args)=>{calls.push(args);return stock;}}
 :{getPickupSlots:async(...args)=>{calls.push(args);return slots;}};
 vm.runInNewContext(ts.transpileModule(fs.readFileSync('services/reorderAvailability.ts','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText,{exports,require});
 return {check:exports.checkReorderAvailability,calls};
}
const items=[{product:{id:1,available:true},quantity:2,weightGrams:null}];
const slot={active:true,remainingCapacity:3,priorityEnabled:true,priorityRemainingCapacity:1};
test('disabled inventory enforcement accepts the server whole-cart decision without per-item rows',async()=>{
 const {check,calls}=load({enforcementEnabled:false,orderable:true,items:[]},[slot]);
 const signal=new AbortController().signal,value=await check(1,'2026-10-07',1,items,false,signal);
 assert.equal(value.dates[0].available,true);assert.equal(calls.length,2);assert.ok(calls.every(call=>call.at(-1)===signal));
});
test('enforcement OFF still blocks unavailable menu items, full slots and a server refusal',async()=>{
 for(const [stock,slots,lines] of [
  [{enforcementEnabled:false,orderable:true,items:[]},[slot],[{...items[0],product:{id:1,available:false}}]],
  [{enforcementEnabled:false,orderable:true,items:[]},[{...slot,remainingCapacity:0,priorityRemainingCapacity:0}],items],
  [{enforcementEnabled:false,orderable:false,items:[]},[slot],items]
 ]){const {check}=load(stock,slots);assert.equal((await check(1,'2026-10-07',1,lines,false,new AbortController().signal)).dates[0].available,false);}
});
test('enabled inventory enforcement requires confirmation for every item',async()=>{
 const {check}=load({enforcementEnabled:true,orderable:true,items:[]},[slot]);
 assert.equal((await check(1,'2026-10-07',1,items,false,new AbortController().signal)).dates[0].available,false);
});
