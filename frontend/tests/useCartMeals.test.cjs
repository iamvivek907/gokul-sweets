const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript');
function moduleFor(file,mocks={}){const exports={};vm.runInNewContext(ts.transpileModule(fs.readFileSync(file,'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText,{exports,require:id=>mocks[id]});return exports;}
const meals=moduleFor('lib/cartMeals.ts');
const product={id:9,name:'Laddoo',price:200,saleMode:'WEIGHT',available:true,minimumWeightGrams:250,weightStepGrams:50};
const recipe=(id,grams)=>({instanceId:id,mealId:1,version:1,quantity:1,optionIds:['a'],title:'Sweet meal',regularTotal:grams*.2,total:grams*.18,components:[{productId:9,quantity:1,weightGrams:grams}]});
function fixture(cart){let stored=meals.reconcileCartMeals(cart);const {useCart}=moduleFor('hooks/useCart.ts',{'@/lib/cartMeals':meals,react:{useMemo:f=>f(),useSyncExternalStore:(_s,get)=>get()},'@/lib/cartStorage':{getCartSnapshot:()=>JSON.stringify(stored),parseCart:JSON.parse,saveCart:next=>stored=meals.reconcileCartMeals(next),clearStoredCart:()=>stored={branchId:null,items:[]}}});return {hook:useCart,cart:()=>stored};}
test('menu Add preserves 500g in a meal when the customer selects an extra 250g',()=>{const f=fixture({branchId:1,items:[{product,quantity:1,weightGrams:500}],meals:[recipe('first',500)]});assert.equal(f.hook().addItem(product,1,250),'added');assert.equal(f.cart().items[0].weightGrams,750);assert.equal(meals.standaloneCartItems(f.cart())[0].weightGrams,250);assert.equal(f.cart().meals.length,1);assert.equal(meals.mealSavings(f.cart().meals),10);assert.equal(f.cart().mealNotice,undefined);});
test('editing menu extra weight preserves overlapping meals and can repeat without accumulating extras',()=>{const f=fixture({branchId:1,items:[{product,quantity:1,weightGrams:1000}],meals:[recipe('first',500),recipe('second',250)]});f.hook().addItem(product,1,400);assert.equal(f.cart().items[0].weightGrams,1150);f.hook().addItem(product,1,300);assert.equal(f.cart().items[0].weightGrams,1050);assert.equal(f.cart().meals.length,2);assert.equal(meals.standaloneCartItems(f.cart())[0].weightGrams,300);});
test('ordinary weighted carts retain replacement semantics',()=>{const f=fixture({branchId:1,items:[{product,quantity:1,weightGrams:500}]});f.hook().addItem(product,1,250);assert.equal(f.cart().items[0].weightGrams,250);assert.equal(f.cart().meals,undefined);});
test('missing weight and branch mismatch leave the meal and quantities untouched',()=>{const f=fixture({branchId:1,items:[{product,quantity:1,weightGrams:500}],meals:[recipe('first',500)]}),before=JSON.stringify(f.cart());assert.equal(f.hook().addItem(product,1),'weight-required');assert.equal(f.hook().addItem(product,2,250),'branch-mismatch');assert.equal(JSON.stringify(f.cart()),before);});
test('rapid Add and Increase use the last saved quantity before the view rerenders',()=>{
 const dish={...product,saleMode:'UNIT'};
 const f=fixture({branchId:null,items:[]}),view=f.hook();
 view.addItem(dish,1);view.addItem(dish,1);view.increaseQuantity(dish.id,1);
 assert.equal(f.cart().items[0].quantity,3);
});
test('a stale Increase cannot change the same dish in another branch’s cart',()=>{
 const dish={...product,saleMode:'UNIT'};
 const f=fixture({branchId:1,items:[{product:dish,quantity:1}]}),oldMenu=f.hook();
 oldMenu.replaceCartForBranch(dish,2);
 for(let i=0;i<3;i++)f.hook().addItem(dish,2);
 const before=JSON.stringify(f.cart());
 oldMenu.increaseQuantity(dish.id,1);
 oldMenu.increaseQuantity(dish.id);
 assert.equal(JSON.stringify(f.cart()),before);
 assert.equal(f.cart().items[0].quantity,4);
 f.hook().increaseQuantity(dish.id,2);
 assert.equal(f.cart().items[0].quantity,5);
});

for(const quantity of [1,2])test(`a stale Decrease from ${quantity} units leaves the new branch cart intact`,()=>{
 const dish={...product,saleMode:'UNIT'};
 const f=fixture({branchId:1,items:[{product:dish,quantity}]}),oldMenu=f.hook();
 oldMenu.replaceCartForBranch(dish,2);f.hook().addItem(dish,2);
 const before=JSON.stringify(f.cart());
 oldMenu.decreaseQuantity(dish.id,1);oldMenu.decreaseQuantity(dish.id);
 assert.equal(JSON.stringify(f.cart()),before);
 f.hook().decreaseQuantity(dish.id,2);
 assert.equal(f.cart().items[0].quantity,1);
});
test('rapid Decrease uses the latest same-branch quantity and removes the final line',()=>{
 const dish={...product,saleMode:'UNIT'};
 const f=fixture({branchId:1,items:[{product:dish,quantity:2}]}),view=f.hook();
 view.increaseQuantity(dish.id,1);view.decreaseQuantity(dish.id,1);
 assert.equal(f.cart().items[0].quantity,2);
 view.decreaseQuantity(dish.id,1);view.decreaseQuantity(dish.id,1);
 assert.equal(f.cart().branchId,null);
 assert.equal(f.cart().items.length,0);
});
test('Decrease preserves meal portions while removing the last standalone weight',()=>{
 const f=fixture({branchId:1,items:[{product,quantity:1,weightGrams:800}],meals:[recipe('first',500)]}),view=f.hook();
 view.decreaseQuantity(product.id,1);
 assert.equal(f.cart().items[0].weightGrams,750);
 view.decreaseQuantity(product.id,1);
 assert.equal(f.cart().items[0].weightGrams,500);
 assert.equal(f.cart().meals.length,1);
});
test('clear confirmation only applies to the exact saved cart snapshot',()=>{
 const dish={...product,saleMode:'UNIT'};
 const f=fixture({branchId:1,items:[{product:dish,quantity:1}]}),view=f.hook();
 const confirmed=JSON.stringify(f.cart());
 view.addItem(dish,1);
 assert.equal(view.clearCart(confirmed),false);
 assert.equal(f.cart().items[0].quantity,2);
 view.replaceCartForBranch(dish,2);
 assert.equal(view.clearCart(confirmed),false);
 assert.equal(f.cart().branchId,2);
 assert.equal(view.clearCart(JSON.stringify(f.cart())),true);
 assert.equal(f.cart().items.length,0);
});
