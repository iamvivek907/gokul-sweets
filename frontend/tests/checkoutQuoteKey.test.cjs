const {test}=require('node:test');
const assert=require('node:assert/strict'),fs=require('node:fs'),ts=require('typescript');
const moduleExports={};new Function('exports',ts.transpileModule(fs.readFileSync('lib/checkoutQuoteKey.ts','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS}}).outputText)(moduleExports);
const {checkoutQuoteKey}=moduleExports;
const auto={branchId:1,pickupSlotId:2,pickupType:'NORMAL',customerName:'GOKUL_GUEST',customerPhone:'9876543210',items:[{productId:3,quantity:1,weightGrams:null}]};
test('automatic and manually accepted prices match regardless of property insertion order',()=>{
 const click={branchId:1,pickupSlotId:2,customerName:'GOKUL_GUEST',customerPhone:'9876543210',pickupType:'NORMAL',items:[{quantity:1,weightGrams:null,productId:3}]};
 assert.equal(checkoutQuoteKey(auto),checkoutQuoteKey(click));
});
test('changed pickup, customer, quantities and reservation require another price review',()=>{
 for(const request of [{...auto,branchId:2},{...auto,pickupSlotId:3},{...auto,pickupType:'PRIORITY'},{...auto,customerPhone:'9876543211'},{...auto,customerName:'Customer'},{...auto,items:[{productId:3,quantity:2,weightGrams:null}]}])assert.notEqual(checkoutQuoteKey(auto),checkoutQuoteKey(request));
 assert.notEqual(checkoutQuoteKey(auto),checkoutQuoteKey(auto,'existing-order'));
});
