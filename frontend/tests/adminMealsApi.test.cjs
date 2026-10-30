const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript');
function fixture(response={ok:true,json:async()=>({id:1,version:2})}){
 const calls=[],exports={};vm.runInNewContext(ts.transpileModule(fs.readFileSync('services/adminMealsApi.ts','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText,{exports,Error,JSON,require:()=>({adminFetch:async(...args)=>{calls.push(args);return response;}})});return {api:exports,calls};
}
for(const id of [0,7])test('meal '+(id?'update':'creation')+' uses the staff CSRF request path with branch and version',async()=>{
 const {api,calls}=fixture(),signal=new AbortController().signal,draft={id,version:id?4:0,title:'Lunch',groups:[]};await api.saveAdminMeal(3,draft,signal);
 const [path,marker,options]=calls[0];assert.equal(path,'/api/admin/branches/3/meals'+(id?'/7':''));assert.equal(marker,'staff-session');assert.equal(options.method,id?'PUT':'POST');assert.equal(options.headers['Content-Type'],'application/json');assert.equal(options.signal,signal);assert.deepEqual(JSON.parse(options.body),draft);
});
for(const status of [401,403,409,500])test('meal save failure '+status+' rejects without mutating the draft',async()=>{
 const {api}=fixture({ok:false,json:async()=>({message:'Please review this meal'})}),draft={id:7,version:4,title:'Lunch'};const before=JSON.stringify(draft);await assert.rejects(api.saveAdminMeal(3,draft,new AbortController().signal),/Please review/);assert.equal(JSON.stringify(draft),before);
});
