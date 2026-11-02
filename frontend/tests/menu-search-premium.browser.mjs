import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const base=process.env.BROWSER_BASE??'http://127.0.0.1:3311',browser=await chromium.launch({headless:true});
const date=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata'}).format(new Date(Date.now()+86400000));
const later=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata'}).format(new Date(Date.now()+172800000));
const branch={id:1,name:'Menu branch',active:true,operational:true,address:'Menu road',phone:'1111111111',pickupAvailable:true};
const orderBranch={...branch,id:2,name:'Order branch',address:'Order road',phone:'9999999999'};
const products=[{id:1,name:'Paneer Burger Half',categoryId:1,categoryName:'Snacks',price:40,saleMode:'UNIT',available:true,imageUrl:null},{id:2,name:'Paneer Burger Full',categoryId:1,categoryName:'Snacks',price:80,saleMode:'UNIT',available:true,imageUrl:null},{id:3,name:'Fresh Peda',categoryId:2,categoryName:'Sweets',price:500,saleMode:'WEIGHT',minimumWeightGrams:250,weightStepGrams:50,available:true,imageUrl:null}];
const slot=(value)=>({id:value===date?1:2,branchId:1,slotDate:value,startTime:'18:00:00',endTime:'19:00:00',active:true,remainingCapacity:20,priorityEnabled:false});
const original={date,slot:slot(date),pickupType:'NORMAL'};
try{
 for(const width of [320,390,640,1280]){
  const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:'block',reducedMotion:width===640?'reduce':'no-preference',hasTouch:true}),page=await context.newPage();page.setDefaultTimeout(15000);
  const errors=[],checks=[];let failSettings=false,activeReads=0;
  page.on('pageerror',e=>errors.push(e.message));
  const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Methods':'GET,POST,PUT,OPTIONS','Access-Control-Allow-Headers':'content-type,idempotency-key'};
  await context.route('**/api/**',async route=>{
   const req=route.request(),url=new URL(req.url()),path=url.pathname;let json=[];
   if(req.method()==='OPTIONS')return route.fulfill({status:204,headers});
   if(path==='/api/storefront/features'){
    if(failSettings)return route.fulfill({status:503,json:{message:'Temporary outage'},headers});
    json={branchExperience:true,futuristicStorefrontV2:true,checkoutExperienceV2:true,contextualStorefrontV2:true,simplifiedCheckout:true,acceptedCheckoutQuote:true,smartAvailability:true,smartPickupSelection:true,truthfulOrderTracking:true,today:date,futureOrderingDays:7};
   }else if(path==='/api/branches')json=[branch,orderBranch];
   else if(path==='/api/branches/1')json=branch;else if(path==='/api/branches/2')json=orderBranch;
   else if(path==='/api/menu')json=[{id:1,name:'Snacks',products:products.slice(0,2)},{id:2,name:'Sweets',products:products.slice(2)}];
   else if(path==='/api/menu/portion-groups')json={groups:[{key:'burger',title:'Paneer Burger',choices:[{productId:1,label:'Half'},{productId:2,label:'Full'}]}]};
   else if(path==='/api/storefront/customer-identity')json={enabled:true,guestCheckoutEnabled:false};
   else if(path==='/api/customer/identity/me')json={authenticated:true,name:'Customer',phone:'+919876543210'};
   else if(path==='/api/customer/identity/orders/page' && url.searchParams.get('status')==='COMPLETED'){assert.equal(url.searchParams.get('limit'),'1');json={orders:[],nextBefore:null};}
   else if(path==='/api/customer/identity/orders/page'){activeReads++;assert.equal(url.searchParams.get('limit'),'20');assert.equal(url.searchParams.get('status'),'ACTIVE_QUEUE');json={orders:[{orderNumber:'PRIVATE-ORDER',customerOrderNumber:10,orderStatus:'PREPARING',branchId:2,branchName:orderBranch.name,fulfillmentType:'PICKUP',pickupDate:date,pickupStartTime:'18:00:00',pickupEndTime:'19:00:00',createdAt:new Date().toISOString(),updatedAt:new Date().toISOString(),totalAmount:40},{orderNumber:'LATE-ORDER',customerOrderNumber:12,orderStatus:'CONFIRMED',branchId:1,branchName:branch.name,fulfillmentType:'PICKUP',pickupDate:later,pickupStartTime:'19:00:00',pickupEndTime:'19:30:00'},{orderNumber:'EARLY-ORDER',customerOrderNumber:11,orderStatus:'CONFIRMED',branchId:1,branchName:branch.name,fulfillmentType:'PICKUP',pickupDate:date,pickupStartTime:'14:00:00',pickupEndTime:'14:30:00'}],nextBefore:null};}
   else if(path==='/api/orders/mobile-preview')json={quote:{token:'signed',subtotal:'40',taxAmount:'0',priorityCharge:'0',convenienceFee:'0',totalAmount:'40',currency:'INR',items:[],expiresAt:new Date(Date.now()+600000).toISOString()},offers:[],paymentFee:0,paymentFeeTax:0};
   else if(path.endsWith('/availability')){
    const payload=req.postDataJSON();checks.push(payload);
    const values=payload.days>1?[date,later]:[payload.startDate];
    json={today:date,maximumDate:later,dates:values.map(value=>{const issues=(payload.items??[]).filter(i=>i.productId===2&&value===date).map(i=>({productId:i.productId,available:false,reason:'Unavailable for selected pickup'}));return {date:value,available:!issues.length,items:products.map(p=>({productId:p.id,available:!(p.id===2&&value===date),reason:p.id===2&&value===date?'Unavailable for selected pickup':null})),slots:[{slot:slot(value),normalAvailable:!issues.length,issues}]};})};
   }else if(path.endsWith('/pickup-discovery'))json={today:date,maximumDate:later,dates:[date,later].map(value=>({date:value,slots:[{slot:slot(value),normalAvailable:true,issues:[]}]}))};
   await route.fulfill({headers,json});
  });
  await context.addInitScript(({branch,original,products})=>{
   if(!sessionStorage.getItem('customer-ease-initialized')){localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));sessionStorage.setItem('customer-ease-initialized','true');}localStorage.setItem('gokul-ordering-tour:v1','seen');localStorage.setItem('gokul-social-follow-popup-seen','true');
   if(!localStorage.getItem('gokul-cart'))localStorage.setItem('gokul-cart',JSON.stringify({branchId:1,items:[{product:products[0],quantity:1,weightGrams:null}]}));
   if(!localStorage.getItem('gokul-selected-pickup-slot'))localStorage.setItem('gokul-selected-pickup-slot',JSON.stringify(original));
  },{branch,original,products});
  await page.clock.install();await page.goto(`${base}/menu`);await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});
  const input=page.locator('#gokul-menu-search'),example=page.locator('.menu-search-example-word').first();
  await input.waitFor();assert.equal(await page.locator('.menu-search-modern-icon').first().isVisible(),true);await example.waitFor();await page.waitForFunction(()=>!document.querySelector('#gokul-product-1 button[data-ordering-target="add"]')?.disabled);
  const initial=await example.innerText();await page.clock.fastForward(4200);
  if(width===640)assert.equal(await example.innerText(),initial,'reduced motion leaves a static example');
  else await page.waitForFunction(text=>document.querySelector('.menu-search-example-word')?.textContent!==text,initial);
  const cart=await page.evaluate(()=>localStorage.getItem('gokul-cart')),selectedBranch=await page.evaluate(()=>localStorage.getItem('gokul-selected-branch'));
  await input.focus();const focusedHint=await example.innerText();await page.clock.fastForward(4200);assert.equal(await example.innerText(),focusedHint,'focus pauses the example');
  await input.fill('Paneer');const list=page.getByRole('listbox',{name:'Menu suggestions',exact:true});await list.waitFor();
  assert.equal(await input.inputValue(),'Paneer');assert.equal(await page.locator('.menu-search-example').count(),0,'hints cannot cover typed text');
  const option=list.getByRole('option',{name:'Paneer Burger Half',exact:true});await option.waitFor();assert.equal(await option.locator('.menu-search-result-copy small').innerText(),'Snacks');assert.equal(await option.locator('mark').innerText(),'Paneer');
  await page.keyboard.press('ArrowDown');const active=await input.getAttribute('aria-activedescendant');assert.ok(active);assert.equal(await page.locator(`[id="${active}"]`).getAttribute('aria-selected'),'true');const chosen=await page.locator(`[id="${active}"]`).getAttribute('aria-label');await page.keyboard.press('Enter');
  assert.equal(await input.inputValue(),chosen);
  const clear=page.getByRole('button',{name:'Clear search',exact:true});const rect=await clear.boundingBox();assert.ok(rect.width>=44&&rect.height>=44);
  await clear.click();assert.equal(await input.inputValue(),'');assert.equal(await input.evaluate(n=>n===document.activeElement),true,'clear retains keyboard focus');
  await page.locator('#gokul-product-3').waitFor();
  await input.fill('zzzzzzzzz');await page.getByText('No matching items',{exact:true}).waitFor();assert.equal(await page.getByRole('listbox',{name:'Menu suggestions',exact:true}).count(),0);
  await clear.click();assert.equal(await input.inputValue(),'');assert.equal(await page.evaluate(()=>localStorage.getItem('gokul-cart')),cart);assert.equal(await page.evaluate(()=>localStorage.getItem('gokul-selected-branch')),selectedBranch);
  await input.fill('Paneer');await list.waitFor();if(process.env.SCREENSHOT_DIR)await page.screenshot({path:`${process.env.SCREENSHOT_DIR}/premium-search-${width}.png`});
  if(width===640){await page.evaluate(()=>{localStorage.setItem('gokul-language','hi');window.dispatchEvent(new Event('storage'));});await page.getByRole('button',{name:'खोज साफ़ करें',exact:true}).click();assert.equal(await input.inputValue(),'');assert.match(await page.locator('.menu-search-example').first().innerText(),/खोजें/);}
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);assert.deepEqual(errors,[]);await context.close();console.log(`PASS premium search examples, clear, suggestions and keyboard ${width}px`);
 }
}finally{await browser.close();}
