const assert=require('node:assert/strict'),test=require('node:test'),fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript');
function moduleAt(file,require){const exports={};vm.runInNewContext(ts.transpileModule(fs.readFileSync(file,'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText,{exports,require,Date,Intl,Set});return exports;}
const freshness=moduleAt('lib/pickupFreshness.ts',()=>{}),options=moduleAt('lib/menuPickupOptions.ts',()=>freshness);
const {earliestNormalMenuPickup,partitionPickupProducts,noDefaultPickupMessage}=moduleAt('lib/menuPickupPresentation.ts',()=>options);
const today='2026-10-08',now=new Date(`${today}T00:01:00+05:30`);
const slot=(id,time,extra={})=>({id,branchId:1,slotDate:today,startTime:time,endTime:'21:00:00',active:true,remainingCapacity:10,priorityEnabled:false,...extra});
const data=slots=>({dates:[{date:today,slots}]});
test('default uses earliest eligible normal pickup today, with mixed breakfast and lunch service',()=>{
 const value=data([{slot:slot(2,'11:00:00'),issues:[{productId:2,available:false}]},{slot:slot(1,'08:00:00'),issues:[{productId:1,available:false}]},{slot:slot(3,'07:00:00'),code:'PICKUP_WINDOW'}]);
 assert.equal(earliestNormalMenuPickup(value,[1,2],today,now).slot.id,1);
 assert.equal(earliestNormalMenuPickup(value,[1],today,now).slot.id,2);
 assert.equal(earliestNormalMenuPickup(value,[1,2],today,new Date(`${today}T21:30:00+05:30`)),null);
});
test('defaults do not choose tomorrow, a priority fee or a slot with no eligible item',()=>{
 const value=data([{slot:slot(1,'08:00:00',{remainingCapacity:0,priorityEnabled:true,priorityRemainingCapacity:2}),issues:[]}]);
 assert.equal(earliestNormalMenuPickup(value,[1],today,now),null);
 assert.equal(earliestNormalMenuPickup(value,[1], '2026-10-09',now),null);
 const blocked=data([{slot:slot(2,'11:00:00'),issues:[{productId:1,available:false}]}]);
 assert.equal(earliestNormalMenuPickup(blocked,[1],today,now),null);
 assert.match(noDefaultPickupMessage(value,today),/fully booked/);
 assert.match(noDefaultPickupMessage(data([{slot:slot(3,'21:00:00'),code:'PICKUP_WINDOW'}]),today),/closed/);
});
test('available items lead, manual unavailable stays blocked, and mixed-size groups are not duplicated',()=>{
 const products=[{id:1,available:true},{id:2,available:true},{id:3,available:true},{id:4,available:false}];
 const groups=[{key:'portion',choices:[{productId:2},{productId:3},{productId:4}]}];
 const result=partitionPickupProducts(products,[{productId:1,available:false},{productId:2,available:true},{productId:3,available:false},{productId:4,available:true}],groups);
 assert.deepEqual(Array.from(result.available,p=>p.id),[2,3,4]);
 assert.deepEqual(Array.from(result.other,p=>p.id),[1]);
 assert.equal(result.available.find(p=>p.id===4).available,false,'the grouped unavailable size remains disabled');
});
test('search and price filters classify a group against the same full catalogue rendered in its picker',()=>{
 const catalog=[{id:1,name:'Half Paratha',price:50,available:true},{id:2,name:'Full Paratha',price:90,available:true}];
 const groups=[{key:'paratha',choices:[{productId:1},{productId:2}]}];
 const items=[{productId:1,available:false},{productId:2,available:true}];
 for(const filtered of [catalog.filter(p=>p.name.includes('Half')),catalog.filter(p=>p.price<=50)]){
  const result=partitionPickupProducts(filtered,items,groups,catalog);
  assert.deepEqual(Array.from(result.available,p=>p.id),[1]);
  assert.equal(result.other.length,0);
 }
 // Hidden, removed or manually unavailable siblings cannot make a group eligible.
 for(const live of [[catalog[0]],[catalog[0],{...catalog[1],available:false}]]){
  const result=partitionPickupProducts([catalog[0]],items,groups,live);
  assert.equal(result.available.length,0);
  assert.deepEqual(Array.from(result.other,p=>p.id),[1]);
 }
});
