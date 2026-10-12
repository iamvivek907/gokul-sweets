const assert=require('node:assert/strict'),test=require('node:test'),fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript');
function fixture(){
 let now=0;const requests=[],exports={};
 vm.runInNewContext(ts.transpileModule(fs.readFileSync('lib/branchOperationalCache.ts','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText,{exports,AbortSignal,Date:{now:()=>now},require:()=>({apiClient:(path,options)=>new Promise((resolve,reject)=>requests.push({path,options,resolve,reject}))})});
 return {api:exports,requests,advance:ms=>now+=ms};
}
test('route remounts share reads, reuse only recent successful checks and revalidate expired snapshots',async()=>{
 const f=fixture(),first=f.api.checkOperationalBranch(1),second=f.api.checkOperationalBranch(1);
 assert.equal(first,second);assert.equal(f.requests.length,1);
 f.requests[0].resolve({id:1,operational:true});await first;
 assert.equal((await f.api.checkOperationalBranch(1)).id,1);assert.equal(f.requests.length,1);
 f.advance(15000);assert.equal(f.api.cachedOperationalBranch(1),null);
 const next=f.api.checkOperationalBranch(1);assert.equal(f.requests.length,2);f.requests[1].resolve({id:1,operational:false});await next;
 assert.equal(f.api.cachedOperationalBranch(1).operational,false);
});
test('failed revalidation invalidates the old successful result and permits retry',async()=>{
 const f=fixture(),first=f.api.checkOperationalBranch(1);f.requests[0].resolve({id:1});await first;
 const failing=f.api.checkOperationalBranch(1,true);f.requests[1].reject(new Error('offline'));await assert.rejects(failing);
 assert.equal(f.api.cachedOperationalBranch(1),null);
 const retry=f.api.checkOperationalBranch(1);assert.equal(f.requests.length,3);f.requests[2].resolve({id:1});await retry;
});
test('unused branch snapshots remain bounded while recent branches remain available',async()=>{
 const f=fixture();for(let id=1;id<=9;id++){const read=f.api.checkOperationalBranch(id);f.requests.at(-1).resolve({id});await read;}
 assert.equal(f.api.cachedOperationalBranch(1),null);assert.equal(f.api.cachedOperationalBranch(9).id,9);
});
