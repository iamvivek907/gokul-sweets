import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {mkdir} from 'node:fs/promises';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const browser=await chromium.launch({headless:true}),base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
try{
 for(const width of [320,390,640,641]){
  const compact=width<641,context=await browser.newContext({viewport:{width,height:844},serviceWorkers:'block',timezoneId:'America/Los_Angeles'}),page=await context.newPage();
  const branch={id:1,code:'FIX',name:'Gokul Tamkuhi Road',address:'Station Road, opposite the bus stand',city:'Tamkuhi Road',pincode:'274407',active:true,operational:true,pickupAvailable:true};
  const now=new Date().toISOString(),date=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata'}).format(new Date(Date.now()+86400000));
  const order={id:1,orderNumber:'CORRECTION-TEST',customerOrderNumber:42,branchId:1,branchName:branch.name,branchAddress:branch.address,branchPhone:'9876543210',orderStatus:'PREPARING',paymentStatus:'PAID',fulfillmentType:'PICKUP',pickupDate:date,pickupStartTime:'18:00:00',pickupEndTime:'19:00:00',pickupType:'NORMAL',customerName:'Customer',maskedCustomerPhone:'******3210',items:[{id:1,productId:1,productName:'Samosa',saleMode:'UNIT',quantity:1,unitPrice:100,taxRate:0,taxAmount:0,lineTotal:100}],subtotal:100,taxAmount:0,priorityCharge:0,convenienceFee:10,convenienceFeeTax:1,paymentFee:2,paymentFeeTax:.2,totalAmount:112,createdAt:now,updatedAt:now};
  const wallet={policyVersion:'1',balance:600,debt:0,pendingCoins:20,completedOrders:5,rewards:[{code:'SWEET_75',name:'Sweet saving',coins:750,discount:75,minimumSubtotal:699,eligible:false,unavailableReason:'More coins needed'}],history:[],nextExpiry:null,maximumRedemptionPercent:10,terms:'Coins are earned on completed paid food orders. Fees are excluded.'};
  let name='',addresses=[],saved=0,location=0,otp=0,cancelBodies=[];
  const summary=()=>({orderNumber:order.orderNumber,branchName:branch.name,serverTime:new Date().toISOString(),cancellationDeadline:new Date(Date.now()+300000).toISOString(),canCancel:order.orderStatus!=='CANCELLED',canTransfer:false,refundAmount:100,retainedCharges:12,refundStatus:order.orderStatus==='CANCELLED'?'REFUND_PENDING':'NOT_REQUESTED',explanation:'Food refund only.'});
  await context.addInitScript(branch=>{localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));sessionStorage.setItem('gokul-mobile-launch-seen','1');window.geoCalls=0;navigator.geolocation.getCurrentPosition=success=>{window.geoCalls++;success({coords:{latitude:26.75,longitude:84.02},timestamp:Date.now()});};},branch);
  await context.route('**/api/**',async route=>{
   const req=route.request(),path=new URL(req.url()).pathname;let json=[];
   if(path==='/api/storefront/features')json={futuristicStorefrontV2:true,checkoutExperienceV2:true,simplifiedCheckout:true,acceptedCheckoutQuote:true,customerAccountHub:true,notificationInbox:true,gokulRewards:true,branchExperience:true,truthfulOrderTracking:true};
   else if(path==='/api/storefront/customer-identity')json={enabled:true,guestCheckoutEnabled:false};
   else if(path==='/api/customer/identity/me')json={authenticated:true,phone:'+919876543210',name};
   else if(path==='/api/customer/identity/start'){otp++;return route.fulfill({status:204});}
   else if(path==='/api/customer/identity/me/name'){name=req.postDataJSON().name;return route.fulfill({status:204});}
   else if(path==='/api/customer/identity/account')json={paidOrders:8,completedOrders:5,favouriteProductIds:[],addresses,preferences:{dietaryNotes:null,preferredBranchId:null}};
   else if(path==='/api/customer/identity/account/location'){if(req.method()==='POST'){location++;json={addressLine:'Station Road, Tamkuhi',locality:'Tamkuhi Road',postalCode:'274407',attribution:'Google Maps'};}else json={enabled:true};}
   else if(path==='/api/customer/identity/account/addresses'){saved++;json={id:1,...req.postDataJSON()};addresses=[json];}
   else if(path==='/api/customer/identity/rewards')json=wallet;
   else if(path==='/api/customer/identity/orders')json=[order];
   else if(path==='/api/customer/identity/notification-preferences')json={offerInboxEnabled:false,marketingConsentGranted:false};
   else if(path==='/api/customer/identity/notifications')json={messages:[{id:1,eventKey:'payment:1',kind:'PAYMENT_PAID',targetType:'ORDER',targetId:order.orderNumber,title:'Payment received',message:'Your order is confirmed. Collect only at Gokul Tamkuhi Road.',deliveryState:'AVAILABLE',createdAt:now,readAt:null}],unreadCount:1,nextBefore:null,readThrough:1};
   else if(path==='/api/branches')json=[branch];else if(path==='/api/branches/1')json=branch;
   else if(path==='/api/orders/CORRECTION-TEST')json=order;
   else if(path==='/api/orders/CORRECTION-TEST/pickup-code')json={code:'0042'};
   else if(path==='/api/customer/identity/orders/CORRECTION-TEST/correction')json=summary();
   else if(path==='/api/customer/identity/orders/CORRECTION-TEST/cancel'){
    cancelBodies.push(req.postDataJSON());order.orderStatus='CANCELLED';order.paymentStatus='REFUND_PENDING';
    if(cancelBodies.length===1)return route.abort('failed');json=summary();
   }
   else if(path==='/api/admin/auth/me')return route.fulfill({status:401,json:{}});
   return route.fulfill({json});
  });
  await page.goto(`${base}/profile`);await page.locator('.account-hub').waitFor();await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});
  assert.equal(await page.locator('.account-tier-mark').isVisible(),compact);
  if(compact){
   await page.getByRole('link',{name:'View rewards',exact:true}).click();await page.waitForURL('**/profile/rewards');await page.locator('.reward-wallet-card').getByText('600',{exact:false}).waitFor();assert.match(await page.locator('.reward-savings-list').innerText(),/Minimum ₹750/);assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
   if(process.env.SCREENSHOT_DIR){await mkdir(process.env.SCREENSHOT_DIR,{recursive:true});await page.screenshot({path:`${process.env.SCREENSHOT_DIR}/rewards-new-${width}.png`,fullPage:true});}
   await page.getByRole('link',{name:'Back to profile',exact:true}).click();await page.waitForURL('**/profile');
   await page.getByRole('button',{name:'My addresses',exact:true}).click();await page.waitForURL('**/profile/addresses');assert.equal(await page.locator('.account-navigation').isVisible(),false);await page.getByRole('button',{name:'Use my current location',exact:true}).waitFor();assert.equal(await page.evaluate(()=>window.geoCalls),0);assert.equal(saved,0);
   await page.getByRole('button',{name:'Use my current location',exact:true}).click();await page.getByLabel('Address line',{exact:true}).inputValue().then(()=>page.waitForFunction(()=>document.querySelector('input[value="Station Road, Tamkuhi"]')));assert.equal(location,1);assert.equal(saved,0);await page.getByLabel('Address line',{exact:true}).fill('House 22, Station Road');await page.getByRole('button',{name:'Save address',exact:true}).click();await page.getByText('Address saved.',{exact:true}).waitFor();assert.equal(saved,1);assert.equal(addresses[0].addressLine,'House 22, Station Road');assert.equal(otp,0);
   await page.getByRole('link',{name:'Back to profile',exact:true}).click();await page.waitForURL('**/profile');await page.getByRole('button',{name:'Add your name',exact:true}).click();await page.locator('#account-name-edit').fill('Vivek');await page.getByRole('button',{name:'Save name',exact:true}).click();await page.getByRole('heading',{name:'Vivek',exact:false}).waitFor();assert.equal(name,'Vivek');assert.equal(otp,0);
  }
  await page.goto(`${base}/notifications?from=${encodeURIComponent('/profile')}`);await page.getByRole('heading',{name:'Notifications',exact:true}).waitFor();await page.getByText('Payment received',{exact:true}).waitFor();assert.equal(await page.getByRole('link',{name:'Back to previous page',exact:true}).getAttribute('href'),'/profile');assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
  if(process.env.SCREENSHOT_DIR&&compact){await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});await page.screenshot({path:`${process.env.SCREENSHOT_DIR}/notifications-new-${width}.png`,fullPage:true});}
  await page.goto(`${base}/notifications?from=${encodeURIComponent('/menu?category=snacks')}`);await page.locator('.notification-back[href="/menu?category=snacks"]').waitFor();assert.equal(await page.getByRole('link',{name:'Back to previous page',exact:true}).getAttribute('href'),'/menu?category=snacks');await page.getByRole('link',{name:'Back to previous page',exact:true}).click();await page.waitForURL('**/menu?category=snacks');
  await page.goto(`${base}/orders/CORRECTION-TEST`);await page.getByRole('heading',{name:compact?'Order details':'#42',exact:true}).waitFor();
  if(compact){await page.getByRole('button',{name:'Review cancellation',exact:true}).click();const dialog=page.getByRole('dialog',{name:'Cancel this order?',exact:true});await dialog.waitFor();assert.match(await dialog.innerText(),/₹100.00/);assert.match(await dialog.innerText(),/₹12.00/);await page.keyboard.press('Escape');await dialog.waitFor({state:'hidden'});assert.equal(cancelBodies.length,0);await page.getByRole('button',{name:'Review cancellation',exact:true}).click();await dialog.getByRole('button',{name:'Cancel & request food refund',exact:true}).click();await dialog.getByRole('alert').waitFor();await dialog.getByRole('button',{name:'Retry cancellation',exact:true}).click();await dialog.waitFor({state:'hidden'});await page.getByText('Food refund requested',{exact:true}).waitFor();assert.equal(cancelBodies.length,2);assert.deepEqual(cancelBodies[0],cancelBodies[1]);assert.equal(cancelBodies[0].acceptedRefundAmount,100);assert.equal(cancelBodies[0].acceptedRetainedCharges,12);assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);}
  else assert.equal(await page.locator('.mobile-order-cancellation').count(),0);
  await context.close();console.log(`New account/correction ${width}px passed`);
 }
 const noJs=await browser.newContext({javaScriptEnabled:false,viewport:{width:390,height:844}}),noJsPage=await noJs.newPage();
 await noJsPage.goto(`${base}/notifications?from=${encodeURIComponent('/menu?category=snacks')}`);
 // Next streams this markup behind its loading boundary; inspect the actual SSR anchor before scripts reveal it.
 assert.equal(await noJsPage.locator('.notification-back').getAttribute('href'),'/menu?category=snacks');

 await noJs.close();console.log('Server-rendered notification return URL before JavaScript passed');
}finally{await browser.close();}
