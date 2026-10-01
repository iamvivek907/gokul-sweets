const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),ts=require('typescript');
const source=fs.readFileSync('lib/checkoutIdentity.ts','utf8');
const code=ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.CommonJS}}).outputText;
const exportsObject={};new Function('exports',code)(exportsObject);
const contact=exportsObject.verifiedCheckoutContact;
test('verified phone becomes checkout contact without borrowing a guest name',()=>{
 assert.deepEqual(contact({authenticated:true,phone:'+91 9876543210',name:' '}),{name:'GOKUL_GUEST',phone:'9876543210'});
 assert.deepEqual(contact({authenticated:true,phone:'9876543210',name:' राम '}),{name:'राम',phone:'9876543210'});
});
test('an unverified or malformed identity cannot skip customer details',()=>{
 for(const session of [{authenticated:false,phone:'9876543210'},{authenticated:true},{authenticated:true,phone:'+1 1234567890'},{authenticated:true,phone:'1234567890'}])assert.equal(contact(session),null);
});
