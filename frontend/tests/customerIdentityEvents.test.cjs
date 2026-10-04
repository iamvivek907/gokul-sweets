const assert=require('node:assert/strict'),test=require('node:test'),fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript');
function fixture(){
 const window=new EventTarget(),document=new EventTarget();document.visibilityState='visible';const writes=[];
 const exports={};vm.runInNewContext(ts.transpileModule(fs.readFileSync('lib/customerIdentityEvents.ts','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText,{exports,window,document,Event,localStorage:{setItem:(...args)=>writes.push(args)},crypto:{randomUUID:()=> 'invalidation-token'}});
 return {window,document,writes,...exports};
}
test('identity changes notify this tab and publish an anonymous cross-tab token',()=>{
 const f=fixture();let calls=0;const stop=f.subscribeCustomerIdentityChanges(()=>calls++);f.notifyCustomerIdentityChanged();assert.equal(calls,1);assert.deepEqual(f.writes,[['gokul-customer-identity-revision','invalidation-token']]);stop();f.notifyCustomerIdentityChanged();assert.equal(calls,1);
});
test('another tab invalidates identity; unrelated storage writes do not',()=>{
 const f=fixture();let calls=0;f.subscribeCustomerIdentityChanges(()=>calls++);
 f.window.dispatchEvent(Object.assign(new Event('storage'),{key:'gokul-cart'}));assert.equal(calls,0);
 f.window.dispatchEvent(Object.assign(new Event('storage'),{key:'gokul-customer-identity-revision'}));assert.equal(calls,1);
 f.window.dispatchEvent(Object.assign(new Event('storage'),{key:null}));assert.equal(calls,2);
});
test('resume revalidates identity with hidden-page and cleanup guards',()=>{
 const f=fixture();let calls=0;const stop=f.subscribeCustomerIdentityChanges(()=>calls++);
 f.document.visibilityState='hidden';f.document.dispatchEvent(new Event('visibilitychange'));assert.equal(calls,0);
 f.document.visibilityState='visible';f.window.dispatchEvent(new Event('focus'));assert.equal(calls,1);
 f.document.dispatchEvent(new Event('visibilitychange'));assert.equal(calls,2);
 f.window.dispatchEvent(Object.assign(new Event('pageshow'),{persisted:true}));assert.equal(calls,3);
 stop();f.window.dispatchEvent(new Event('focus'));f.document.dispatchEvent(new Event('visibilitychange'));assert.equal(calls,3);
});
