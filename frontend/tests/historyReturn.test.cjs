const assert=require('node:assert/strict'),test=require('node:test'),fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript');
const moduleValue={exports:{}};
vm.runInNewContext(ts.transpileModule(fs.readFileSync('lib/historyReturn.ts','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS}}).outputText,{module:moduleValue,exports:moduleValue.exports,require:()=>({}),URL});
const {safeHistoryReturn}=moduleValue.exports;
test('history return retains page and filters only on allowed customer routes',()=>{
 for(const path of ['/orders?historyPage=6&before=ORDER-61&status=COMPLETED','/profile/orders?historyPage=6&q=peda'])assert.equal(safeHistoryReturn(path),path);
 for(const path of [null,'https://evil.example/orders','//evil.example/orders','/\\evil.example/orders','/admin','/orders/OTHER','/profile/privacy','/orders?'+('x'.repeat(2000))])assert.equal(safeHistoryReturn(path),'/orders');
});
