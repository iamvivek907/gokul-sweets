import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const browser=await chromium.launch({headless:true}),base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
const branch={id:1,name:'Gokul Test Branch',active:true,operational:true,pickupAvailable:true};
const initial={orderNumber:'HANDOVER-1',customerOrderNumber:1,branchId:1,branchName:branch.name,orderStatus:'READY_FOR_PICKUP',paymentStatus:'PAID',fulfillmentType:'PICKUP',totalAmount:120,subtotal:120,taxAmount:0,priorityCharge:0,createdAt:new Date().toISOString(),updatedAt:new Date().toISOString(),pickupDate:'2026-10-06',pickupStartTime:'18:00:00',pickupEndTime:'19:00:00',items:[]};
try{for(const navigation of ['link','history','edge']){
 const context=await browser.newContext({viewport:{width:390,height:900},hasTouch:true,serviceWorkers:'block'}),page=await context.newPage();page.setDefaultTimeout(15000);
 // Optional local stress mode; CI retains the same timeout and normal CPU profile.
 if(Number(process.env.BROWSER_CPU_SLOWDOWN)>1)await (await context.newCDPSession(page)).send('Emulation.setCPUThrottlingRate',{rate:Number(process.env.BROWSER_CPU_SLOWDOWN)});
 let order={...initial},detailReads=0,hold=false,release,fail=false,deniedStatus=0;const errors=[],aborted=[];
 page.on('pageerror',error=>errors.push(error.message));page.on('requestfailed',request=>{if(new URL(request.url()).pathname==='/api/orders/HANDOVER-1')aborted.push(request.failure()?.errorText);});
 const waitForHeldRead=async()=>{const until=Date.now()+5000;while(!release&&Date.now()<until)await new Promise(resolve=>setTimeout(resolve,10));assert.ok(release,'held order read reached the server');};
 const orderResponse=status=>page.waitForResponse(response=>new URL(response.url()).pathname==='/api/orders/HANDOVER-1'&&response.status()===status);
 const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Methods':'GET,POST,OPTIONS','Access-Control-Allow-Headers':'content-type'};
 await context.addInitScript(branch=>{localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));Object.defineProperty(navigator,'standalone',{value:true,configurable:true});},branch);
 await context.route('**/api/**',async route=>{
  const path=new URL(route.request().url()).pathname;let json=[];
  if(route.request().method()==='OPTIONS')return route.fulfill({status:204,headers});
  if(path==='/api/storefront/features')json={futuristicStorefrontV2:true,checkoutExperienceV2:true,simplifiedCheckout:true,acceptedCheckoutQuote:true,truthfulOrderTracking:true};
  else if(path==='/api/storefront/customer-identity')json={enabled:true};
  else if(path==='/api/customer/identity/me')json={authenticated:true,name:'Test customer',phone:null};
  else if(path==='/api/branches')json=[branch];else if(path==='/api/branches/1')json=branch;
  else if(path==='/api/customer/identity/orders/page')json={orders:[order],nextBefore:null};
  else if(path==='/api/orders/HANDOVER-1'){
   detailReads++;if(deniedStatus)return route.fulfill({status:deniedStatus,headers,json:{message:'Order access denied'}});if(hold)await new Promise(resolve=>{release=resolve;});
   if(fail)return route.fulfill({status:503,headers,json:{message:'Status service unavailable'}});
   json=order;
  }
  else if(path==='/api/orders/HANDOVER-1/pickup-code')json={code:'1234'};
  else if(path==='/api/orders/HANDOVER-1/review')json={eligible:false,products:[],review:null};
  try{return await route.fulfill({headers,json});}catch{/* Leaving aborts the held read. */}
 });
 await page.clock.install();await page.goto(`${base}/orders`);await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});
 await page.locator('a[href^="/orders/HANDOVER-1?"]').first().click();await page.getByRole('heading',{name:'Order details',exact:true}).waitFor();await page.getByText('1234',{exact:true}).waitFor();
 await page.clock.pauseAt(await page.evaluate(()=>Date.now()+1000));
 // runFor preserves timer order. Await the network result and committed DOM before the next tick.
 const refreshAlert=page.getByRole('alert').filter({hasText:'Status service unavailable'});
 fail=true;const failedResponse=orderResponse(503);await page.clock.runFor(15001);await failedResponse;await refreshAlert.waitFor();assert.equal(await page.locator('.mobile-order-detail').isVisible(),true);
 // Keep recovery pending after the clock stops; stale details must remain usable until it finishes.
 fail=false;hold=true;release=undefined;const recoveredResponse=orderResponse(200);await page.clock.runFor(15001);await waitForHeldRead();
 assert.equal(await refreshAlert.isVisible(),true);assert.equal(await page.locator('.mobile-order-detail').isVisible(),true);
 hold=false;release();await recoveredResponse;await refreshAlert.waitFor({state:'hidden'});
 await page.getByText('1234',{exact:true}).waitFor();
 // Definitive access failures must unmount all protected order content.
 for(const status of [401,403,404]){
  deniedStatus=status;const deniedResponse=orderResponse(status);await page.clock.runFor(15001);await deniedResponse;
  await page.getByText('Order access denied',{exact:true}).waitFor();
  assert.equal(await page.locator('.mobile-order-detail').count(),0);
  assert.equal(await page.getByText('1234',{exact:true}).count(),0);
  deniedStatus=0;await page.getByRole('button',{name:'Try again',exact:true}).click();
  await page.getByText('1234',{exact:true}).waitFor();
 }
 // Account changes clear immediately, including cross-tab changes, before a replacement read finishes.
 for(const event of ['local','storage']){
  hold=true;release=undefined;
  await page.evaluate(event=>window.dispatchEvent(event==='local'?new Event('gokul-customer-identity-changed'):new StorageEvent('storage',{key:'gokul-customer-identity-revision',newValue:'next-account'})),event);
  await page.locator('.pickup-code-card').waitFor({state:'hidden'});
  await waitForHeldRead();
  assert.equal(await page.getByText('1234',{exact:true}).count(),0);
  hold=false;release();await page.getByText('1234',{exact:true}).waitFor();
 }
 const beforeHold=detailReads;
 hold=true;release=undefined;await page.clock.fastForward(15001);
 await waitForHeldRead();assert.equal(detailReads,beforeHold+1);
 order={...order,orderStatus:'PICKED_UP'};
 if(navigation==='link')await page.getByRole('link',{name:'My orders',exact:true}).click();
 else if(navigation==='history')await page.goBack();
 else await page.evaluate(()=>{const target=document.querySelector('.mobile-order-detail header'),touch=x=>new Touch({identifier:1,target,clientX:x,clientY:150});target.dispatchEvent(new TouchEvent('touchstart',{bubbles:true,touches:[touch(10)]}));target.dispatchEvent(new TouchEvent('touchmove',{bubbles:true,cancelable:true,touches:[touch(150)]}));target.dispatchEvent(new TouchEvent('touchend',{bubbles:true,changedTouches:[touch(150)],touches:[]}));});
 await page.waitForURL(url=>url.pathname==='/orders');await page.locator('.profile-order-card').first().waitFor();
 for(let i=0;i<100&&!aborted.length;i++)await new Promise(resolve=>setTimeout(resolve,10));assert.ok(aborted.length,'leaving aborts in-flight detail refresh');
 hold=false;release();await page.clock.fastForward(16000);assert.equal(detailReads,beforeHold+1,'departed page stops detail polling');
 // Poll cancellation is verified; let navigation transitions and the bottom bar run normally.
 await page.clock.resume();
 for(const destination of ['/','/menu','/orders']){
  await page.locator(`.customer-bottom-navigation a[href="${destination}"]`).click();await page.waitForURL(base+destination);
  await page.locator('main').filter({hasText:/\S[\s\S]{19,}\S/}).waitFor({state:'visible'});
  assert.equal(await page.evaluate(()=>!!document.querySelector('dialog[open]')),false,'no stranded overlay');
 }
 await page.locator('a[href^="/orders/HANDOVER-1?"]').first().click();await page.getByRole('heading',{name:'Order details',exact:true}).waitFor();await page.getByRole('heading',{name:'Picked Up',exact:true}).waitFor();assert.equal(await page.getByText('1234',{exact:true}).count(),0);
 assert.deepEqual(errors,[]);await context.close();console.log(`Handover navigation ${navigation} passed`);
}}finally{await browser.close();}
