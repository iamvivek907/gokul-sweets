const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript');
function load(file,dependencies={},globals={}){
 const exports={};vm.runInNewContext(ts.transpileModule(fs.readFileSync(file,'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022,jsx:ts.JsxEmit.ReactJSX}}).outputText,{exports,require:name=>{if(name in dependencies)return dependencies[name];throw Error(`Unexpected import ${name}`);},...globals});return exports;
}
const {parseCart}=load('lib/cartStorage.ts',{'@/lib/menuPickupMode':{setMenuPickupMode(){}}});
const vitals=load('lib/vitals.ts');
const weighted=grams=>({branchId:1,items:[{product:{id:123,name:'Private dish',categoryName:'Sweets',saleMode:'WEIGHT',price:400},quantity:1,weightGrams:grams}]});
function setup({enabled=true,sampled=true,storageFails=false}={}){
 let cart=weighted(250),listener=null,reads=0,failure=storageFails;const effects=[],sent=[];
 const {default:Collector}=load('components/common/StorefrontVitals.tsx',{
  react:{useEffect:fn=>effects.push(fn),useRef:current=>({current}),useCallback:fn=>fn},
  'next/navigation':{usePathname:()=>'/menu'},'next/web-vitals':{useReportWebVitals(){}},
  '@/hooks/useStorefrontFeatures':{useStorefrontFeatures:()=>({accessibleOrderingV2:enabled})},
  '@/lib/constants':{API_BASE_URL:'https://example.invalid'},'@/lib/vitals':vitals,
  '@/lib/cartStorage':{parseCart,getCartSnapshot:()=>{reads++;if(failure)throw Error('Storage blocked');return JSON.stringify(cart);},subscribeToCart:fn=>{listener=fn;return()=>{listener=null;};}}
 },{Math:{random:()=>sampled?0:1},location:{pathname:'/menu'},window:{addEventListener(){},removeEventListener(){}},fetch:async(_,options)=>sent.push(JSON.parse(options.body))});
 Collector();assert.doesNotThrow(()=>effects.forEach(fn=>fn()));
 return {sent,reads:()=>reads,subscribed:()=>listener!==null,failStorage:value=>{failure=value;},change:next=>{cart=next;assert.doesNotThrow(()=>listener?.());}};
}
test('sampled weighted additions emit one anonymous funnel counter per document',()=>{
 const s=setup();s.change(weighted(500));s.change(weighted(750));
 const events=s.sent.filter(event=>event.name==='FUNNEL_ADD');assert.equal(events.length,1);
 assert.deepEqual(events[0],{name:'FUNNEL_ADD',value:1,rating:'measured',page:'menu'});
 assert.ok(!JSON.stringify(s.sent).includes('Private dish'));assert.ok(!JSON.stringify(s.sent).includes('123'));
});
test('smaller weights are ignored, then a genuine increase is recorded',()=>{
 const s=setup();s.change(weighted(200));assert.equal(s.sent.some(event=>event.name==='FUNNEL_ADD'),false);
 s.change(weighted(250));assert.equal(s.sent.filter(event=>event.name==='FUNNEL_ADD').length,1);
});
test('disabled or unsampled collectors neither observe cart storage nor send counters',()=>{
 for(const options of [{enabled:false},{sampled:false}]){
  const s=setup(options);s.change(weighted(500));assert.equal(s.reads(),0);assert.equal(s.subscribed(),false);assert.deepEqual(s.sent,[]);
 }
});
test('unavailable storage cannot interrupt the app or produce a guessed addition',()=>{
 const initial=setup({storageFails:true});assert.equal(initial.subscribed(),false);
 const s=setup();s.failStorage(true);s.change(weighted(500));s.failStorage(false);s.change(weighted(500));
 assert.equal(s.sent.some(event=>event.name==='FUNNEL_ADD'),false);
 s.change(weighted(550));assert.equal(s.sent.filter(event=>event.name==='FUNNEL_ADD').length,1);
});
