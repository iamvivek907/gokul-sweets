const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),test=require('node:test'),vm=require('node:vm'),ts=require('typescript');
const code=ts.transpileModule(fs.readFileSync(path.join(__dirname,'../lib/orderInvoice.ts'),'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText;
const exportsForTest={};vm.runInNewContext(code,{exports:exportsForTest,require:()=>({})});
test('delivery savings reconcile persisted delivery, inclusive fees and total',()=>{
 assert.equal(exportsForTest.orderDiscount({subtotal:100,taxAmount:0,priorityCharge:0,deliveryFee:30,totalAmount:110}),20);
 assert.equal(exportsForTest.orderDiscount({subtotal:100,taxAmount:5,priorityCharge:0,deliveryFee:30,convenienceFee:10,convenienceFeeTax:1,paymentFee:2,paymentFeeTax:.3,totalAmount:127}),20);
 assert.equal(exportsForTest.orderDiscount({subtotal:100,taxAmount:0,priorityCharge:0,totalAmount:80}),20);
});
