const assert=require('node:assert/strict'),test=require('node:test'),fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript');
const exportsObject={};vm.runInNewContext(ts.transpileModule(fs.readFileSync('lib/reorderPickup.ts','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText,{exports:exportsObject,Date});
const {reorderPickupOptions,validReorderItems}=exportsObject;
const now=Date.parse('2026-10-05T12:00:00+05:30');
function slot(id,start,normal=true,priority=false){return {slot:{id,active:true,startTime:start,priorityEnabled:priority},normalAvailable:normal,priorityAvailable:priority};}
test('reorder finds earliest whole-basket pickup, ignores past/inactive/full slots and prefers normal capacity',()=>{
 const dates=[{date:'2026-10-06',slots:[slot(3,'11:00:00')]},{date:'2026-10-05',slots:[slot(1,'10:00:00'),slot(9,'12:30:00',false),{...slot(8,'12:30:00'),slot:{...slot(8,'12:30:00').slot,active:false}},slot(2,'13:00:00',true,true),slot(4,'14:00:00',false,true)]}];
 const options=reorderPickupOptions({dates},now);assert.deepEqual(Array.from(options,o=>[o.slot.id,o.pickupType]),[[2,'NORMAL'],[4,'PRIORITY'],[3,'NORMAL']]);
});
test('reorder rejects empty, fractional, nonfinite and incompatible pack quantities',()=>{
 const unit=n=>({product:{saleMode:'UNIT'},quantity:n,weightGrams:null});
 const weight=n=>({product:{saleMode:'WEIGHT',minimumWeightGrams:250,weightStepGrams:50},quantity:1,weightGrams:n});
 assert.equal(validReorderItems([]),false);for(const n of [0,-1,NaN,Infinity,1.5])assert.equal(validReorderItems([unit(n)]),false);
 assert.equal(validReorderItems([unit(2),weight(1000)]),true);for(const n of [200,275,NaN,Infinity])assert.equal(validReorderItems([weight(n)]),false);
});

test('single-day pickup loss preserves alternate dates and replaces stale slots',()=>{
 const first={date:'2026-10-06',slots:[slot(1,'09:00:00')]},later={date:'2026-10-07',slots:[slot(2,'09:00:00')]};
 const previous={today:'2026-10-05',maximumDate:'2026-10-07',dates:[first,later]};
 const result=exportsObject.mergeReorderAvailability(previous,{dates:[{...first,slots:[]}]});
 assert.deepEqual(Array.from(result.dates,d=>d.date),['2026-10-06','2026-10-07']);
 assert.equal(result.dates[0].slots.length,0);assert.equal(result.dates[1],later);assert.equal(previous.dates[0],first);
 assert.equal(result.maximumDate,previous.maximumDate);
});
