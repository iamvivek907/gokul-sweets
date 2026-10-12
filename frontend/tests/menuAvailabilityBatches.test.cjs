const assert=require('node:assert/strict'),test=require('node:test'),fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript');
function fixture(respond){const calls=[],box={exports:{},require:()=>({apiClient:async(path,options)=>{const body=JSON.parse(options.body);calls.push({path,body,signal:options.signal});return respond(body,calls.length);}})};vm.runInNewContext(ts.transpileModule(fs.readFileSync('services/availabilityApi.ts','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText,box);return {...box.exports,calls};}
const items=Array.from({length:215},(_,i)=>({productId:i+1,quantity:i===0?7:1,weightGrams:null}));
const response=body=>({fulfilmentType:'PICKUP',today:'2026-10-07',maximumDate:'2026-10-10',dates:[{date:body.startDate,available:true,items:body.items.map(p=>({productId:p.productId,available:true})),slots:[{slot:{id:1,remainingCapacity:10},normalAvailable:true,priorityAvailable:false,issues:[]}]}]});
test('full catalogue uses bounded sequential batches and preserves actual cart quantities',async()=>{const f=fixture(response);const data=await f.checkMenuAvailability(1,'2026-10-08',1,items);assert.deepEqual(f.calls.map(c=>c.body.items.length),[100,100,15]);assert.equal(f.calls[0].body.items[0].quantity,7);assert.ok(f.calls.every(c=>c.path.endsWith('?menuPreview=true&compact=true')));assert.deepEqual(Array.from(data.dates[0].items,p=>p.productId),items.map(p=>p.productId));});
test('issues from later batches and pickup-window restrictions survive merging',async()=>{const f=fixture((body,n)=>{const data=response(body);if(n===2){const slot=data.dates[0].slots[0];slot.issues=[{productId:113,available:false}];slot.code='PICKUP_WINDOW';slot.normalAvailable=false;slot.reason='Closed';data.dates[0].available=false;}return data;});const data=await f.checkMenuAvailability(1,'2026-10-08',1,items);assert.equal(data.dates[0].slots[0].issues[0].productId,113);assert.equal(data.dates[0].slots[0].code,'PICKUP_WINDOW');assert.equal(data.dates[0].slots[0].normalAvailable,false);assert.equal(data.dates[0].available,false);});
test('failed batch rejects incomplete preview and stops later requests',async()=>{const f=fixture((body,n)=>{if(n===2)throw Error('offline');return response(body)});await assert.rejects(f.checkMenuAvailability(1,'2026-10-08',1,items),/offline/);assert.equal(f.calls.length,2);});
test('context cancellation stops subsequent batches',async()=>{const controller=new AbortController();const f=fixture(body=>{controller.abort();return response(body)});await assert.rejects(f.checkMenuAvailability(1,'2026-10-08',1,items,controller.signal),{name:'AbortError'});assert.equal(f.calls.length,1);});
test('authoritative cart request remains a single whole-cart check',async()=>{const f=fixture(response);await f.checkCartAvailability(1,'2026-10-08',1,items.slice(0,2));assert.equal(f.calls.length,1);assert.equal(f.calls[0].path,'/api/branches/1/availability');});
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
  await assert.rejects(f.checkMenuAvailability(1,'2026-10-08',1,items),/Invalid menu preview issue index/);assert.equal(f.calls.length,1);
 }
});
