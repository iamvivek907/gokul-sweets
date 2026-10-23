import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const browser=await chromium.launch({headless:true}),base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
try{for(const width of [390,1280,641]){
 const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:'block'}),page=await context.newPage();page.setDefaultTimeout(15000);
 const errors=[];let authenticated=false,exchanges=0,starts=0;
 page.on('pageerror',error=>errors.push(error.message));
 const branch={id:1,name:'Gokul branch',active:true,operational:true,pickupAvailable:true,phone:'9876543210',address:'Market Road'};
 await context.addInitScript(branch=>{
  localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));localStorage.setItem('gokul-language','en');localStorage.setItem('gokul-ordering-tour-seen','true');
  window.__widgetAttempts=[];window.initSendOTP=config=>window.__widgetAttempts.push(config);
  const timer=window.setTimeout.bind(window);window.setTimeout=(fn,delay,...args)=>{if(delay===120000)window.__expireVerification=()=>fn(...args);return timer(fn,delay,...args);};
 },branch);
 const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Methods':'GET,POST,OPTIONS','Access-Control-Allow-Headers':'content-type'};
 await context.route('**/api/**',route=>{
  const path=new URL(route.request().url()).pathname;let json=[];
  if(route.request().method()==='OPTIONS')return route.fulfill({status:204,headers});
  if(path==='/api/storefront/features')json={futuristicStorefrontV2:true,checkoutExperienceV2:true,customerAccountHub:true,simplifiedCheckout:true,acceptedCheckoutQuote:true,branchExperience:true};
  else if(path==='/api/branches')json=[branch];else if(path==='/api/branches/1')json=branch;
  else if(path==='/api/storefront/customer-identity')json={enabled:true,guestCheckoutEnabled:false};
  else if(path==='/api/customer/identity/me')json={authenticated,phone:authenticated?'+919876543210':undefined,name:authenticated?'Customer':undefined};
  else if(path==='/api/customer/identity/start'){starts++;return route.fulfill({status:204,headers});}
  else if(path==='/api/customer/identity/exchange'){exchanges++;authenticated=true;json={authenticated:true,phone:'+919876543210'};}
  else if(path==='/api/customer/identity/account')json={paidOrders:0,favouriteProductIds:[],addresses:[],preferences:{}};
  else if(path==='/api/customer/identity/orders/page')json={orders:[],nextBefore:null};
  else if(path==='/api/customer/identity/notifications')json={messages:[],unreadCount:0,nextBefore:null};
  else if(path==='/api/admin/auth/me')return route.fulfill({status:401,json:{message:'Not signed in'},headers});
  return route.fulfill({json,headers});
 });
 await page.goto(`${base}/profile`);await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});
 const verify=page.getByRole('button',{name:'Verify with SMS',exact:true});await verify.click();await page.waitForFunction(()=>window.__widgetAttempts.length===1);
 await page.evaluate(()=>window.__expireVerification());await page.getByRole('alert').filter({hasText:'Verification took too long'}).waitFor();assert.equal(await verify.isEnabled(),true);
 await page.evaluate(()=>window.__widgetAttempts[0].success({accessToken:'stale-provider-proof'}));assert.equal(exchanges,0);
 if(width===641){
  authenticated=true;await verify.click();await page.waitForFunction(()=>document.body.innerText.includes('Customer'));
  assert.equal(starts,1);assert.equal(exchanges,0);assert.equal(await page.evaluate(()=>window.__widgetAttempts.length),1);
  assert.deepEqual(errors,[]);await context.close();console.log('An already-established session resumes without another OTP');continue;
 }
 await verify.click();await page.waitForFunction(()=>window.__widgetAttempts.length===2);
 await page.evaluate(()=>{window.__widgetAttempts[0].failure();window.__widgetAttempts[0].success({accessToken:'stale-provider-proof'});});assert.equal(exchanges,0);assert.equal(await page.getByRole('button',{name:'Please wait…',exact:true}).isDisabled(),true);
 const established=page.waitForResponse(response=>new URL(response.url()).pathname==='/api/customer/identity/me'&&response.status()===200);
 await page.evaluate(()=>window.__widgetAttempts[1].success({accessToken:'current-provider-proof'}));await established;await page.waitForFunction(()=>document.body.innerText.includes('Customer'));
 assert.equal(exchanges,1);assert.equal(starts,2);assert.deepEqual(errors,[]);
 await context.close();console.log(`OTP expiry, retry and stale-callback isolation ${width}px passed`);
}}catch(error){for(const context of browser.contexts())for(const page of context.pages())console.error(await page.locator('body').innerText());throw error;}finally{await browser.close();}
