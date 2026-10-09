const assert=require('node:assert/strict'),test=require('node:test'),fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript');
const code=ts.transpileModule(fs.readFileSync('services/menuApi.ts','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText;
const categories=[{id:1,products:[{id:1,name:'Samosa',price:30,available:true,imageUrl:'/photo.webp'}]}];
function fixture(){const calls=[],exports={};vm.runInNewContext(code,{exports,process:{env:{}},Date,performance,DOMException,AbortController,AbortSignal,setTimeout,clearTimeout,require:name=>name.includes('apiClient')?{apiClient:(path,options)=>new Promise((resolve,reject)=>calls.push({path,options,resolve,reject}))}:{getMockMenu:()=>[]}});return {api:exports,calls};}
const flush=()=>new Promise(resolve=>setImmediate(resolve));
test('names prices and photos publish before live stock; catalog cannot enable ordering',async()=>{
 const f=fixture(),shown=[],result=f.api.getMenu(1,undefined,c=>shown.push(c));
 const display=f.calls.find(c=>c.path.includes('catalog?')),live=f.calls.find(c=>c.path.includes('view=availability'));
 display.resolve({revision:'1',categories});await flush();
 assert.equal(shown.length,1);assert.equal(shown[0][0].products[0].price,30);assert.equal(shown[0][0].products[0].imageUrl,'/photo.webp');assert.equal(shown[0][0].products[0].available,false);
 live.resolve(categories);assert.equal((await result)[0].products[0].available,true);assert.equal(display.options.signal.aborted,true);
});
test('late display data never replaces a completed live menu',async()=>{
 const f=fixture(),shown=[],result=f.api.getMenu(1,undefined,c=>shown.push(c));f.calls.find(c=>c.path.includes('availability')).resolve(categories);await result;
 f.calls.find(c=>c.path.includes('catalog?')).resolve({categories});await flush();assert.equal(shown.length,0);
});
test('branch cancellation discards display data even when transport ignores abort',async()=>{
 const f=fixture(),shown=[],controller=new AbortController(),result=f.api.getMenu(1,controller.signal,c=>shown.push(c));controller.abort();
 f.calls.find(c=>c.path.includes('catalog?')).resolve({categories});f.calls.find(c=>c.path.includes('availability')).reject(new DOMException('Cancelled','AbortError'));
 await assert.rejects(result);await flush();assert.equal(shown.length,0);
});
test('display failure does not fail live menu and live failure cannot enable display data',async()=>{
 const f=fixture(),result=f.api.getMenu(1,undefined,()=>{});f.calls.find(c=>c.path.includes('catalog?')).reject(Error('display offline'));f.calls.find(c=>c.path.includes('availability')).resolve(categories);assert.equal((await result).length,1);
 const g=fixture(),shown=[],failed=g.api.getMenu(1,undefined,c=>shown.push(c));g.calls.find(c=>c.path.includes('catalog?')).resolve({categories});await flush();g.calls.find(c=>c.path.includes('availability')).reject(Error('stock offline'));await assert.rejects(failed);assert.equal(shown[0][0].products[0].available,false);
});
test('early catalog reuse removes a duplicate catalog read without borrowing its stock decision',async()=>{
 const f=fixture(),result=f.api.getMenu(1,undefined,()=>{});
 f.calls.find(c=>c.path.includes('catalog?')).resolve({revision:'1',categories});await flush();
 f.calls.find(c=>c.path.includes('availability')).resolve({revision:'1',serviceWindowsEnabled:true,items:[{productId:1,available:false}]});
 const menu=await result;assert.equal(menu[0].products[0].price,30);assert.equal(menu[0].products[0].available,false);assert.equal(f.calls.length,2);
});
