const assert=require('node:assert/strict');
const test=require('node:test');
const fs=require('node:fs');
const ts=require('typescript');
const vm=require('node:vm');
function load(name){const exports={};vm.runInNewContext(ts.transpileModule(fs.readFileSync(`${__dirname}/../lib/${name}.ts`,'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText,{exports,Intl,Date,Math,Number});return exports;}
const {indiaToday,validPickupDate,pickupIsFresh}=load('pickupFreshness');
const {formatWeight}=load('orderQuantity');
const {usefulRebateTarget}=load('pickupAddOnRebate');
test('India midnight advances bounds despite UTC being yesterday',()=>{assert.equal(indiaToday(new Date('2026-09-30T18:29:59Z')),'2026-09-30');assert.equal(indiaToday(new Date('2026-09-30T18:30:00Z')),'2026-10-01');assert.equal(validPickupDate('2026-09-30','2026-10-01',7),false);});
test('date handler rejects malformed, impossible, backdated and out-of-window dates',()=>{for(const value of ['2026-9-30','2026-02-30','not-a-date','2026-09-30','2026-10-09'])assert.equal(validPickupDate(value,'2026-10-01',7),false);assert.equal(validPickupDate('2026-10-08','2026-10-01',7),true);});
test('elapsed slots invalidate without replacing valid future slots',()=>{const s={date:'2026-10-01',slot:{slotDate:'2026-10-01',startTime:'09:00:00',active:true}};assert.equal(pickupIsFresh(s,new Date('2026-10-01T03:29:59Z')),true);assert.equal(pickupIsFresh(s,new Date('2026-10-01T03:30:00Z')),false);assert.equal(pickupIsFresh({...s,date:'2026-10-02',slot:{...s.slot,slotDate:'2026-10-02'}},new Date('2026-10-01T03:30:00Z')),true);});
test('quantities retain precision with readable kilos and grams',()=>{assert.equal(formatWeight(2000),'2 kg');assert.equal(formatWeight(1250),'1.25 kg');assert.equal(formatWeight(999),'999 g');assert.equal(formatWeight(1001),'1.001 kg');assert.equal(formatWeight(null),'Weight pending');});
test('rebate caps and extra spend prevent revenue-consuming promotions',()=>{const offer={rebateAmount:20,amountNeededForNextSlab:50,nextSlabRebateAmount:80,maximumDiscountAmount:40};assert.equal(usefulRebateTarget([offer]).nextSlabRebateAmount,40);assert.equal(usefulRebateTarget([{...offer,maximumDiscountAmount:null}]),null);assert.equal(usefulRebateTarget([{...offer,maximumDiscountAmount:20}]),null);assert.equal(usefulRebateTarget([{...offer,nextSlabRebateAmount:70,maximumDiscountAmount:100}]),null);assert.equal(usefulRebateTarget([]),null);});
