const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const test=require('node:test');
const ts=require('typescript');
const code=ts.transpileModule(fs.readFileSync(require('node:path').join(__dirname,'../services/menuApi.ts'),'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText;
function setup(){
 const calls=[];let revision=1,available=true,enabled=true;
 const categories=[{id:1,name:'Sweets',products:[{id:1,name:'Sweet',price:400,available:true,saleMode:'WEIGHT',minimumWeightGrams:250,weightStepGrams:250}]}];
 const exports={};
 vm.runInNewContext(code,{exports,process:{env:{}},performance,Date,DOMException,AbortController,AbortSignal,setTimeout,clearTimeout,require:name=>name.includes('apiClient')?{apiClient:async(path,options)=>{
  calls.push({path,options});
  if(path.includes('view=availability'))return {revision,serviceWindowsEnabled:enabled,items:[{productId:1,available,serviceAvailability:{available,code:available?'AVAILABLE':'SOLD_OUT'}}]};
  return {revision,categories:categories.map(c=>({...c,products:c.products.map(p=>({...p,price:revision*400}))}))};
 }}:name.includes('mobileConnection')?{constrainedPhoneConnection:()=>true}:{getMockMenu:()=>[]}});
 return {api:exports,calls,change:value=>{revision=value;},sold:()=>{available=false;},disable:()=>{enabled=false;}};
}
test('repeat visits refresh live stock but reuse the versioned catalog',async()=>{
 const f=setup();const first=await f.api.getMenu(1);assert.equal(first[0].products[0].price,400);
 assert.equal(f.calls.length,2);assert.match(f.calls[1].path,/catalog\/1\/1$/);assert.equal(f.calls[1].options.cacheMode,'default');
 f.sold();const next=await f.api.getMenu(1);assert.equal(next[0].products[0].available,false);assert.equal(f.calls.length,3);
 f.change(2);const changed=await f.api.refreshMenuAvailability(1);assert.equal(changed[0].products[0].price,800);assert.equal(f.calls.length,5);
 assert.equal(first[0].products[0].price,400,'older immutable display data is unchanged');
});
test('flag-off menus retain legacy exclusion of unavailable products',async()=>{
 const f=setup();await f.api.getMenu(1);f.sold();f.disable();assert.equal((await f.api.refreshMenuAvailability(1)).length,0);
});
test('branch catalogs stay isolated and bounded across navigation',async()=>{
 const f=setup();for(let branch=1;branch<=4;branch++)await f.api.getMenu(branch);
 const before=f.calls.length;await f.api.getMenu(1);assert.equal(f.calls.length,before+2,'evicted branch must reload its catalog');
 const count=f.calls.length;await f.api.getMenu(4);assert.equal(f.calls.length,count+1,'retained branch only checks availability');
});
