const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript');
function moduleFor(path){const box={exports:{}};vm.runInNewContext(ts.transpileModule(fs.readFileSync(path,'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText,box);return box.exports;}
test('stock invalidations coalesce until all active reads finish',()=>{let calls=0;const gate=moduleFor('lib/menuPreviewRefresh.ts').createMenuPreviewRefresh(()=>calls++),preview=gate.acquire(),finder=gate.acquire();for(let i=0;i<10;i++)gate.request();assert.equal(calls,0);preview();assert.equal(calls,0);finder();assert.equal(calls,1);finder();assert.equal(calls,1);gate.request();assert.equal(calls,2)});
test('context cleanup drops old queued invalidations and late completion is inert',()=>{let calls=0;const gate=moduleFor('lib/menuPreviewRefresh.ts').createMenuPreviewRefresh(()=>calls++),old=gate.acquire();gate.request();old(false);const current=gate.acquire();old();assert.equal(calls,0);current();assert.equal(calls,0);gate.request();assert.equal(calls,1)});
test('later pickup reopens temporal closures but preserves manual and missing-dependency closures',()=>{const available=moduleFor('lib/datedMenuAvailability.ts').datedMenuAvailable;for(const code of ['OUTSIDE_SERVICE','SERVICE_WINDOW'])assert.equal(available({available:false,catalogAvailable:true,serviceAvailability:{code}}),true);for(const code of ['SOLD_OUT','DEPENDENCY_UNAVAILABLE','CHECKING'])assert.equal(available({available:false,catalogAvailable:true,serviceAvailability:{code}}),false);assert.equal(available({available:false,catalogAvailable:true,serviceAvailability:{code:'DEPENDENCY_UNAVAILABLE',nextChangeAt:'2026-10-22T12:00:00Z'}}),true);assert.equal(available({available:false,catalogAvailable:false,serviceAvailability:{code:'OUTSIDE_SERVICE'}}),false);assert.equal(available({available:false,serviceAvailability:{code:'OUTSIDE_SERVICE'}}),false);assert.equal(available({available:true}),true)});

test('expired service decisions are blocked independently and keep their recovery metadata',()=>{
 const {blockExpiredMenuService}=moduleFor('lib/datedMenuAvailability.ts');
 const product=(id,nextChangeAt)=>({id,available:true,serviceAvailability:{
  available:true,code:'AVAILABLE',evaluatedAt:'2026-10-22T10:00:00Z',nextChangeAt,
  requestStartedMonotonic:100,receivedMonotonic:600
 }});
 const first=product(1,'2026-10-22T10:00:02Z'),second=product(2,'2026-10-22T10:00:05Z');
 const menu=[{products:[first,second]}];
 assert.equal(blockExpiredMenuService(menu,2099),menu);
 const partial=blockExpiredMenuService(menu,2100);
 assert.equal(partial[0].products[0].serviceAvailability.code,'CHECKING');
 assert.equal(partial[0].products[0].serviceAvailability.nextChangeAt,first.serviceAvailability.nextChangeAt);
 assert.equal(partial[0].products[1],second);
 assert.equal(blockExpiredMenuService(partial,5099),partial);
 const blocked=blockExpiredMenuService(partial,5100);
 assert.equal(blocked[0].products[1].available,false);
 assert.equal(blockExpiredMenuService(blocked,5500),blocked);
});
