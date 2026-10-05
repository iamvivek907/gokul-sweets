const assert=require('node:assert/strict'),test=require('node:test'),fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript');
const code=ts.transpileModule(fs.readFileSync('lib/abortSignalCompatibility.ts','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText;
function fixture(){
 class LegacySignal extends AbortSignal {}
 Object.defineProperties(LegacySignal,{any:{value:undefined,configurable:true},timeout:{value:undefined,configurable:true}});
 const timers=[],exports={};
 vm.runInNewContext(code,{exports,AbortSignal:LegacySignal,AbortController,DOMException,setTimeout:(callback,delay)=>timers.push({callback,delay})});
 return {Signal:LegacySignal,timers,install:exports.installAbortSignalCompatibility};
}
test('missing mobile cancellation APIs combine sources and release every listener on abort',()=>{
 const f=fixture(),first=new AbortController(),second=new AbortController();let added=0,removed=0;
 for(const source of [first.signal,second.signal]){
  const add=source.addEventListener.bind(source),remove=source.removeEventListener.bind(source);
  source.addEventListener=(...args)=>{added++;return add(...args);};source.removeEventListener=(...args)=>{removed++;return remove(...args);};
 }
 const combined=f.Signal.any([first.signal,first.signal,second.signal]);assert.equal(added,2);
 const reason=new DOMException('Left page','AbortError');second.abort(reason);
 assert.equal(combined.aborted,true);assert.equal(combined.reason,reason);assert.equal(removed,2);
 first.abort();assert.equal(combined.reason,reason);
});
test('already aborted consumers fail immediately and timeout fallback retains deadline semantics',()=>{
 const f=fixture(),caller=new AbortController();caller.abort('gone');
 assert.equal(f.Signal.any([caller.signal]).reason,'gone');
 const deadline=f.Signal.timeout(8000),combined=f.Signal.any([deadline]);
 assert.equal(combined.aborted,false);assert.equal(f.timers[0].delay,8000);f.timers[0].callback();
 assert.equal(combined.aborted,true);assert.equal(combined.reason.name,'TimeoutError');
});
test('supported browsers retain native cancellation methods',()=>{
 const f=fixture(),any=AbortSignal.any,timeout=AbortSignal.timeout;
 f.install(AbortSignal);assert.equal(AbortSignal.any,any);assert.equal(AbortSignal.timeout,timeout);
});
