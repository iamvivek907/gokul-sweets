import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const browser=await chromium.launch({headless:true}),base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
const branch={id:1,name:'Gokul Test Branch',active:true,pickupAvailable:true};
try{for(const [width,themed] of [[390,true],[1280,true],[390,false]]){
 const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:'block'}),page=await context.newPage();
 let order={orderNumber:'REFRESH-1',customerOrderNumber:1,branchId:1,branchName:branch.name,orderStatus:'PREPARING',fulfillmentType:'PICKUP',totalAmount:120,createdAt:new Date().toISOString()},reads=0,authenticated=true,fail=false,hold=false,release;
 await context.addInitScript(branch=>{localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));localStorage.setItem('gokul-social-follow-popup-seen','true');window.__ordersVisible=true;window.__ordersOnline=true;Object.defineProperty(document,'visibilityState',{get:()=>window.__ordersVisible?'visible':'hidden',configurable:true});Object.defineProperty(navigator,'onLine',{get:()=>window.__ordersOnline,configurable:true});},branch);
 await context.route('**/api/**',async route=>{const url=new URL(route.request().url()),path=url.pathname;let json=[];
  if(path==='/api/storefront/features')json={futuristicStorefrontV2:themed,checkoutExperienceV2:themed,simplifiedCheckout:themed,acceptedCheckoutQuote:themed};
  else if(path==='/api/storefront/customer-identity')json={enabled:true};else if(path==='/api/customer/identity/me')json={authenticated,phone:'+919876543210'};
  else if(path==='/api/branches')json=[branch];else if(path==='/api/branches/1')json=branch;
  else if(path==='/api/customer/identity/orders/page'){reads++;if(hold)await new Promise(resolve=>release=resolve);if(fail)return route.fulfill({status:503,json:{message:'Unavailable'}});json={orders:url.searchParams.get('status')==='ACTIVE'&&order.orderStatus==='PICKED_UP'?[]:[order],nextBefore:null};}
  return route.fulfill({json}).catch(()=>{});
 });
 await page.clock.install();await page.goto(`${base}/orders`);await page.locator('.profile-order-card').first().waitFor();await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});
 await page.getByRole('button',{name:'Active',exact:true}).click();await page.locator('.profile-order-card').first().waitFor();await page.clock.pauseAt(await page.evaluate(()=>Date.now()+1000));
 hold=true;let count=reads;await page.clock.fastForward(15001);for(let n=0;n<100&&!release;n++)await new Promise(resolve=>setTimeout(resolve,10));assert.ok(release);assert.equal(reads,count+1);assert.equal(await page.locator('.profile-order-card').count(),1,'pending refresh retains cards');
 hold=false;order={...order,orderStatus:'READY_FOR_PICKUP'};release();await page.getByText('Ready for Pickup',{exact:true}).waitFor();
 count=reads;await page.evaluate(()=>{window.__ordersVisible=false;document.dispatchEvent(new Event('visibilitychange'));});await page.clock.fastForward(60000);assert.equal(reads,count,'hidden history does not poll');
 const resumed=page.waitForResponse(response=>new URL(response.url()).pathname==='/api/customer/identity/orders/page');await page.evaluate(()=>{window.__ordersVisible=true;document.dispatchEvent(new Event('visibilitychange'));});await (await resumed).finished();await page.waitForFunction(()=>!document.body.textContent.includes('Updating…'));
 count=reads;await page.evaluate(()=>{window.__ordersOnline=false;window.dispatchEvent(new Event('offline'));});await page.clock.fastForward(60000);assert.equal(reads,count,'offline history does not poll');
 fail=true;await page.evaluate(()=>{window.__ordersOnline=true;window.dispatchEvent(new Event('online'));});await page.getByRole('alert').filter({hasText:'could not refresh'}).waitFor();assert.equal(await page.locator('.profile-order-card').count(),1);
 fail=false;order={...order,orderStatus:'PICKED_UP'};await page.getByRole('button',{name:'Retry',exact:true}).click();await page.locator('.profile-order-card').waitFor({state:'hidden'});await page.getByRole('button',{name:'All',exact:true}).click();await page.getByText('Picked Up',{exact:true}).waitFor();
 count=reads;await page.clock.fastForward(60000);assert.equal(reads,count,'completed-only history stops polling');
 authenticated=false;await page.evaluate(()=>window.dispatchEvent(new Event('gokul-customer-identity-changed')));await page.locator('.profile-order-card').waitFor({state:'hidden'});assert.equal(await page.locator('.profile-order-card').count(),0);
 await context.close();console.log(`History refresh ${width}px themed=${themed} passed`);
}}finally{await browser.close();}
