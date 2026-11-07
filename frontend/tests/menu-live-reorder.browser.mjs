import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
const browser=await chromium.launch({headless:true});
const date=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata'}).format(new Date(Date.now()+86400000));
const branch={id:1,name:'Test branch',active:true,operational:true,pickupAvailable:true};
const products=Array.from({length:7},(_,i)=>({id:i+1,name:`Sweet ${i+1}`,categoryId:1,categoryName:'Sweets',price:15,available:true,saleMode:'UNIT'}));
const starters=Array.from({length:4},(_,i)=>({id:8+i,name:`Starter ${i+1}`,categoryId:2,categoryName:'Starters',price:20,available:true,saleMode:'UNIT'}));
const meal={id:1,branchId:1,version:1,title:'Sweet Box',basePrice:0,pricingMode:'SELECTED_ITEMS',discountType:'PERCENT',discountValue:2,active:true,
 groups:[{key:'sweet',title:'Choose a sweet',minimum:1,maximum:1,options:[{key:'sweet-five',productId:5,quantity:1,weightGrams:null,upgrade:0}]}]};
const slot={id:1,branchId:1,slotDate:date,startTime:'18:00:00',endTime:'19:00:00',active:true,remainingCapacity:20,priorityEnabled:false};
try{
 const context=await browser.newContext({viewport:{width:390,height:900},serviceWorkers:'block',reducedMotion:'reduce'});
 const page=await context.newPage();page.setDefaultTimeout(15000);
 let blocked=false,startersBlocked=false,stockFails=false,addChecks=0,holdBasket=false,holdRecommendations=true,releaseBasket,startedBasket;
 let basketGate=new Promise(resolve=>releaseBasket=resolve);
 let basketStarted=new Promise(resolve=>startedBasket=resolve);
 const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Methods':'GET,POST,OPTIONS','Access-Control-Allow-Headers':'content-type'};
 await context.route('**/api/**',async route=>{
  const request=route.request(),path=new URL(request.url()).pathname;
  if(request.method()==='OPTIONS')return route.fulfill({status:204,headers});
  let json=[];
  if(path==='/api/storefront/features')json={futuristicStorefrontV2:true,checkoutExperienceV2:true,contextualStorefrontV2:true,smartAvailability:true,pickupAddOns:true,today:date,futureOrderingDays:30};
  else if(path==='/api/branches')json=[branch];
  else if(path==='/api/branches/1')json=branch;
  else if(path==='/api/menu')json=[{id:2,name:'Starters',products:starters},{id:1,name:'Sweets',products}];
  else if(path==='/api/branches/1/meals')json=[meal];
  else if(path==='/api/menu/portion-groups')json={groups:[]};
  else if(path==='/api/menu/appearance')json={banners:[],categories:[],pairings:[{seedProductId:1,suggestedProductId:2}]};
  else if(path==='/api/menu/pickup-addons/check'){addChecks++;json={orderable:!blocked};}
  else if(path==='/api/menu/pickup-addons'){
   if(holdBasket&&holdRecommendations)await basketGate;
   json=[{slotVerified:true,product:products[1],weightGrams:null,portionPrice:15,portionTotal:15,reason:'Often ordered with Sweet 1'}];
  }
  else if(path.endsWith('/availability')){
   if(stockFails)return route.fulfill({status:503,json:{message:'Unavailable'},headers});
   if(holdBasket){startedBasket();await basketGate;}
   const issues=blocked?[{productId:1,available:false,code:'NO_INVENTORY'}]:[];
   json={today:date,maximumDate:date,dates:[{date,available:!blocked,items:[...products,...starters].map(p=>({productId:p.id,available:!(startersBlocked&&p.categoryId===2)})),slots:[{slot,normalAvailable:!blocked,priorityAvailable:false,issues}]}]};
  }
  await route.fulfill({json,headers}).catch(()=>{});
 });
 await context.addInitScript(({branch,slot,date,product})=>{
  localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));
  localStorage.setItem('gokul-selected-pickup-slot',JSON.stringify({date,slot,pickupType:'NORMAL'}));
  localStorage.setItem('gokul-pickup-intent',JSON.stringify({branchId:branch.id,date}));
  localStorage.setItem('gokul-cart',JSON.stringify({branchId:branch.id,items:[{product,quantity:1}]}));
  localStorage.setItem('gokul-ordering-tour:v1','seen');
 },{branch,slot,date,product:products[0]});
 await page.goto(base+'/menu');await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});
 const addPairing=page.getByRole('button',{name:'Add Sweet 2 from pairings',exact:true});
 const pairingEnabled=()=>page.waitForFunction(()=>document.querySelector('button[aria-label="Add Sweet 2 from pairings"]')?.disabled===false);
 await pairingEnabled();
 stockFails=true;await page.evaluate(()=>window.dispatchEvent(new Event('online')));
 const retry=page.locator('.menu-pickup-refresh-error').getByRole('button',{name:'Retry availability',exact:true});
 await retry.waitFor();
 assert.equal(await addPairing.isDisabled(),true,'an older slot-verified pairing cannot enable Add after the stock check fails');
 stockFails=false;await retry.click();await pairingEnabled();
 const card=page.locator('#gokul-product-5'),node=await card.elementHandle();
 await card.getByRole('button',{name:'View details · Sweet 5',exact:true}).click();
 const details=page.getByRole('dialog',{name:'Sweet 5',exact:true});await details.waitFor();
 blocked=true;await page.evaluate(()=>window.dispatchEvent(new Event('online')));
 await page.waitForFunction(()=>document.querySelector('#gokul-product-5').compareDocumentPosition(document.querySelector('#gokul-product-1'))&Node.DOCUMENT_POSITION_FOLLOWING);
 assert.equal(await details.isVisible(),true,'reordering across the fourth row keeps details open');
 assert.equal(await node.evaluate(card=>card.isConnected),true,'reordering keeps the original card');
 await details.getByRole('button',{name:'Close',exact:true}).click();await details.waitFor({state:'hidden'});
 assert.equal(await card.getByRole('button',{name:'Add Sweet 5 to cart',exact:true}).isEnabled(),true);
 await addPairing.waitFor({state:'hidden'});
 assert.equal(addChecks,0,'the known basket conflict is handled before Add');
 blocked=false;await page.evaluate(()=>window.dispatchEvent(new Event('online')));
 await pairingEnabled();
 const first=page.locator('#gokul-product-1');
 await first.getByRole('button',{name:'View details · Sweet 1',exact:true}).click();
 const firstDetails=page.getByRole('dialog',{name:'Sweet 1',exact:true});await firstDetails.waitFor();
 blocked=true;await page.evaluate(()=>window.dispatchEvent(new Event('online')));
 await page.waitForFunction(()=>document.querySelector('#gokul-product-5').compareDocumentPosition(document.querySelector('#gokul-product-1'))&Node.DOCUMENT_POSITION_FOLLOWING);
 assert.equal(await firstDetails.evaluate(dialog=>dialog.matches(':modal')),true,'moving the open card itself preserves its native modal');
 await firstDetails.getByRole('button',{name:'Close',exact:true}).click();await firstDetails.waitFor({state:'hidden'});
 blocked=false;await page.evaluate(()=>window.dispatchEvent(new Event('online')));
 await pairingEnabled();
 holdBasket=true;
 const seedIncrease=page.getByRole('button',{name:'Add one more Sweet 1',exact:true});
 await seedIncrease.click();await basketStarted;
 assert.equal(await addPairing.isDisabled(),true,'the previous result cannot enable Add after the basket changes');
 assert.equal(addChecks,0,'no add request is sent during the recheck');
 holdBasket=false;releaseBasket();
 await page.waitForFunction(()=>Array.from(document.querySelectorAll('button[aria-label="Add Sweet 2 from pairings"]')).some(button=>!button.disabled));
 await addPairing.click();
 const removePairing=page.locator('.mobile-menu-pairings-inline').getByRole('button',{name:'Remove one Sweet 2',exact:true});
 await removePairing.waitFor();
 const increasePairing=page.locator('.mobile-menu-pairings-inline').getByRole('button',{name:'Add one more Sweet 2',exact:true});
 await page.waitForFunction(()=>document.querySelector('.mobile-menu-pairings-inline button[aria-label="Add one more Sweet 2"]')?.disabled===false);
 basketGate=new Promise(resolve=>releaseBasket=resolve);
 basketStarted=new Promise(resolve=>startedBasket=resolve);
 holdBasket=true;holdRecommendations=false;
 const refreshedRecommendations=page.waitForResponse(response=>new URL(response.url()).pathname==='/api/menu/pickup-addons'
  && response.request().postDataJSON()?.items?.some(item=>item.productId===1&&item.quantity===3));
 await seedIncrease.click();await basketStarted;await refreshedRecommendations;
 assert.equal(await increasePairing.isDisabled(),true,'a retained pairing also blocks quantity increases');
 assert.equal(await removePairing.isEnabled(),true,'the basket can still be edited while verifying');
 holdBasket=false;releaseBasket();

 const shelf=page.locator('#menu-meals');
 assert.equal(await shelf.evaluate(node=>node.closest('section[id^="menu-category-"]')?.id),'menu-category-2');
 await shelf.getByRole('button',{name:'Build meal Sweet Box',exact:true}).click();
 const builder=page.getByRole('dialog',{name:'Sweet Box',exact:true});await builder.waitFor();
 await builder.getByRole('radio').check();
 const builderNode=await builder.elementHandle();
 startersBlocked=true;await page.evaluate(()=>window.dispatchEvent(new Event('online')));
 await page.waitForFunction(()=>document.querySelector('#menu-meals')?.closest('section[id^="menu-category-"]')?.id==='menu-category-1');
 assert.equal(await builderNode.evaluate(node=>node.isConnected&&node.matches(':modal')),true,'the original meal dialog survives the category move');
 assert.equal(await builder.getByRole('radio').isChecked(),true,'the selected meal option is preserved');
 assert.equal(await builder.getByRole('button',{name:/^Add meal/}).isEnabled(),true);
 await builder.getByRole('button',{name:'Close',exact:true}).click();await builder.waitFor({state:'hidden'});
 await page.waitForFunction(()=>document.activeElement?.id==='menu-meal-1');
 await context.close();
 console.log('Stock sorting preserves open dishes and meal drafts; pairings recheck a changed basket before Add.');
}finally{await browser.close();}
