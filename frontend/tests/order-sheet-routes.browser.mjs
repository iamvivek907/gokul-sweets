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
  const errors=[],checks=[];let failSettings=false;
  page.on('pageerror',e=>errors.push(e.message));
  const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Methods':'GET,POST,PUT,OPTIONS','Access-Control-Allow-Headers':'content-type,idempotency-key'};
  await context.route('**/api/**',async route=>{
   const req=route.request(),url=new URL(req.url()),path=url.pathname;let json=[];
   if(req.method()==='OPTIONS')return route.fulfill({status:204,headers});
   if(path==='/api/storefront/features'){
    if(failSettings)return route.fulfill({status:503,json:{message:'Temporary outage'},headers});
    json={preHomeIntentGateway:true,branchExperience:true,futuristicStorefrontV2:true,checkoutExperienceV2:true,contextualStorefrontV2:true,simplifiedCheckout:true,acceptedCheckoutQuote:true,smartAvailability:true,smartPickupSelection:true,truthfulOrderTracking:true,today:date,futureOrderingDays:7};
   }else if(path==='/api/branches')json=[branch,orderBranch];
   else if(path==='/api/branches/1')json=branch;else if(path==='/api/branches/2')json=orderBranch;
   else if(path==='/api/menu')json=[{id:1,name:'Snacks',products:products.slice(0,2)},{id:2,name:'Sweets',products:products.slice(2)}];
   else if(path==='/api/menu/portion-groups')json={groups:[{key:'burger',title:'Paneer Burger',choices:[{productId:1,label:'Half'},{productId:2,label:'Full'}]}]};
   else if(path==='/api/storefront/customer-identity')json={enabled:true,guestCheckoutEnabled:false};
   else if(path==='/api/customer/identity/me')json={authenticated:true,name:'Customer',phone:'+919876543210'};
   else if(path==='/api/customer/identity/orders/page' && url.searchParams.get('status')==='COMPLETED'){assert.equal(url.searchParams.get('limit'),'1');json={orders:[],nextBefore:null};}
   else if(path==='/api/customer/identity/orders/page'){assert.equal(url.searchParams.get('limit'),'20');assert.equal(url.searchParams.get('status'),'ACTIVE_QUEUE');json={orders:[{orderNumber:'PRIVATE-ORDER',customerOrderNumber:10,orderStatus:'PREPARING',branchId:2,branchName:orderBranch.name,fulfillmentType:'PICKUP',pickupDate:date,pickupStartTime:'18:00:00',pickupEndTime:'19:00:00',createdAt:new Date().toISOString(),updatedAt:new Date().toISOString(),totalAmount:40},{orderNumber:'LATE-ORDER',customerOrderNumber:12,orderStatus:'CONFIRMED',branchId:1,branchName:branch.name,fulfillmentType:'PICKUP',pickupDate:later,pickupStartTime:'19:00:00',pickupEndTime:'19:30:00'},{orderNumber:'EARLY-ORDER',customerOrderNumber:11,orderStatus:'CONFIRMED',branchId:1,branchName:branch.name,fulfillmentType:'PICKUP',pickupDate:date,pickupStartTime:'14:00:00',pickupEndTime:'14:30:00'}],nextBefore:null};}
   else if(path==='/api/orders/mobile-preview')json={quote:{token:'signed',subtotal:'40',taxAmount:'0',priorityCharge:'0',convenienceFee:'0',totalAmount:'40',currency:'INR',items:[],expiresAt:new Date(Date.now()+600000).toISOString()},offers:[],paymentFee:0,paymentFeeTax:0};
   else if(path.endsWith('/availability')){
    const payload=req.postDataJSON();checks.push(payload);
    const values=payload.days>1?[date,later]:[payload.startDate];
    json={today:date,maximumDate:later,dates:values.map(value=>{const issues=(payload.items??[]).filter(i=>i.productId===2&&value===date).map(i=>({productId:i.productId,available:false,reason:'Unavailable for selected pickup'}));return {date:value,available:!issues.length,items:products.map(p=>({productId:p.id,available:!(p.id===2&&value===date),reason:p.id===2&&value===date?'Unavailable for selected pickup':null})),slots:[{slot:slot(value),normalAvailable:!issues.length,issues}]};})};
   }else if(path.endsWith('/pickup-discovery'))json={today:date,maximumDate:later,dates:[date,later].map(value=>({date:value,slots:[{slot:slot(value),normalAvailable:true,issues:[]}]}))};
   if(path==='/api/customer/identity/orders/page'&&url.searchParams.get('status')==='ACTIVE_QUEUE')json.orders=Array.from({length:20},(_,i)=>({...json.orders[i%3],orderNumber:`ORDER-${i}`,customerOrderNumber:i+1}));
  await route.fulfill({headers,json});
  });
  await context.addInitScript(({branch,original,products})=>{
   if(!sessionStorage.getItem('customer-ease-initialized')){localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));sessionStorage.setItem('customer-ease-initialized','true');}localStorage.setItem('gokul-ordering-tour:v1','seen');localStorage.setItem('gokul-social-follow-popup-seen','true');
   if(!localStorage.getItem('gokul-cart'))localStorage.setItem('gokul-cart',JSON.stringify({branchId:1,items:[{product:products[0],quantity:1,weightGrams:null}]}));
   if(!localStorage.getItem('gokul-selected-pickup-slot'))localStorage.setItem('gokul-selected-pickup-slot',JSON.stringify(original));
  },{branch,original,products});
  for(const path of ['/','/branches/1','/menu']){
   await page.goto(base+path);await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});
   const tab=page.getByRole('complementary',{name:'Active order'});await tab.getByRole('button').click();
   const sheet=page.getByRole('dialog',{name:'Your upcoming orders',exact:true}),close=sheet.getByRole('button',{name:'Close',exact:true});await close.waitFor();await sheet.evaluate(async n=>{await Promise.all(n.getAnimations({subtree:true}).map(a=>a.finished.catch(()=>{})));});
   const box=await close.boundingBox(),header=await sheet.locator('header').boundingBox(),frame=await sheet.locator('header').evaluate(n=>{const r=n.parentElement.getBoundingClientRect();return {x:r.x,width:r.width};});
   assert.ok(box.width>=44&&box.height>=44&&box.y>=0&&box.y+box.height<header.y,'Close is fully visible above the order surface');
   assert.ok(Math.abs(box.x+box.width/2-(frame.x+frame.width/2))<2,'Close stays centred on every route');
   assert.equal(await sheet.evaluate(n=>getComputedStyle(n).backgroundColor),'rgba(0, 0, 0, 0)');
   const status=sheet.locator('.active-order-list article>button>span:last-child').first();assert.equal(await status.evaluate(n=>getComputedStyle(n).overflowWrap),'normal','status words never break into individual letters');
   assert.equal(await sheet.getByRole('button',{name:'Hide reminder for this visit'}).evaluate(n=>getComputedStyle(n).color),'rgb(143, 24, 56)','reminder footer retains the brand colour');
   const body=sheet.locator('.mobile-menu-suggestion-body');await body.focus();await page.keyboard.press('End');await page.waitForFunction(()=>document.querySelector('.active-order-list')?.parentElement.scrollTop>0);
   assert.ok((await close.boundingBox()).y>=0,'scrolling many future orders cannot hide Close');
   if(process.env.SCREENSHOT_DIR)await page.screenshot({path:`${process.env.SCREENSHOT_DIR}/order-close-${path==='/'?'home':path.includes('branches')?'branch':'menu'}-${width}.png`});
   await close.click();await sheet.waitFor({state:'hidden'});assert.equal(await tab.getByRole('button').evaluate(n=>document.activeElement===n),true);
   await tab.getByRole('button').click();await sheet.waitFor();await page.keyboard.press('Escape');await sheet.waitFor({state:'hidden'});
  }
  assert.deepEqual(errors,[]);await context.close();console.log(`PASS Home, branch Home and Menu order-sheet close ${width}px`);
 }
}finally{await browser.close();}
