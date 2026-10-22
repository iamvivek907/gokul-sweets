const assert=require('node:assert/strict');
const test=require('node:test');
const vm=require('node:vm');
const fs=require('node:fs');
const ts=require('typescript');
test('bell and sound use one timer across staggered mounts and release it on last unmount',()=>{
 const exports={};let tick,created=0,cleared=0,a=0,b=0;
 vm.runInNewContext(ts.transpileModule(fs.readFileSync('services/customerNotificationPolling.ts','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText,
 {exports,setInterval:callback=>{tick=callback;created++;return 1;},clearInterval:()=>cleared++});
 const first=exports.subscribeNotificationTick(()=>a++);tick();
 const second=exports.subscribeNotificationTick(()=>b++);tick();
 assert.equal(created,1);assert.equal(a,2);assert.equal(b,1);
 first();tick();assert.equal(a,2);assert.equal(b,2);assert.equal(cleared,0);
 second();assert.equal(cleared,1);
 const again=exports.subscribeNotificationTick(()=>{});assert.equal(created,2);again();
});
