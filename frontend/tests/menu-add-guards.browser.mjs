import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
const date=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata'}).format(new Date(Date.now()+86400000));
const branch={id:1,name:'Test branch',active:true,operational:true,pickupAvailable:true};
const common={categoryId:1,categoryName:'Sweets',available:true,saleMode:'UNIT'};
const products=[
 {...common,id:1,name:'Last Laddu',price:20},
 {...common,id:2,name:'Fresh Peda',price:400,saleMode:'WEIGHT',minimumWeightGrams:250,weightStepGrams:50},
 {...common,id:3,name:'Rasgulla',price:15},
];
const meal={id:1,branchId:1,version:1,title:'Sweet Box',basePrice:0,pricingMode:'SELECTED_ITEMS',discountType:'PERCENT',discountValue:2,active:true,
 groups:[{key:'sweet',title:'Choose a sweet',minimum:1,maximum:1,options:[{key:'rasgulla',productId:3,quantity:1,weightGrams:null,upgrade:0}]}]};
const slot={id:1,branchId:1,slotDate:date,startTime:'18:00:00',endTime:'19:00:00',active:true,remainingCapacity:20,priorityEnabled:false};
const browser=await chromium.launch({headless:true});
try{
 const context=await browser.newContext({viewport:{width:390,height:900},serviceWorkers:'block',reducedMotion:'reduce'});
 const page=await context.newPage();page.setDefaultTimeout(15000);
 let price=400,sold=false,hold=false,releaseRefresh,releaseQuote,exactChecks=0;
 const refreshGate=new Promise(resolve=>releaseRefresh=resolve);
 const quoteGate=new Promise(resolve=>releaseQuote=resolve);
 const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Methods':'GET,POST,OPTIONS','Access-Control-Allow-Headers':'content-type'};
 await context.route('**/api/**',async route=>{
  const request=route.request(),url=new URL(request.url()),path=url.pathname;
  if(request.method()==='OPTIONS')return route.fulfill({status:204,headers});
  let json=[];
  if(path==='/api/storefront/features')json={futuristicStorefrontV2:true,checkoutExperienceV2:true,contextualStorefrontV2:true,smartAvailability:true,today:date,futureOrderingDays:30};
  else if(path==='/api/branches')json=[branch];
  else if(path==='/api/branches/1')json=branch;
  else if(path==='/api/menu')json=[{id:1,name:'Sweets',products:products.map(p=>p.id===2?{...p,price}:p)}];
  else if(path==='/api/menu/portion-groups')json={groups:[]};
  else if(path==='/api/branches/1/meals')json=[meal];
  else if(path==='/api/branches/1/meals/quote'){
   await quoteGate;
   json={mealId:1,version:1,title:meal.title,quantity:1,regularTotal:15,baseTotal:15,discount:.30,upgrades:0,total:14.70,components:[{productId:3,quantity:1,weightGrams:null,unitPrice:15,regularTotal:15}]};
  }else if(path.endsWith('/availability')){
   if(hold)await refreshGate;
   const demand=request.postDataJSON().items;
   const items=products.map(p=>({productId:p.id,available:!(p.id===3&&sold),availableQuantity:p.id===1?1:p.id===2?1000:sold?0:20}));
   const issues=items.filter(item=>!item.available&&demand.some(line=>line.productId===item.productId));
   json={today:date,maximumDate:date,dates:[{date,available:!issues.length,items,slots:[{slot,normalAvailable:!issues.length,priorityAvailable:false,issues}]}]};
   if(!url.searchParams.has('menuPreview')){
    exactChecks++;
    await route.fulfill({json,headers}).catch(()=>{});return;
   }
  }
  await route.fulfill({json,headers}).catch(()=>{});
 });
 await context.addInitScript(({branch,date,slot})=>{
  localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));
  localStorage.setItem('gokul-selected-pickup-slot',JSON.stringify({date,slot,pickupType:'NORMAL'}));
  localStorage.setItem('gokul-pickup-intent',JSON.stringify({branchId:1,date}));
  localStorage.setItem('gokul-ordering-tour:v1','seen');
 },{branch,date,slot});
 const cart=()=>page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-cart')??'{"items":[]}'));
 await page.goto(base+'/menu');await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});
 const dish=page.locator('#gokul-product-1');
 // Let the initial preview complete before holding the post-Add refresh.
 await page.waitForFunction(()=>document.querySelector('#gokul-product-1 button[aria-label="Add Last Laddu to cart"]')?.disabled===false);
 const refreshPending=page.waitForRequest(request=>hold&&new URL(request.url()).pathname.endsWith('/availability'));
 hold=true;await dish.getByRole('button',{name:'Add Last Laddu to cart',exact:true}).click();
 await page.evaluate(()=>window.dispatchEvent(new Event('online')));await refreshPending;
 assert.equal((await cart()).items[0].quantity,1,'ordinary Add updates without waiting for the network');
 await dish.getByRole('button',{name:'Add one more Last Laddu',exact:true}).click();
 await page.getByText('No more available for this pickup.',{exact:true}).waitFor();
 assert.equal((await cart()).items[0].quantity,1,'the verified stock limit still applies during a refresh');
 hold=false;releaseRefresh();

 await page.locator('#gokul-product-2').getByRole('button',{name:'Add Fresh Peda to cart',exact:true}).click();
 const weight=page.getByRole('dialog',{name:'Fresh Peda',exact:true});
 const estimate=weight.getByText('Estimated price',{exact:true}).locator('..');
 assert.match(await estimate.innerText(),/₹100/);
 price=800;await page.evaluate(()=>window.dispatchEvent(new Event('online')));
 await page.waitForFunction(()=>document.querySelector('#gokul-product-2')?.textContent?.includes('₹200'));
 await weight.getByRole('button',{name:/^Add /}).click();
 await weight.getByRole('alert').filter({hasText:'Review the price and quantity'}).waitFor();
 assert.match(await estimate.innerText(),/₹200/);
 assert.equal((await cart()).items.some(item=>item.product.id===2),false);
 await weight.getByRole('button',{name:/^Add /}).click();await weight.waitFor({state:'hidden'});
 assert.equal((await cart()).items.find(item=>item.product.id===2).product.price,800);

 await page.locator('#menu-meals').getByRole('button',{name:'Build meal Sweet Box',exact:true}).click();
 const builder=page.getByRole('dialog',{name:'Sweet Box',exact:true});
 await builder.getByRole('radio').check();
 const stockDone=page.waitForResponse(response=>{const url=new URL(response.url());return url.pathname.endsWith('/availability')&&!url.searchParams.has('menuPreview');});
 await builder.getByRole('button',{name:/^Add meal/}).click();await (await stockDone).finished();
 sold=true;await page.evaluate(()=>window.dispatchEvent(new Event('online')));
 await builder.getByText(/Currently unavailable/).waitFor();
 releaseQuote();
 await builder.getByRole('alert').filter({hasText:'cannot be prepared for that pickup'}).waitFor();
 assert.equal(exactChecks,2,'the newer stock result triggers a fresh exact check');
 assert.equal((await cart()).meals?.length??0,0);
 await builder.getByRole('button',{name:'Close',exact:true}).click();
 await builder.waitFor({state:'hidden'});
 const clear=page.getByRole('button',{name:'Clear cart',exact:true});
 const clearDialog=page.getByRole('dialog',{name:'Clear cart?',exact:true});
 const confirm=clearDialog.getByRole('button',{name:'Yes, clear cart',exact:true});
 // The storage write can be visible before this tab processes the storage event.
 const staleOpen=await page.evaluate(()=>{
  const key='gokul-cart',oldValue=localStorage.getItem(key);
  const replacement=JSON.parse(oldValue);replacement.branchId=2;replacement.items[0].quantity=4;
  const newValue=JSON.stringify(replacement);localStorage.setItem(key,newValue);
  document.querySelector('.floating-cart-delete').click();
  window.dispatchEvent(new StorageEvent('storage',{key,oldValue,newValue}));
  return {oldValue,newValue};
 });
 await clearDialog.getByRole('alert').filter({hasText:'Your cart changed'}).waitFor();
 assert.equal(await confirm.isDisabled(),true,'a stale control cannot ask to delete the replacement cart');
 assert.equal(await page.evaluate(()=>localStorage.getItem('gokul-cart')),staleOpen.newValue);
 await clearDialog.getByRole('button',{name:'No, keep cart',exact:true}).click();
 await clearDialog.waitFor({state:'hidden'});
 await page.evaluate(value=>{
  localStorage.setItem('gokul-cart',value);
  window.dispatchEvent(new StorageEvent('storage',{key:'gokul-cart',newValue:value}));
 },staleOpen.oldValue);
 await clear.click();await clearDialog.waitFor();
 const changed=await page.evaluate(()=>{
  const key='gokul-cart',oldValue=localStorage.getItem(key);
  const cart=JSON.parse(oldValue);cart.branchId=2;cart.items[0].quantity=4;
  const newValue=JSON.stringify(cart);localStorage.setItem(key,newValue);
  window.dispatchEvent(new StorageEvent('storage',{key,oldValue,newValue}));
  return newValue;
 });
 await confirm.click();
 await clearDialog.getByRole('alert').filter({hasText:'Your cart changed'}).waitFor();
 assert.equal(await confirm.isDisabled(),true,'the old confirmation cannot be used again');
 assert.equal(await page.evaluate(()=>localStorage.getItem('gokul-cart')),changed);
 await clearDialog.getByRole('button',{name:'No, keep cart',exact:true}).click();
 await clearDialog.waitFor({state:'hidden'});
 await clear.click();await confirm.click();
 await page.locator('.gokul-floating-cart').waitFor({state:'hidden'});
 assert.equal((await cart()).items.length,0,'a fresh confirmation may clear the current cart');
 // Replacing a cart requires consent for the specific cart, not a later version.
 await page.evaluate(product=>{
  localStorage.setItem('gokul-cart',JSON.stringify({branchId:2,items:[{product,quantity:1,weightGrams:null}]}));
  window.dispatchEvent(new StorageEvent('storage',{key:'gokul-cart'}));
 },products[0]);
 await clear.waitFor();
 const add=dish.getByRole('button',{name:'Add Last Laddu to cart',exact:true});
 await add.waitFor();
 const branchReplacement=await add.evaluate(button=>{
  const key='gokul-cart',before=localStorage.getItem(key);
  const updated=JSON.parse(before);updated.items[0].quantity=3;
  const latest=JSON.stringify(updated),originalConfirm=window.confirm;
  let asked=false;
  window.confirm=()=>{asked=true;localStorage.setItem(key,latest);return true;};
  try{button.click();}finally{window.confirm=originalConfirm;}
  window.dispatchEvent(new StorageEvent('storage',{key,oldValue:before,newValue:latest}));
  return {asked,latest};
 });
 assert.equal(branchReplacement.asked,true);
 await page.getByText('Your cart changed. Review it and try again.',{exact:true}).waitFor();
 assert.equal(await page.evaluate(()=>localStorage.getItem('gokul-cart')),branchReplacement.latest);
 page.once('dialog',dialog=>dialog.accept());
 await add.click();
 await page.waitForFunction(()=>JSON.parse(localStorage.getItem('gokul-cart'))?.branchId===1);
 // The desktop weight editor must keep the cart it was opened for, even after a rerender.
 await page.setViewportSize({width:1280,height:900});
 await page.evaluate(product=>{
  localStorage.setItem('gokul-cart',JSON.stringify({branchId:1,items:[{product,quantity:1,weightGrams:250}]}));
 },products[1]);
 await page.goto(base+'/cart');
 await page.getByRole('button',{name:/^Change Fresh Peda quantity/}).click();
 const editWeight=page.getByRole('dialog',{name:'Fresh Peda',exact:true});
 const replacement=await page.evaluate(()=>{
  const key='gokul-cart',oldValue=localStorage.getItem(key);
  const next=JSON.parse(oldValue);next.items[0].weightGrams=500;
  const newValue=JSON.stringify(next);localStorage.setItem(key,newValue);
  window.dispatchEvent(new StorageEvent('storage',{key,oldValue,newValue}));
  return newValue;
 });
 await page.getByRole('button',{name:/^Change Fresh Peda quantity.*500/}).waitFor({state:'attached'});
 await editWeight.getByRole('button',{name:/^(Add |Update quantity)/}).click();
 await page.getByRole('alert').filter({hasText:'Your cart changed. Review it and try again.'}).waitFor();
 assert.equal(await page.evaluate(()=>localStorage.getItem('gokul-cart')),replacement);
 await context.close();
 console.log('PASS local stock cap, price reconfirmation, invalidated meal stock, and stale clear, branch switch and weight protection');
}finally{await browser.close();}
