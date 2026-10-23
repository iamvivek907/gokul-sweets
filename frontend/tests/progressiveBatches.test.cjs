const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript');
const api={};vm.runInNewContext(ts.transpileModule(fs.readFileSync('lib/progressiveBatches.ts','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText,{exports:api,Promise});
const flush=()=>new Promise(resolve=>setImmediate(resolve));
test('optional batches publish before the slowest read and never exceed two in flight',async()=>{
 const waiting=[],seen=[],controller=new AbortController();
 const complete=api.progressiveBatches([1,2,3],id=>new Promise((resolve,reject)=>waiting.push({id,resolve,reject})),value=>seen.push(value),controller.signal);
 assert.equal(waiting.length,2);waiting[0].resolve('first');await flush();assert.deepEqual(seen,['first']);assert.equal(waiting.length,3);
 waiting[1].reject(Error('optional failure'));waiting[2].resolve('last');await complete;assert.deepEqual(seen,['first','last']);
});
test('cancellation ignores late data and does not start queued reads',async()=>{
 const waiting=[],seen=[],controller=new AbortController();
 const complete=api.progressiveBatches([1,2,3],()=>new Promise(resolve=>waiting.push(resolve)),value=>seen.push(value),controller.signal);
 controller.abort();waiting.forEach(resolve=>resolve('old branch'));await complete;assert.equal(waiting.length,2);assert.deepEqual(seen,[]);
});
