const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),ts=require('typescript'),Module=require('node:module');
function api(responses){
 const calls=[],file=path.resolve(__dirname,'../services/badgeApi.ts'),loaded=new Module(file,module);
 loaded.require=name=>name==='./apiClient'?{apiClient:async(url,options)=>{calls.push({url,options});return responses.shift();}}:require(name);
 loaded._compile(ts.transpileModule(fs.readFileSync(file,'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText,file);return {...loaded.exports,calls};
}
test('badge optional reads reject malformed data and keep server-configured names and thresholds',async()=>{
 const snapshot={badges:[{id:12,name:'Custom loyal guest',minimumSubtotal:625,requiredOrders:8,bonusPercent:17.5}],current:null},subject=api([snapshot,[]]);
 assert.deepEqual(await subject.getBadges(),snapshot);assert.equal(subject.calls[0].options.credentials,'include');await assert.rejects(subject.getBadges(),/unavailable/);
});
test('empty or malformed celebrations do not open a customer modal',async()=>{
 const subject=api([null,[],{awardId:1}]);assert.equal(await subject.claimBadge(),null);assert.equal(await subject.claimBadge(),null);assert.equal(await subject.claimBadge(),null);
});
test('presentation acknowledges the exact server claim and keeps identity cookies',async()=>{
 const value={awardId:41,claimId:'server-lease',name:'Regular',appearance:'GOLD',bonusPercent:20},subject=api([value,undefined]);assert.deepEqual(await subject.claimBadge(),value);await subject.acknowledgeBadge(value);
 assert.equal(subject.calls[1].url,'/api/customer/identity/badges/41/acknowledge');assert.deepEqual(JSON.parse(subject.calls[1].options.body),{claimId:'server-lease'});assert.equal(subject.calls[1].options.credentials,'include');
});
