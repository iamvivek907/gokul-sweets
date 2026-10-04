const assert=require('node:assert/strict'),test=require('node:test'),fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript');
function moduleAt(file,require){const exports={};vm.runInNewContext(ts.transpileModule(fs.readFileSync(file,'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText,{exports,require,Date,Intl,Set});return exports;}
const freshness=moduleAt('lib/pickupFreshness.ts',()=>{}),{menuPickupOptions}=moduleAt('lib/menuPickupOptions.ts',()=>freshness);
const now=new Date('2026-10-04T03:00:00Z'); // 08:30 IST
const slot=(id,time,extra={})=>({id,branchId:1,slotDate:'2026-10-04',startTime:time,endTime:'11:00:00',active:true,remainingCapacity:10,priorityEnabled:false,...extra});
const value=slots=>({dates:[{date:'2026-10-04',slots}]});
test('mobile menu time choices discard past IST slots and retain a time where some menu items fit',()=>{
 const data=value([{slot:slot(1,'08:00:00'),normalAvailable:true},{slot:slot(2,'09:00:00'),normalAvailable:false,issues:[{productId:1,available:false}]},{slot:slot(3,'10:00:00'),code:'PICKUP_WINDOW',normalAvailable:false}]);
 assert.deepEqual(Array.from(menuPickupOptions(data,[1,2],now),o=>o.slot.id),[2]);
 assert.equal(menuPickupOptions(data,[1],now).length,0);
});
test('fully booked and inactive slots cannot be selected, while explicit priority capacity remains available',()=>{
 const data=value([{slot:slot(1,'09:00:00',{remainingCapacity:0,priorityEnabled:true,priorityRemainingCapacity:1}),priorityAvailable:true},{slot:slot(2,'09:30:00',{remainingCapacity:0,priorityEnabled:true,priorityRemainingCapacity:0})},{slot:slot(3,'10:00:00',{active:false}),normalAvailable:true}]);
 const options=menuPickupOptions(data,[1],now);assert.equal(options.length,1);assert.equal(options[0].pickupType,'PRIORITY');
});
