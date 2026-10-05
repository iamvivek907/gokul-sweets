import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const browser=await chromium.launch({headless:true}),base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
const branch={id:1,name:'Offer test branch',code:'TEST',address:'Main Road',active:true,operational:true,pickupAvailable:true};
const product={id:1,name:'Fresh meal',categoryId:1,categoryName:'Meals',price:100,available:true,saleMode:'UNIT'};
try { for(const scenario of ['guest-retry','resume','expiry','cadence','refresh-failure']) {
 const context=await browser.newContext({viewport:{width:390,height:900},serviceWorkers:'block'}),page=await context.newPage();
 const errors=[];page.on('pageerror',error=>errors.push(error.message));page.setDefaultTimeout(15000);
 let reads=0,available=true,fail=scenario==='guest-retry',hold=false,release;
 const gate=new Promise(resolve=>release=resolve);
 const today=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata'}).format(new Date());
 const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Methods':'GET,POST,OPTIONS','Access-Control-Allow-Headers':'content-type'};
 await context.route('**/api/**',async route=>{
  if(route.request().method()==='OPTIONS')return route.fulfill({status:204,headers});
  const path=new URL(route.request().url()).pathname;let json=[];
  if(path==='/api/storefront/features')json={futuristicStorefrontV2:true,checkoutExperienceV2:true,contextualStorefrontV2:true,simplifiedCheckout:true,acceptedCheckoutQuote:true,pickupAddOns:true,smartAvailability:false,reviews:true,today,futureOrderingDays:30};
  else if(path==='/api/branches')json=[branch];else if(path==='/api/branches/1')json=branch;
  else if(path==='/api/menu')json=[{id:1,name:'Meals',products:[product]}];
  else if(path==='/api/menu/portion-groups')json={groups:[]};
  else if(path==='/api/storefront/customer-identity')json={enabled:true,guestCheckoutEnabled:false};
  else if(path==='/api/customer/identity/me')json={authenticated:false};
  else if(path==='/api/menu/offers'){
   reads++;if(hold)await gate;
   if(fail)return route.fulfill({status:503,json:{message:'Temporarily unavailable'},headers}).catch(()=>{});
   // Return an expired entry too: the browser must reject it even if server time lags.
   json=available?[{rebateId:1,code:'SAVE20',name:'Public meal saving',description:null,rebateType:'FIXED_AMOUNT',rebateValue:20,minimumOrderAmount:200,maximumDiscountAmount:null,tiers:[],validUntil:new Date(Date.now()+(scenario==='expiry'?8000:3600000)).toISOString()}]:[];
  }
  await route.fulfill({json,headers}).catch(()=>{});
 });
 await context.addInitScript(branch=>localStorage.setItem('gokul-selected-branch',JSON.stringify(branch)),branch);
 if(scenario==='cadence')await page.clock.install();
 await page.goto(`${base}/menu`);await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});
 const strip=page.locator('#menu-offers-open');
 if(scenario==='guest-retry') {
  await strip.getByText('Offers could not be checked. Try again.',{exact:true}).waitFor();
  await strip.click();const sheet=page.getByRole('dialog',{name:'Offers & savings',exact:true});
  await sheet.getByRole('button',{name:'Retry offers',exact:true}).waitFor();fail=false;
  await sheet.getByRole('button',{name:'Retry offers',exact:true}).click();
  await sheet.getByText('Public meal saving',{exact:true}).waitFor();
  assert.equal(await sheet.getByRole('button',{name:'Retry offers',exact:true}).count(),0);
  assert.ok(reads>=2,'guest can recover a failed catalogue');
 } else {
  await strip.getByText('₹20.00 off above ₹200.00',{exact:true}).waitFor();
  const productY=(await page.locator('#gokul-product-1').boundingBox()).y;
  if(scenario==='expiry') {
   available=false;await strip.getByText('₹20.00 off above ₹200.00',{exact:true}).waitFor({state:'hidden',timeout:10000});
   assert.ok(reads>=2,'expiry triggers a fresh catalogue read');
  } else if(scenario==='cadence') {
   available=false;await page.clock.fastForward(60001);
   await strip.getByText('₹20.00 off above ₹200.00',{exact:true}).waitFor({state:'hidden'});
   assert.ok(reads>=2,'visible menu rechecks within one minute');
  } else {
   hold=true;available=false;fail=scenario==='refresh-failure';const before=reads;
   await page.evaluate(()=>window.dispatchEvent(new Event('focus')));
   await page.waitForFunction(()=>!document.querySelector('#menu-offers-open')?.textContent.includes('₹20.00'));
   assert.ok(reads>before,'resume refreshes public terms');
   release();
   if(fail){await strip.click();await page.getByRole('dialog',{name:'Offers & savings',exact:true}).getByRole('button',{name:'Retry offers',exact:true}).waitFor();}
  }
  assert.ok(Math.abs((await page.locator('#gokul-product-1').boundingBox()).y-productY)<=1,'refresh preserves menu product position');
 }
 assert.deepEqual(errors,[]);await context.close();console.log(`Public offer freshness: ${scenario} passed`);
}} finally {await browser.close();}
