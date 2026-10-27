const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript');
const api={};vm.runInNewContext(ts.transpileModule(fs.readFileSync('lib/vitals.ts','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText,{exports:api});
const unit=(id,quantity)=>({product:{id,saleMode:'UNIT',price:20},quantity,weightGrams:null});
const weight=(id,grams)=>({product:{id,saleMode:'WEIGHT',price:400},quantity:1,weightGrams:grams});
const cart=(items,branchId=1)=>({branchId,items});
test('cart funnel detects piece and weight additions without depending on total line quantity',()=>{
 for(const [before,after] of [
  [cart([]),cart([unit(1,1)])],
  [cart([unit(1,1)]),cart([unit(1,2)])],
  [cart([weight(1,250)]),cart([weight(1,500)])],
  [cart([weight(1,250),unit(2,1)]),cart([weight(1,500)])],
  [cart([unit(1,1)]),cart([unit(2,1)])],
  [cart([weight(1,250)]),cart([weight(1,250)],2)]
 ])assert.equal(api.cartHasIncreased(before,after),true);
});
test('cart removals, smaller amounts, price refreshes and unchanged carts do not count as adds',()=>{
 const same=cart([weight(1,250)]),repriced=cart([{...weight(1,250),product:{id:1,saleMode:'WEIGHT',price:500}}]);
 for(const [before,after] of [
  [same,same],[same,repriced],[same,cart([])],[same,cart([weight(1,200)])],
  [cart([unit(1,2)]),cart([unit(1,1)])],
  [cart([unit(1,1)]),cart([weight(1,250)])]
 ])assert.equal(api.cartHasIncreased(before,after),false);
});
test('zero, negative, missing and non-finite current amounts are not additions',()=>{
 for(const amount of [0,-1,undefined,null,NaN,Infinity]){
  assert.equal(api.cartHasIncreased(cart([]),cart([weight(1,amount)])),false);
  assert.equal(api.cartHasIncreased(cart([]),cart([unit(1,amount)])),false);
 }
});
