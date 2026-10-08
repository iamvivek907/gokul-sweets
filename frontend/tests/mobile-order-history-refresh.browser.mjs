import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const browser=await chromium.launch({headless:true});
const base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
const branch={id:1,name:'Gokul Sweets Test Branch',active:true,operational:true,pickupAvailable:true};
const initial={orderNumber:'REFRESH-1',customerOrderNumber:1,branchId:1,branchName:branch.name,orderStatus:'PREPARING',paymentStatus:'PAID',fulfillmentType:'PICKUP',totalAmount:120,createdAt:new Date().toISOString(),updatedAt:new Date().toISOString(),pickupDate:'2026-10-06',items:[]};
try {for(const [width,enabled] of [[390,true],[1280,true],[390,false]]) {
 const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:'block'}),page=await context.newPage();
 let order={...initial},reads=0,fail=false,hold=false,release,authenticated=true;
 const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Methods':'GET,POST,OPTIONS','Access-Control-Allow-Headers':'content-type'};
 await context.addInitScript(branch=>{
  localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));
  window.__ordersVisible=true;window.__ordersOnline=true;
  Object.defineProperty(document,'visibilityState',{get:()=>window.__ordersVisible?'visible':'hidden',configurable:true});
  Object.defineProperty(navigator,'onLine',{get:()=>window.__ordersOnline,configurable:true});
 },branch);
 await context.route('**/api/**',async route=>{
  const path=new URL(route.request().url()).pathname;let json=[];
  if(route.request().method()==='OPTIONS')return route.fulfill({status:204,headers});
  if(path==='/api/orders/history')json=[{...initial,orderNumber:'LOCAL-2',customerOrderNumber:2}];
  else
  if(path==='/api/storefront/features')json={futuristicStorefrontV2:enabled,checkoutExperienceV2:enabled,simplifiedCheckout:enabled,acceptedCheckoutQuote:enabled,branchExperience:true};
  else if(path==='/api/storefront/customer-identity')json={enabled:true};
  else if(path==='/api/customer/identity/me')json={authenticated,name:'Test customer',phone:'+919876543210'};
  else if(path==='/api/customer/identity/orders'){
   reads++;
   if(hold)await new Promise(resolve=>{release=resolve;});
   if(fail)return route.fulfill({status:503,headers,json:{message:'Temporarily unavailable'}});
   json=authenticated?[order]:[];
  }
  else if(path==='/api/branches')json=[branch];else if(path==='/api/branches/1')json=branch;
  return route.fulfill({headers,json}).catch(()=>{});
 });
 await page.clock.install();
 await page.goto(`${base}/orders`);
 await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});
 await page.locator('article').first().waitFor();
 await page.clock.pauseAt(await page.evaluate(()=>Date.now()+1000));
 // The initial hidden check can precede hydration. Advance the launch timeout
 // before clicking with a paused clock, while keeping the 15s poll below untouched.
 await page.clock.fastForward(3000);
 await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});
 const previous=reads;
 if(width<=640&&enabled){
  await page.getByRole('searchbox',{name:'Find an order'}).fill('REFRESH-1');
  await page.getByRole('tab',{name:'Active',exact:true}).click();
  order={...order,orderStatus:'READY_FOR_PICKUP'};hold=true;
  await page.clock.fastForward(15001);
  for(let i=0;i<100&&!release;i++)await new Promise(resolve=>setTimeout(resolve,10));
  assert.ok(release,'poll starts while page stays open');
  assert.equal(reads,previous+1);
  assert.equal(await page.locator('.mobile-order-card').count(),1,'cards stay visible during refresh');
  assert.equal(await page.getByText('Loading your orders...', {exact:true}).count(),0);
  await page.clock.fastForward(15001);
  await page.getByText('Order updates took too long. Please try again.',{exact:true}).waitFor();
  assert.equal(reads,previous+1,'history reads do not overlap before the deadline');
  assert.equal(await page.locator('.mobile-order-card').count(),1,'timeout keeps existing cards');
  hold=false;release();
  await page.getByRole('button',{name:'Try again',exact:true}).click();
  await page.getByText('Ready for Pickup',{exact:true}).waitFor();
  assert.equal(reads,previous+2,'retry recovers immediately after timeout');
  assert.equal(await page.getByRole('searchbox',{name:'Find an order'}).inputValue(),'REFRESH-1');
  assert.equal(await page.getByRole('tab',{name:'Active',exact:true}).getAttribute('aria-selected'),'true');
  let count=reads;
  await page.evaluate(()=>{window.__ordersVisible=false;document.dispatchEvent(new Event('visibilitychange'));});
  await page.clock.fastForward(60000);assert.equal(reads,count,'hidden pages do not poll');
  order={...order,orderStatus:'PREPARING'};
  await page.evaluate(()=>{window.__ordersVisible=true;document.dispatchEvent(new Event('visibilitychange'));});
  await page.getByText('Preparing',{exact:true}).waitFor();assert.equal(reads,count+1,'visible resume refreshes immediately');
  count=reads;
  await page.evaluate(()=>{window.__ordersOnline=false;window.dispatchEvent(new Event('offline'));});
  await page.clock.fastForward(60000);assert.equal(reads,count,'offline pages do not poll');
  order={...order,orderStatus:'READY_FOR_PICKUP'};
  await page.evaluate(()=>{window.__ordersOnline=true;window.dispatchEvent(new Event('online'));});
  await page.getByText('Ready for Pickup',{exact:true}).waitFor();
  fail=true;await page.clock.fastForward(15001);
  await page.getByText('Your orders could not be updated',{exact:true}).waitFor();
  assert.equal(await page.locator('.mobile-order-card').count(),1,'failed refresh preserves last successful cards');
  assert.equal(await page.getByText('Ready for Pickup',{exact:true}).count(),1);
  fail=false;order={...order,orderStatus:'PICKED_UP'};
  await page.clock.fastForward(15001);
  await page.getByRole('heading',{name:'No matching orders',exact:true}).waitFor();
  await page.getByRole('tab',{name:'All',exact:true}).click();
  await page.getByText('Picked Up',{exact:true}).waitFor();
  assert.equal(await page.getByText('Your orders could not be updated',{exact:true}).count(),0);
  count=reads;await page.clock.fastForward(60000);assert.equal(reads,count,'completed-only history stops periodic reads');
  authenticated=false;
  await page.evaluate(()=>window.dispatchEvent(new Event('gokul-customer-identity-changed')));
  await page.getByRole('heading',{name:'No orders yet',exact:true}).waitFor();
  assert.equal(await page.locator('.mobile-order-card').count(),0,'identity changes never retain another account’s cards');
  authenticated=true;fail=true;
  await page.evaluate(()=>{
   localStorage.setItem('gokul-order-history',JSON.stringify([{orderNumber:'LOCAL-2',createdAt:new Date().toISOString()}]));
   window.dispatchEvent(new Event('gokul-order-history-change'));
  });
  await page.getByText('Your orders could not be updated',{exact:true}).waitFor();
  await page.getByRole('searchbox',{name:'Find an order'}).fill('');
  assert.equal(await page.locator('.mobile-order-card').count(),1,'a private inbox outage keeps successfully checked local orders available');
  assert.equal(await page.locator('a[href="/orders/LOCAL-2"]').count(),1);
  assert.equal(await page.locator('a[href="/orders/REFRESH-1"]').count(),0,'old account records cannot enter the fallback');
 }else {
  await page.clock.fastForward(60000);assert.equal(reads,previous,'desktop and flag-OFF do not gain polling');
  assert.equal(await page.getByRole('button',{name:'Refresh',exact:true}).count(),1);
 }
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
 await context.close();console.log(`Order history refresh ${width}px enabled=${enabled} passed`);
}}finally{await browser.close();}
