import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {mkdir} from 'node:fs/promises';
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
 for(const width of [390,1280]){
  const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:'block'}),page=await context.newPage();page.setDefaultTimeout(15000);
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
   else if(path==='/api/customer/identity/orders/page'){activeReads++;assert.equal(url.searchParams.get('limit'),'1');assert.equal(url.searchParams.get('status'),'ACTIVE');json={orders:[{orderNumber:'PRIVATE-ORDER',customerOrderNumber:10,orderStatus:'PREPARING',branchId:2,branchName:orderBranch.name,fulfillmentType:'PICKUP',pickupDate:date,pickupStartTime:'18:00:00',pickupEndTime:'19:00:00',createdAt:new Date().toISOString(),updatedAt:new Date().toISOString(),totalAmount:40}],nextBefore:null};}
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
  await page.goto(`${base}/menu`);await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});
  const active=page.getByRole('complementary',{name:'Active order'});await active.getByRole('heading',{name:'Preparing',exact:true}).waitFor();
  assert.equal(await active.getByRole('link',{name:'Call',exact:true}).getAttribute('href'),'tel:9999999999');
  assert.match(await active.getByRole('link',{name:'Directions',exact:true}).getAttribute('href'),/Order%20road/);
  assert.match(await active.getByRole('link',{name:'View pickup code',exact:true}).getAttribute('href'),/#pickup-code$/);
  assert.equal(await active.getByText('PRIVATE-ORDER',{exact:true}).count(),0);
  const input=page.locator('#gokul-menu-search');await input.fill('panner');await page.getByRole('heading',{name:width===390?'Paneer Burger':'Paneer Burger Half',exact:true}).waitFor();assert.equal(await page.getByRole('heading',{name:'Fresh Peda',exact:true}).count(),0);
  if(width===390){
   await page.locator('.menu-browse-chips').getByRole('button',{name:'Browse all item categories',exact:true}).click();
   const filters=page.getByRole('dialog',{name:'Filter menu',exact:true});await filters.getByLabel('Maximum price',{exact:true}).fill('100');await filters.getByRole('button',{name:'Show items',exact:true}).click();
   const chip=page.locator('.menu-active-filters').getByRole('button',{name:/Up to/});await chip.waitFor();await chip.click();assert.equal(await page.locator('.menu-active-filters').count(),0);
   await page.getByRole('button',{name:'Choose options for Paneer Burger',exact:true}).click();const variants=page.getByRole('dialog',{name:'Paneer Burger',exact:true});
   await variants.getByRole('button',{name:'Choose another pickup',exact:true}).click();const picker=page.getByRole('dialog',{name:'Choose pickup date & time',exact:true});await picker.waitFor();assert.equal(await variants.count(),0);
   const cart=await page.evaluate(()=>localStorage.getItem('gokul-cart'));
   assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-selected-pickup-slot')).date),date);
   await picker.getByRole('button',{name:later,exact:true}).click();await picker.getByRole('button',{name:'18:00–19:00 Standard',exact:true}).click();
   assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-selected-pickup-slot')).date),date,'draft time must not save silently');
   await picker.getByRole('button',{name:'Use this pickup',exact:true}).click();await picker.waitFor({state:'hidden'});
   assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-selected-pickup-slot')).date),later);assert.equal(await page.evaluate(()=>localStorage.getItem('gokul-cart')),cart);
   assert.ok(checks.some(c=>c.days>1&&c.items?.some(i=>i.productId===1)&&c.items?.some(i=>i.productId===2)),'prospective variant and actual cart searched together');
   await page.evaluate(()=>{localStorage.setItem('gokul-language','hi');window.dispatchEvent(new Event('storage'));});await page.getByRole('button',{name:'सभी आइटम श्रेणियाँ देखें',exact:true}).waitFor();await input.fill('पनीर');await page.getByRole('heading',{name:'Paneer Burger',exact:true}).waitFor();await active.getByRole('link',{name:'पिकअप कोड देखें',exact:true}).waitFor();
   await page.evaluate(()=>{localStorage.setItem('gokul-language','en');window.dispatchEvent(new Event('storage'));});
  }
  assert.ok(activeReads<=2,'active order must not trigger repeated reads for menu interaction');
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
  await input.fill('panner');
  const savedCart=await page.evaluate(()=>localStorage.getItem('gokul-cart'));
  await page.goto(`${base}/about`);await page.goto(`${base}/menu`);
  await page.waitForFunction(()=>document.querySelector('#gokul-menu-search')?.value==='panner');
  assert.equal(await page.evaluate(()=>localStorage.getItem('gokul-cart')),savedCart);
  // A new branch must not inherit the old branch's search/filter preferences.
  await page.evaluate(branch=>localStorage.setItem('gokul-selected-branch',JSON.stringify(branch)),orderBranch);
  await page.goto(`${base}/menu`);await page.waitForFunction(()=>document.querySelector('#gokul-menu-search')?.value==='');
  await page.evaluate(branch=>localStorage.setItem('gokul-selected-branch',JSON.stringify(branch)),branch);
  await page.goto(`${base}/menu`);await page.waitForFunction(()=>document.querySelector('#gokul-menu-search')?.value==='panner');
  if(width===1280){
   const cartBefore=await page.evaluate(()=>localStorage.getItem('gokul-cart'));
   await page.goto(`${base}/checkout/pickup`);const plan=page.getByRole('complementary',{name:'Your pickup plan',exact:true});await plan.waitFor();
   await plan.getByRole('link',{name:'Edit items',exact:true}).waitFor();assert.equal(await plan.getByRole('link',{name:'Call branch',exact:true}).getAttribute('href'),'tel:1111111111');
   assert.equal(await page.evaluate(()=>localStorage.getItem('gokul-cart')),cartBefore);
   if(process.env.SCREENSHOT_DIR)await page.screenshot({path:`${process.env.SCREENSHOT_DIR}/desktop-pickup-plan.png`});
   await page.goto(`${base}/menu`);await page.waitForFunction(()=>document.querySelector('#gokul-menu-search')?.value==='panner');
  }
  await input.fill('');await page.evaluate(()=>scrollTo({top:0,behavior:'instant'}));
  if(process.env.SCREENSHOT_DIR){await mkdir(process.env.SCREENSHOT_DIR,{recursive:true});await page.screenshot({path:`${process.env.SCREENSHOT_DIR}/customer-ease-menu-${width}.png`});}
  if(width===390){
   failSettings=true;await page.evaluate(()=>sessionStorage.removeItem('gokul-storefront-settings'));await page.goto(`${base}/checkout/mobile`);await page.getByRole('heading',{name:'Your checkout is saved',exact:true}).waitFor();
   const cart=await page.evaluate(()=>localStorage.getItem('gokul-cart'));failSettings=false;await page.getByRole('button',{name:'Try again',exact:true}).click();await page.getByRole('heading',{name:'Your order',exact:true}).waitFor();assert.equal(await page.evaluate(()=>localStorage.getItem('gokul-cart')),cart);
  }
  await page.goto(`${base}/offline`);await context.setOffline(true);
  await page.getByRole('heading',{name:'You’re offline',exact:true}).waitFor();assert.equal(await page.getByRole('button',{name:'Try again',exact:true}).isDisabled(),true);
  await context.setOffline(false);await page.getByRole('heading',{name:'You’re connected again',exact:true}).waitFor();
  await page.goto(`${base}/missing-customer-page`);await page.getByRole('heading',{name:'This page has moved',exact:true}).waitFor();await page.getByRole('link',{name:'Back to menu',exact:true}).waitFor();
  assert.deepEqual(errors,[]);await context.close();console.log(`Customer recovery, search, filters, pickup and order shortcut ${width}px passed`);
 }
}finally{await browser.close();}
