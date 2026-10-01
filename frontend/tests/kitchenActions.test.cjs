const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),ts=require('typescript');
const exportsObject={};
new Function('exports',ts.transpileModule(fs.readFileSync('lib/kitchenActions.ts','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS}}).outputText)(exportsObject);
const {canStartKitchenOrder,kitchenOrderAction}=exportsObject;
test('overdue preparation must finish, never restart KOT',()=>{
 for(const bucket of ['PREPARING','OVERDUE']){
  const row={orderStatus:'PREPARING',bucket};
  assert.equal(canStartKitchenOrder(row),false);
  assert.equal(kitchenOrderAction(row,true,true),'ready');
  assert.equal(kitchenOrderAction(row,true,false),null);
 }
});
test('only confirmed actionable orders can start, with the right permission',()=>{
 for(const status of ['CONFIRMED','PREPARING','READY_FOR_PICKUP','READY_FOR_DELIVERY','CANCELLED','PENDING_PAYMENT'])
  for(const bucket of ['ELIGIBLE','OVERDUE','SCHEDULED','READY']){
   const row={orderStatus:status,bucket},start=status==='CONFIRMED'&&['ELIGIBLE','OVERDUE'].includes(bucket);
   assert.equal(canStartKitchenOrder(row),start);
   assert.equal(kitchenOrderAction(row,true,false),start?'start':null);
   assert.equal(kitchenOrderAction(row,false,false),null);
  }
});
