import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {mkdir} from 'node:fs/promises';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const browser=await chromium.launch({headless:true}),base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
try {
 for(const [width,checkout,futuristic,contextual,acceptedQuote=true,simplified=true] of [[390,true,true,true],[640,true,true,true],[1280,true,true,true],[390,true,false,true],[390,false,true,false],[390,false,true,true],[390,true,true,true,false,true],[390,true,true,true,false,false]]) {
  const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:'block'}),page=await context.newPage();
  const date=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata'}).format(new Date(Date.now()+86400000));
  const branch={id:1,code:'TEST',name:'Test Gokul branch',active:true,address:'Test address',city:'Test city',phone:'9876543210',pickupAvailable:true};
  const product={id:1,name:'Paneer meal',categoryId:1,categoryName:'Meals',description:'Fresh paneer with rice and accompaniments.',price:200,taxRate:0,imageUrl:null,available:true,saleMode:'UNIT',minimumWeightGrams:null,weightStepGrams:null};
  const slot={id:1,branchId:1,slotDate:date,startTime:'18:00:00',endTime:'19:00:00',active:true,capacity:50,remainingCapacity:50,priorityEnabled:false};
  let signedIn=false,identityDown=false,mutations=0,cancels=0,cancelError=true,status='PENDING';
  const payment=()=>({paymentId:10,orderNumber:'TEST-MOBILE',provider:'PHONEPE',paymentStatus:status,amount:200,currency:'INR',providerOrderId:'test',paymentUrl:'https://example.invalid/pay',expiresAt:new Date(Date.now()+600000).toISOString()});
  const order=()=>({id:1,orderNumber:'TEST-MOBILE',branchId:1,branchName:branch.name,pickupSlotId:1,pickupDate:date,pickupStartTime:'18:00:00',pickupEndTime:'19:00:00',pickupType:'NORMAL',fulfillmentType:'PICKUP',customerName:'Verified customer',customerPhone:'9876543210',orderStatus:status==='PENDING'?'PENDING_PAYMENT':'PAYMENT_FAILED',paymentStatus:status,subtotal:200,taxAmount:0,priorityCharge:0,totalAmount:200,items:[],reservationExpiresAt:new Date(Date.now()+600000).toISOString(),createdAt:new Date().toISOString()});
  await context.route('**/api/**',async route=>{
   const req=route.request(),p=new URL(req.url()).pathname,headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Methods':'GET,POST,PUT,OPTIONS','Access-Control-Allow-Headers':'content-type,idempotency-key'};let json=[];
   if(req.method()==='OPTIONS')return route.fulfill({status:204,headers});
   if(p==='/api/storefront/features')json={futuristicStorefrontV2:futuristic,checkoutExperienceV2:checkout,contextualStorefrontV2:contextual,simplifiedCheckout:simplified,smartPickupSelection:true,smartAvailability:true,authoritativePickupCommitment:true,acceptedCheckoutQuote:acceptedQuote,persistentPickupContext:true,cartSwitchPreview:true,inPlaceBranchSwitch:true,accessibleOrderingV2:true,paymentPollingV2:true,paidCartRecovery:true,futureOrderingDays:30,today:date};
   else if(p==='/api/storefront/customer-identity'){if(identityDown)return route.fulfill({status:503,json:{message:'Try again'},headers});json={enabled:true,guestCheckoutEnabled:false};}
   else if(p==='/api/customer/identity/me')json={authenticated:signedIn,...(signedIn?{phone:'+919876543210',name:'Verified customer'}:{})};
   else if(p==='/api/customer/identity/start')json={};
   else if(p==='/api/customer/identity/exchange'){signedIn=true;json={authenticated:true};}
   else if(p==='/api/branches')json=[branch];
   else if(p==='/api/branches/1')json=branch;
   else if(p==='/api/menu')json=[{id:1,name:'Meals',description:'Fresh meals',displayOrder:0,products:[product,{...product,id:2,name:'Sweet bowl',price:80},{...product,id:3,name:'Fresh drink',price:40}]}];
   else if(p.endsWith('/availability'))json={today:date,maximumDate:date,dates:[{date,available:true,slots:[{slot,normalAvailable:true,priorityAvailable:false}]}]};
   else if(p==='/api/orders/quote')json={token:'test',subtotal:'200',taxAmount:'0',priorityCharge:'0',totalAmount:'200',currency:'INR',items:[],expiresAt:new Date(Date.now()+600000).toISOString()};
   else if(p==='/api/orders'&&req.method()==='POST'){mutations++;json={};}
   else if(p==='/api/orders/TEST-MOBILE')json=order();
   else if(p==='/api/payments/providers')json={defaultProvider:'PHONEPE',enabledProviders:['PHONEPE']};
   else if(p==='/api/payments/order/TEST-MOBILE')json={payment:payment()};
   else if(p==='/api/payments/10/refresh')json=payment();
   else if(p==='/api/payments/10/cancel-checkout'){cancels++;if(cancelError)return route.fulfill({status:503,json:{message:'Provider check unavailable. Your order is unchanged.'},headers});status='FAILED';json=payment();}
   return route.fulfill({json,headers});
  });
  await context.addInitScript(({branch,product,slot,date})=>{
   localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));
   localStorage.setItem('gokul-cart',JSON.stringify({branchId:1,items:[{product,quantity:1,weightGrams:null}]}));
   localStorage.setItem('gokul-selected-pickup-slot',JSON.stringify({date,slot,pickupType:'NORMAL'}));
   localStorage.setItem('gokul-customer-details',JSON.stringify({name:'Old guest',phone:'9123456789'}));
   window.initSendOTP=options=>options.success({type:'success',accessToken:'synthetic-test-proof'});
  },{branch,product,slot,date});
  const started=Date.now();await page.goto(`${base}/menu`);
  const launch=page.locator('.gokul-mobile-launch');
  if(width<=640){await launch.waitFor({state:'visible'});assert.equal(await launch.getByRole('button').count(),0,'launch requires no tap');if(process.env.SCREENSHOT_DIR){await mkdir(process.env.SCREENSHOT_DIR,{recursive:true});await page.screenshot({path:`${process.env.SCREENSHOT_DIR}/mobile-launch-${width}.png`});}await launch.waitFor({state:'hidden'});assert.ok(Date.now()-started<6000,'launch ends independently of optional data');}
  else assert.equal(await launch.isVisible(),false);
  await page.locator('.gokul-product-card').first().waitFor();
  if(contextual)assert.equal(await page.locator('.gokul-menu-product-grid').evaluate(e=>getComputedStyle(e).gridTemplateColumns.split(' ').length),width<=640?1:4);
  else assert.equal(await page.locator('.product-card-controls').first().evaluate(e=>getComputedStyle(e).position),'relative');
  const action=page.locator('.gokul-floating-cart a');assert.equal(await action.getAttribute('href'),width<=640?(checkout&&acceptedQuote&&simplified?'/checkout/mobile':simplified?'/checkout/pickup':'/cart'):'/cart');
  if(process.env.SCREENSHOT_DIR)await page.screenshot({path:`${process.env.SCREENSHOT_DIR}/compact-menu-${width}.png`,fullPage:true});
  if(width<=640&&!(checkout&&acceptedQuote&&simplified)){const target=simplified?'/checkout/pickup':'/cart';await page.goto(`${base}/checkout/mobile`);await page.waitForURL(`**${target}`);await page.reload();assert.equal(new URL(page.url()).pathname,target);assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-cart')).items.length),1);assert.equal(mutations,0);}
  await page.goto(`${base}/checkout/customer`);await launch.waitFor({state:'hidden'});
  await page.getByRole('heading',{name:'Verify your phone to continue',exact:true}).waitFor();
  assert.equal(await page.getByRole('button',{name:'Continue as guest',exact:true}).count(),0);
  assert.equal(await page.locator('form').count(),0);assert.equal(mutations,0);
  identityDown=true;await page.reload();await launch.waitFor({state:'hidden'});
  await page.getByText('Sign-in is needed to place an order. Your cart is saved. Please try verification again shortly.',{exact:true}).waitFor();
  assert.equal(await page.locator('form').count(),0);assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-cart')).items.length),1);
  identityDown=false;await page.reload();await launch.waitFor({state:'hidden'});await page.getByRole('button',{name:'Verify with SMS',exact:true}).click();
  if(width<=640&&checkout){await page.waitForURL('**/checkout/review');assert.equal(await launch.isVisible(),false,'client navigation does not restart launch');}
  else {await page.getByText('Signed in · phone verified',{exact:true}).waitFor();assert.equal(new URL(page.url()).pathname,'/checkout/customer');}
  assert.equal(signedIn,true);
  if(width<=640){
   await page.goto(`${base}/checkout/payment/TEST-MOBILE`);await launch.waitFor({state:'hidden'});
   await page.getByRole('button',{name:'Cancel this order',exact:true}).click();
   const cancel=page.getByRole('dialog',{name:'Cancel this payment?'});await cancel.waitFor();assert.equal(cancels,0);
   await cancel.getByRole('button',{name:'Keep payment',exact:true}).click();await cancel.waitFor({state:'hidden'});assert.equal(cancels,0);
   await page.getByRole('button',{name:'Cancel this order',exact:true}).click();await cancel.getByRole('button',{name:'Confirm cancellation',exact:true}).click();
   await cancel.getByRole('alert').filter({hasText:'Provider check unavailable'}).waitFor();assert.equal(new URL(page.url()).pathname,'/checkout/payment/TEST-MOBILE');assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-cart')).items.length),1);
   cancelError=false;await cancel.getByRole('button',{name:'Confirm cancellation',exact:true}).click();
   await page.waitForURL('**/checkout/review?paymentRecovery=failed');await page.getByText('Ready to try again',{exact:true}).waitFor();assert.equal(cancels,2);assert.equal(mutations,0);
   if(!checkout){await page.locator('.checkout-mobile-action').waitFor();assert.notEqual(await page.locator('.checkout-mobile-action').evaluate(e=>getComputedStyle(e).position),'fixed');assert.equal(await page.locator('.customer-bottom-navigation').isVisible(),true);}
  }
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
  await context.close();
 }
 console.log('PASS: compact phone menu, desktop grid/cart path, bounded brand launch, mandatory sign-in, outage cart preservation, mobile contact skip and provider-verified cancellation/recovery.');
} finally {await browser.close();}
