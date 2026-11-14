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

test('cutoffs crossed during a refresh share one follow-up only when the result is behind',async()=>{
 const {blockExpiredMenuService,menuServiceDeadline}=moduleFor('lib/datedMenuAvailability.ts');
 const source=ts.transpileModule(fs.readFileSync('hooks/useMenuServiceRefresh.ts','utf8'),{
  compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}
 }).outputText;
 for(const outcome of ['failure','stale','fresh']){
  const timers=new Map(),reads=[],published=[];
  let now=0,notices=0,dispose,active=0,peak=0;
  const product=(id,cutoff)=>({id,available:true,serviceAvailability:{
   available:true,code:'AVAILABLE',nextChangeAt:new Date(cutoff).toISOString(),
   evaluatedAt:new Date(0).toISOString(),requestStartedMonotonic:0,receivedMonotonic:0
  }});
  let categories=[{products:[product(1,1000),product(2,2000),product(3,2500)]}];
  const surface={addEventListener(){},removeEventListener(){}};
  const box={
   exports:{},AbortController,AbortSignal,performance:{now:()=>now},
   navigator:{onLine:true},window:surface,document:{...surface,visibilityState:'visible'},
   setTimeout(fn,delay){const id=Symbol();timers.set(id,{at:now+delay,fn});return id;},
   clearTimeout:id=>timers.delete(id),setInterval:()=>0,clearInterval(){},
   require(path){
    if(path==='react')return {useLayoutEffect:fn=>{dispose=fn();},useRef:()=>({current:null})};
    if(path.endsWith('/datedMenuAvailability'))return {blockExpiredMenuService,menuServiceDeadline};
    if(path.endsWith('/availabilityApi'))return {MENU_READ_RETRY_DEADLINE_MS:17000,retryMenuRead:fn=>fn()};
    return {refreshMenuAvailability:()=>new Promise((resolve,reject)=>{
     active++;peak=Math.max(peak,active);
     reads.push({resolve:value=>{active--;resolve(value);},reject:()=>{active--;reject(Error('outage'));}});
    })};
   }
  };
  vm.runInNewContext(source,box);
  box.exports.useMenuServiceRefresh(1,categories,update=>{categories=update(categories);},value=>published.push(value),()=>notices++);
  const advance=time=>{now=time;for(const [id,timer] of [...timers])if(timer.at<=time){timers.delete(id);timer.fn();}};
  advance(1000);advance(2000);advance(2500);
  assert.equal(reads.length,1);
  assert.ok(categories[0].products.every(product=>!product.available));
  const result=observed=>[{products:[{...product(1,6000),serviceAvailability:{
   ...product(1,6000).serviceAvailability,evaluatedAt:new Date(observed).toISOString(),requestStartedMonotonic:now
  }}]}];
  if(outcome==='failure')reads[0].reject();
  else reads[0].resolve(result(outcome==='fresh'?2500:1200));
  await new Promise(resolve=>setImmediate(resolve));
  assert.equal(notices,0);
  assert.equal(reads.length,outcome==='fresh'?1:2);
  assert.equal(published.length,outcome==='fresh'?1:0);
  if(outcome!=='fresh'){
   reads[1].reject();
   await new Promise(resolve=>setImmediate(resolve));
   assert.equal(reads.length,2);
   assert.equal(notices,1);
  }
  assert.equal(peak,1);
  dispose();
  assert.equal(timers.size,0);
 }
});
