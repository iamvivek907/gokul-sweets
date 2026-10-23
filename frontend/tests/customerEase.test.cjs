const assert=require('node:assert/strict'),test=require('node:test'),fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript');
function read(file,require=()=>{}){const exports={};vm.runInNewContext(ts.transpileModule(fs.readFileSync(file,'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText,{exports,require,Date,Set,AbortSignal,AbortController,setTimeout,clearTimeout});return exports;}
const {canUndoCart}=read('lib/cartUndo.ts'),{checkoutAction}=read('lib/checkoutAction.ts'),{matchesMenuSearch}=read('lib/menuSearch.ts');
test('undo expires and refuses any cart, branch, pickup, reservation or checkout change',()=>{
 const change={before:'original',after:'remaining',branch:'1',pickup:'today',pending:'',attempt:'',expiresAt:100};
 const current={after:'remaining',branch:'1',pickup:'today',pending:'',attempt:''};
 assert.equal(canUndoCart(change,current,99),true);assert.equal(canUndoCart(change,current,100),false);
 for(const field of Object.keys(current))assert.equal(canUndoCart(change,{...current,[field]:'changed'},99),false,field);
 assert.equal(canUndoCart({...change,pending:'reserved'},{...current,pending:'reserved'},99),false);
});
test('checkout action names the prerequisite before promising payment',()=>{
 const ready={identityResolved:true,verificationBusy:false,contact:true,attempt:false,branchReviewed:true,pickupConfirmed:true,ready:true};
 assert.equal(checkoutAction(ready),'Pay now');
 for(const [field,value,expected] of [['contact',false,'Verify phone to continue'],['branchReviewed',false,'Confirm pickup branch'],['pickupConfirmed',false,'Confirm pickup time'],['ready',false,'Checking total…'],['attempt',true,'Retry checkout'],['verificationBusy',true,'Verifying phone…']])assert.equal(checkoutAction({...ready,[field]:value}),expected);
});
test('local menu search handles common spellings, category terms, Hindi and multiple words',()=>{
 const product={name:'Paneer Burger',categoryName:'Snacks',description:'Freshly prepared'};
 for(const query of ['panner','पनीर','burger snacks','PANEER','पनीर बर्गर'])assert.equal(matchesMenuSearch(product,query),true,query);
 for(const query of ['pizza','paneer sweets','rasgulla'])assert.equal(matchesMenuSearch(product,query),false,query);
 assert.equal(matchesMenuSearch(product,''),true);
});
