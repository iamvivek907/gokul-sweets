const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript');
const api={};vm.runInNewContext(ts.transpileModule(fs.readFileSync('lib/menuBrowseState.ts','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText,{exports:api,Date,Number,JSON});
const state={savedAt:100,search:'panner',categoryId:3,categories:[3],maximum:100,portions:true,browseId:3,scrollY:800};
test('menu preferences are branch scoped, expire, and never contain cart or pickup values',()=>{
 const map=new Map(),storage={getItem:k=>map.get(k)??null,setItem:(k,v)=>map.set(k,v)};
 api.writeMenuBrowse(storage,1,state);assert.equal(api.readMenuBrowse(storage,1,101).search,'panner');assert.equal(api.readMenuBrowse(storage,2,101),null);assert.equal(api.readMenuBrowse(storage,1,1800101),null);assert.equal(api.readMenuBrowse(storage,1,99),null);
 assert.deepEqual(Object.keys(JSON.parse([...map.values()][0])).sort(),Object.keys(state).sort());
});
test('malformed, unavailable or full storage never prevents menu browsing',()=>{
 for(const change of [{categories:[null]},{maximum:-1},{scrollY:NaN},{search:'x'.repeat(201)},{portions:'yes'},{categoryId:-1}])assert.equal(api.readMenuBrowse({getItem:()=>JSON.stringify({...state,...change})},1,101),null);
 assert.equal(api.readMenuBrowse({getItem:()=>'{broken'},1),null);assert.equal(api.readMenuBrowse(null,1),null);assert.equal(api.browserMenuStorage(),null);assert.doesNotThrow(()=>api.writeMenuBrowse({setItem(){throw Error('quota');}},1,state));
});
test('the app retains its explicit no-pinch-zoom requirement',()=>{
 const layout=fs.readFileSync('app/layout.tsx','utf8');assert.match(layout,/maximumScale:\s*1/);assert.match(layout,/userScalable:\s*false/);
});
