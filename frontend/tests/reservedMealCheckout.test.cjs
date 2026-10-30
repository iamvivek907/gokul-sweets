const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript');
function load(file,mocks={},globals={}){const exports={};vm.runInNewContext(ts.transpileModule(fs.readFileSync(file,'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText,{exports,require:name=>mocks[name]??{},AbortSignal,Error,JSON,Promise,...globals});return exports;}
const meals=load('lib/cartMeals.ts'),refresh=load('lib/checkoutRefresh.ts');
const storage=load('lib/cartStorage.ts',{'@/lib/cartMeals':meals,'@/lib/menuPickupMode':{} });
const availability=load('services/availabilityApi.ts',{'@/lib/cartMeals':meals,'@/lib/cartStorage':storage});
const file=fs.readFileSync('components/checkout/CheckoutOffersPanel.tsx','utf8'),ast=ts.createSourceFile('panel.tsx',file,ts.ScriptTarget.Latest,true,ts.ScriptKind.TSX);let handler;
function visit(node){if(ts.isFunctionDeclaration(node)&&node.name?.text==='refreshAfterAddition')handler=node.getText(ast);ts.forEachChild(node,visit);}visit(ast);assert.ok(handler);
function fixture({missingChoices=false,quoteFails=false,changeDuringQuote=false}={}){
 const products=new Map([[1,{id:1,name:'Main',price:100,saleMode:'UNIT',available:true}],[2,{id:2,name:'Side',price:50,saleMode:'UNIT',available:true}]]);
 const meal={instanceId:'meal1',mealId:1,version:1,quantity:1,title:'Lunch',optionIds:['main','side'],total:120,regularTotal:150,components:[{productId:1,quantity:1,weightGrams:null},{productId:2,quantity:1,weightGrams:null}]};
 let cart=meals.addMeal({branchId:1,items:[{product:{id:3,name:'Drink',price:20,saleMode:'UNIT',available:true},quantity:1,weightGrams:null}]},meal,products,1);if(missingChoices)cart={...cart,meals:undefined};
 let raw=JSON.stringify(cart),pickup={date:'2026-10-31',slot:{id:1},pickupType:'NORMAL'},pending={orderNumber:'TEST',branchId:1},quoted,updated;const original=raw;
 const env={exports:{},AbortSignal,Error,JSON,Promise,orderNumber:'TEST',features:{acceptedCheckoutQuote:true},getCartSnapshot:()=>raw,getPickupSlotSnapshot:()=>JSON.stringify(pickup),getStoredBranchSnapshot:()=>'{"id":1}',getPendingOrderSnapshot:()=>JSON.stringify(pending),getCustomerSnapshot:()=>'{"name":"Customer","phone":"9876543210"}',parseCart:storage.parseCart,parsePickupSlot:JSON.parse,parseCustomerDetails:JSON.parse,parsePendingOrder:JSON.parse,adjustCartItems:meals.adjustCartItems,availabilityItems:availability.availabilityItems,getCustomerOrder:async()=>({orderStatus:'PENDING_PAYMENT',paymentStatus:null,items:[{productName:'Main',mealLabel:'Lunch'}]}),previewCheckoutQuote:async request=>{quoted=request;if(quoteFails)throw Error('Quote rejected');if(changeDuringQuote)raw+=' ';return {token:'q'};},updatePendingCheckout:async(number,request)=>{updated=request;const subtotal=request.items.reduce((n,i)=>n+cart.items.find(c=>c.product.id===i.productId).product.price*i.quantity,0)-(request.items[0].mealSelections?.length?30:0);return {orderStatus:'PENDING_PAYMENT',items:[],totalAmount:subtotal};},saveCart:value=>{cart=meals.reconcileCartMeals(value);raw=JSON.stringify(cart);},savePickupSlot:value=>pickup=value,savePendingOrder:value=>pending=value,createCartFingerprint:items=>JSON.stringify(items.map(i=>i.mealIdentity??'')),getAvailableRebates:async()=>[],apiClient:async()=>[],onRefreshRequiredChange:()=>{},onUpdateError:()=>{},CheckoutUpdateUncertainError:refresh.CheckoutUpdateUncertainError};
 for(const [,name] of handler.matchAll(/\b(set[A-Z]\w*)\(/g))env[name]=()=>{};
 vm.runInNewContext(ts.transpileModule('export '+handler,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText,env);
 return {env,run:env.exports.refreshAfterAddition,get cart(){return cart},get quoted(){return quoted},get updated(){return updated},get pending(){return pending},get pickup(){return pickup},get raw(){return raw},original};
}
test('reserved meal pickup-only updates carry claims through quote and reservation update',async()=>{const f=fixture();await f.run(undefined,{date:'2026-10-31',slot:{id:2},pickupType:'NORMAL'});assert.equal(f.quoted.items[0].mealSelections[0].instanceId,'meal1');assert.equal(f.updated.items[0].mealSelections[0].instanceId,'meal1');assert.equal(f.pending.totalAmount,140);assert.equal(f.pickup.slot.id,2);assert.equal(f.cart.meals.length,1);});
test('reserved meal changes to ordinary quantities preserve the meal discount',async()=>{const f=fixture();await f.run(f.cart.items.map(i=>i.product.id===3?{...i,quantity:3}:i));assert.equal(f.pending.totalAmount,180);assert.equal(f.cart.meals.length,1);assert.equal(f.updated.items[0].mealSelections.length,1);});
test('removing a required dish drops its claim and preserves a visible notice',async()=>{const f=fixture();await f.run(f.cart.items.filter(i=>i.product.id!==1));assert.equal(f.updated.items[0].mealSelections,undefined);assert.equal(f.cart.meals,undefined);assert.match(f.cart.mealNotice,/Review your total/);assert.equal(f.pending.totalAmount,70);});
test('reserved meals with missing saved choices cannot be silently repriced',async()=>{const f=fixture({missingChoices:true});await assert.rejects(f.run(),/saved choices/);assert.equal(f.quoted,undefined);assert.equal(f.updated,undefined);assert.equal(f.raw,f.original);});
for(const options of [{quoteFails:true},{changeDuringQuote:true}])test('a failed or stale quote cannot update the reserved meal '+JSON.stringify(options),async()=>{const f=fixture(options);await assert.rejects(f.run());assert.equal(f.updated,undefined);assert.equal(f.cart.meals.length,1);});

// Run the real review-page callback, including its missing-choice recovery guard.
const reviewSource=fs.readFileSync('app/checkout/review/page.tsx','utf8');
const reviewAst=ts.createSourceFile('review.tsx',reviewSource,ts.ScriptTarget.Latest,true,ts.ScriptKind.TSX);
let reviewHandler;
function findReviewHandler(node){
 if(ts.isJsxSelfClosingElement(node)&&node.tagName.getText(reviewAst)==='CheckoutAdjustmentDialog'){
  const attribute=node.attributes.properties.find(a=>ts.isJsxAttribute(a)&&a.name.getText(reviewAst)==='onApply');
  reviewHandler=attribute?.initializer?.expression?.getText(reviewAst);
 }
 ts.forEachChild(node,findReviewHandler);
}
findReviewHandler(reviewAst);assert.ok(reviewHandler);
function reviewFixture(options={}){
 const f=fixture(options),env=f.env;
 Object.assign(env,{branch:{id:1},pendingOrder:{...f.pending,orderStatus:'PENDING_PAYMENT'},quoteEnabled:true,checkoutQuoteKey:()=> 'quote-key'});
 for(const [,name] of reviewHandler.matchAll(/\b(set[A-Z]\w*)\(/g))env[name]=()=>{};
 vm.runInNewContext(ts.transpileModule('exports.apply='+reviewHandler,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText,env);
 return {f,apply:env.exports.apply};
}
for(const quoteEnabled of [true,false])test('review blocks reserved meals without saved choices before quote/update (quotes '+quoteEnabled+')',async()=>{
 const {f,apply}=reviewFixture({missingChoices:true});f.env.quoteEnabled=quoteEnabled;
 await assert.rejects(apply(f.cart.items,{date:'2026-10-31',slot:{id:2},pickupType:'NORMAL'}),/saved choices/);
 assert.equal(f.quoted,undefined);assert.equal(f.updated,undefined);assert.equal(f.raw,f.original);assert.equal(f.pickup.slot.id,1);assert.equal(f.pending.totalAmount,undefined);
});
test('review pickup changes retain complete meal claims and discounted total',async()=>{
 const {f,apply}=reviewFixture();await apply(f.cart.items,{date:'2026-10-31',slot:{id:2},pickupType:'NORMAL'});
 assert.equal(f.quoted.items[0].mealSelections[0].instanceId,'meal1');assert.equal(f.updated.items[0].mealSelections[0].instanceId,'meal1');assert.equal(f.pending.totalAmount,140);assert.equal(f.pickup.slot.id,2);
});
test('review permits explicit removal of an included dish with a visible notice',async()=>{
 const {f,apply}=reviewFixture();await apply(f.cart.items.filter(i=>i.product.id!==1),{date:'2026-10-31',slot:{id:2},pickupType:'NORMAL'});
 assert.equal(f.updated.items[0].mealSelections,undefined);assert.equal(f.cart.meals,undefined);assert.match(f.cart.mealNotice,/Review your total/);assert.equal(f.pending.totalAmount,70);
});
test('review permits ordinary reserved orders without meal choices',async()=>{
 const {f,apply}=reviewFixture({missingChoices:true});f.env.getCustomerOrder=async()=>({orderStatus:'PENDING_PAYMENT',paymentStatus:null,items:[{productName:'Main'}]});
 await apply(f.cart.items,{date:'2026-10-31',slot:{id:2},pickupType:'NORMAL'});assert.ok(f.updated);assert.equal(f.updated.items[0].mealSelections,undefined);
});
