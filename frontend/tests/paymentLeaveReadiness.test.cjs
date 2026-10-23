const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript');
function harness(active=true){
 let url=new URL('https://shop.example/checkout/payment/UNPAID'),guard=null,destination=null;
 const layout=[],passive=[],cleanups=[];
 const location={get origin(){return url.origin;},get pathname(){return url.pathname;},get href(){return url.href;},get search(){return url.search;},get hash(){return url.hash;}};
 const history={state:{preserve:true},pushState(state,_title,path){assert.equal(state,this.state);url=new URL(path,url);}};
 const react={useRef:value=>({current:value}),useState:value=>[value,next=>{destination=next;}],useEffect:fn=>passive.push(fn),useLayoutEffect:fn=>layout.push(fn)};
 const exports={};vm.runInNewContext(ts.transpileModule(fs.readFileSync('components/checkout/PaymentLeaveChoice.tsx','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,jsx:ts.JsxEmit.ReactJSX,target:ts.ScriptTarget.ES2022}}).outputText,{
  exports,URL,location,history,queueMicrotask:fn=>fn(),window:{},document:{addEventListener(){},removeEventListener(){}},
  require:name=>name==='react'?react:name==='next/navigation'?{useRouter:()=>({}),usePathname:()=>'/checkout/payment/UNPAID'}:name==='@/lib/language'?{T:()=>null,useTranslation:()=>text=>text}:name==='@/lib/paymentNavigation'?{guardPaymentBack:handler=>{guard=handler;return()=>{if(guard===handler)guard=null;};}}:require(name)
 });
 exports.default({active,busy:false,autoCancel:true,onCancel:async()=>{}});
 return {paint(){for(const fn of layout){const cleanup=fn();if(cleanup)cleanups.push(cleanup);}},back(){url=new URL('/orders/UNPAID',url);let held=false;guard?.({stopImmediatePropagation(){held=true;}});return {held,path:url.pathname,destination};},cleanup(){cleanups.forEach(fn=>fn());},passive};
}
test('unresolved payment holds legacy Back as soon as its UI can paint',()=>{
 const h=harness();h.paint(); // Browser input may arrive before passive effects run.
 assert.deepEqual(h.back(),{held:true,path:'/checkout/payment/UNPAID',destination:'/orders/UNPAID'});
 h.cleanup();assert.equal(h.back().held,false);
});
test('inactive payment does not install a blocking Back guard',()=>{
 const h=harness(false);h.paint();assert.equal(h.back().held,false);assert.equal(h.back().destination,null);h.cleanup();
});

test('client bootstrap synchronously installs the dispatcher before router hydration',()=>{
 let loaded=false;
 const compiled=ts.transpileModule(fs.readFileSync('instrumentation-client.ts','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText;
 vm.runInNewContext(compiled,{exports:{},require:name=>{assert.equal(name,'./lib/paymentNavigation');loaded=true;}});
 assert.equal(loaded,true);
});
