const assert=require('node:assert/strict'),test=require('node:test'),fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript');
function moduleAt(file,require){const exports={};vm.runInNewContext(ts.transpileModule(fs.readFileSync(file,'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText,{exports,require,Date,Intl,Set});return exports;}
const freshness=moduleAt('lib/pickupFreshness.ts',()=>{}),{menuPickupOptions,cartPickupOptions,branchPickupOptions,menuItemsForPickup}=moduleAt('lib/menuPickupOptions.ts',()=>freshness);
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

test('cart choices require the server to approve the whole cart rather than one menu item',()=>{
 const data=value([{slot:slot(1,'09:00:00'),normalAvailable:false,priorityAvailable:false,issues:[{productId:1,available:false}]},{slot:slot(2,'10:00:00'),normalAvailable:true,priorityAvailable:false},{slot:slot(3,'08:00:00'),normalAvailable:true}]);
 assert.deepEqual(Array.from(cartPickupOptions(data,now),o=>o.slot.id),[2]);
});

test('empty-cart branch choices ignore stock issues but honor capacity, closure and time',()=>{const data=value([{slot:slot(1,'09:00:00'),normalAvailable:false,issues:[{productId:1,available:false}]},{slot:slot(2,'09:30:00'),code:'PICKUP_WINDOW'},{slot:slot(3,'10:00:00',{remainingCapacity:0})},{slot:slot(4,'08:00:00'),normalAvailable:true}]);assert.deepEqual(Array.from(branchPickupOptions(data,now),o=>o.slot.id),[1]);assert.equal(cartPickupOptions(data,now).length,0);});

test('a slot-wide capacity restriction preserves only the actual sold-out dish',()=>{
 const items=[{productId:1,available:true},{productId:2,available:true}];
 const soldOut={productId:1,available:false,code:'SOLD_OUT',reason:'No stock'};
 const slot={
  code:'SOLD_OUT',reason:'Dish one: No stock',issues:[soldOut],
  slot:{remainingCapacity:0,priorityEnabled:true,priorityRemainingCapacity:3}
 };
 const normal=menuItemsForPickup(items,slot,'NORMAL');
 assert.equal(normal[0],soldOut);
 assert.equal(normal[1].code,'SLOT_FULL');
 assert.equal(normal[1].available,false);
 const priority=menuItemsForPickup(items,slot,'PRIORITY');
 assert.equal(priority[0],soldOut);
 assert.equal(priority[1],items[1]);
});
