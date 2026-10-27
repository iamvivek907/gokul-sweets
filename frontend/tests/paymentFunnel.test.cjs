const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),ts=require('typescript'),vm=require('node:vm');
function setup(){
 const exports={},events=[];let response={paymentStatus:'PENDING'},reject=false;
 vm.runInNewContext(ts.transpileModule(fs.readFileSync('services/paymentApi.ts','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText,{exports,require:()=>({apiClient:()=>reject?Promise.reject(Error('network')):Promise.resolve(response)}),AbortSignal,JSON,CustomEvent:class{constructor(type,{detail}){this.type=type;this.detail=detail;}},window:{dispatchEvent:e=>events.push(e.detail)}});
 return {api:exports,events,set:(value,failure=false)=>{response=value;reject=failure;}};
}
test('payment telemetry observes server outcomes and never treats network errors as failed payments',async()=>{
 const {api,events,set}=setup();const pending={paymentStatus:'PENDING',orderNumber:'PRIVATE',paymentId:42};set(pending);
 assert.equal(await api.createPayment({orderNumber:'PRIVATE'}),pending);assert.deepEqual(events,['FUNNEL_PAYMENT']);
 set({paymentStatus:'PAID'});assert.equal((await api.refreshPayment(42)).paymentStatus,'PAID');assert.deepEqual(events,['FUNNEL_PAYMENT','FUNNEL_PAID']);
 set({payment:{paymentStatus:'FAILED'}});await api.getPaymentForOrder('PRIVATE');assert.equal(events.at(-1),'FUNNEL_FAILED');
 set({payment:null});const count=events.length;await api.getPaymentForOrder('PRIVATE');assert.equal(events.length,count);
 set(null,true);await assert.rejects(api.refreshPayment(42),/network/);assert.equal(events.length,count);
 assert.ok(!JSON.stringify(events).includes('PRIVATE'));
});
