import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const browser=await chromium.launch({headless:true}),base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
const date=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata'}).format(new Date(Date.now()+86400000));
const branch={id:1,name:'Rewards branch',code:'REWARD',active:true,pickupAvailable:true},product={id:1,name:'Fresh sweets',categoryId:1,categoryName:'Sweets',price:150,saleMode:'UNIT',available:true},slot={id:1,branchId:1,slotDate:date,startTime:'18:00:00',endTime:'19:00:00',active:true,priorityEnabled:false};
const wallet={policyVersion:'rules-v1',balance:100,pendingCoins:15,completedOrders:3,debt:0,maximumRedemptionPercent:10,terms:'Coins expire 180 days after your last qualifying completed order. Tax and fees are excluded.',history:[],nextExpiry:null,rewards:[{code:'SWEET_5',name:'₹5 sweet saving',coins:30,discount:5,minimumSubtotal:149,eligible:true,unavailableReason:null}]};
try{for(const scenario of ['switch','reward-first','offer-failure','reward-failure','flag-off','desktop']){
 console.log('Rewards scenario:',scenario);const desktop=scenario==='desktop',enabled=scenario!=='flag-off';
 const context=await browser.newContext({viewport:{width:desktop?1280:390,height:900},serviceWorkers:'block'}),page=await context.newPage();let previews=[],applies=[],posts=[],rewardCode=null,currentTotal=142.8;
 const offer=(code,amount,reward=0)=>({rebateId:amount,code,name:`Save ${amount}`,rebateType:'FIXED_AMOUNT',scope:'GENERAL',rebateAmount:amount,payableAfterRebate:Math.round((160-reward-amount)*102)/100,minimumOrderAmount:code==='SAVE20'?150:100});
 const offers=reward=>[...(!reward?[offer('SAVE20',20)]:[]),offer('SAVE10',10,reward),offer('SAVE8',8,reward)];
 const order=()=>({id:1,orderNumber:'REWARDS-TEST',branchId:1,branchName:branch.name,pickupSlotId:1,pickupDate:date,pickupStartTime:'18:00:00',pickupEndTime:'19:00:00',pickupType:'NORMAL',fulfillmentType:'PICKUP',customerName:'Verified customer',customerPhone:'9876543210',maskedCustomerPhone:'******3210',orderStatus:'PENDING_PAYMENT',paymentStatus:null,subtotal:150,taxAmount:0,priorityCharge:0,convenienceFee:10,paymentFee:2.8,paymentFeeRate:2,totalAmount:currentTotal,items:[{id:1,productId:1,productName:product.name,saleMode:'UNIT',quantity:1,weightGrams:null,unitPrice:150,taxAmount:0,lineTotal:150}],reservationExpiresAt:new Date(Date.now()+600000).toISOString(),createdAt:new Date().toISOString()});
 await context.route('**/api/**',async route=>{const req=route.request(),path=new URL(req.url()).pathname,headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Methods':'GET,POST,PUT,OPTIONS','Access-Control-Allow-Headers':'content-type,idempotency-key'};let json=[];
  if(req.method()==='OPTIONS')return route.fulfill({status:204,headers});
  if(path==='/api/storefront/features')json={futuristicStorefrontV2:true,checkoutExperienceV2:true,simplifiedCheckout:true,acceptedCheckoutQuote:true,customerAccountHub:true,gokulRewards:enabled,futureOrderingDays:30,today:date,pickupAddOns:true};
  else if(path==='/api/storefront/customer-identity')json={enabled:true,guestCheckoutEnabled:false};
  else if(path==='/api/customer/identity/me')json={authenticated:true,phone:'+919876543210',name:'Verified customer'};
  else if(path==='/api/branches')json=[branch];else if(path==='/api/branches/1')json=branch;
  else if(path==='/api/menu')json=[{id:1,name:'Sweets',products:[product]}];
  else if(path.endsWith('/availability'))json={today:date,maximumDate:date,dates:[{date,available:true,slots:[{slot,normalAvailable:true,priorityAvailable:false}]}]};
  else if(path==='/api/orders/mobile-preview'){
   const data=req.postDataJSON();previews.push(data);
   if((scenario==='offer-failure'&&data.offerCode==='SAVE8')||(scenario==='reward-failure'&&data.rewardCode))return route.fulfill({status:503,headers,json:{message:'Savings verification unavailable. Try again.'}});
   const reward=data.rewardCode?5:0,eligible=offers(reward),selected=eligible.find(o=>o.code===data.offerCode)??eligible[0];
   json={quote:{token:'server-signed',subtotal:'150',taxAmount:'0',priorityCharge:'0',convenienceFee:'10',paymentFeeRate:'2',totalAmount:'163.20',currency:'INR',expiresAt:new Date(Date.now()+600000).toISOString(),items:[]},offers:eligible,selectedOffer:selected,rewards:enabled?wallet:null,rewardDiscount:reward,totalBeforeOffer:(160-reward)*1.02,paymentFee:Math.round((160-reward-selected.rebateAmount)*2)/100};
  }else if(path==='/api/orders'&&req.method()==='POST'){posts.push(req.postDataJSON());json=order();}
  else if(path==='/api/orders/REWARDS-TEST')json=order();
  else if(path.endsWith('/available-rebates'))json=offers(rewardCode?5:0);
  else if(path.endsWith('/rebate-spend-targets'))json=[];
  else if(path.endsWith('/rebate')&&req.method()==='POST'){const code=req.postDataJSON().code;applies.push(code);const selected=offers(rewardCode?5:0).find(o=>o.code===code);currentTotal=selected.payableAfterRebate;json={orderNumber:'REWARDS-TEST',rebateCode:code,rebateName:selected.name,rebateAmount:selected.rebateAmount,totalAmount:currentTotal};}
  else if(path.endsWith('/rebate/best')){applies.push('BEST');currentTotal=offers(rewardCode?5:0)[0].payableAfterRebate;json={totalAmount:currentTotal};}
  else if(path.endsWith('/rewards')){if(req.method()==='PUT'){assert.equal(req.postDataJSON().policyVersion,'rules-v1');rewardCode=req.postDataJSON().rewardCode;}const selected=offers(rewardCode?5:0)[0];currentTotal=selected.payableAfterRebate;json={rewards:wallet,rewardCode,coins:rewardCode?30:0,rewardDiscount:rewardCode?5:0,offer:req.method()==='PUT'?{rebateCode:selected.code,rebateAmount:selected.rebateAmount,totalAmount:currentTotal}:null};}
  else if(path==='/api/payments'&&req.method()==='POST')json={paymentId:10,orderNumber:'REWARDS-TEST',provider:'PHONEPE',paymentStatus:'PENDING',amount:currentTotal,currency:'INR',paymentUrl:'https://gateway.example.invalid/rewards',expiresAt:new Date(Date.now()+600000).toISOString()};
  return route.fulfill({json,headers});
 });
 await context.route('https://gateway.example.invalid/**',route=>route.fulfill({body:'Test gateway'}));
 await context.addInitScript(({branch,product,desktop})=>{localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));localStorage.setItem('gokul-cart',JSON.stringify({branchId:1,items:[{product,quantity:1,weightGrams:null}]}));if(desktop)localStorage.setItem('gokul-pending-order',JSON.stringify({orderId:1,orderNumber:'REWARDS-TEST',orderStatus:'PENDING_PAYMENT',branchId:1,pickupSlotId:1,totalAmount:142.8,reservationExpiresAt:new Date(Date.now()+600000).toISOString(),createdAt:new Date().toISOString(),cartFingerprint:'1:UNIT:1:-'}));},{branch,product,desktop});
 await page.goto(`${base}${desktop?'/checkout/offers/REWARDS-TEST':'/checkout/mobile'}`);await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});
 if(desktop){await page.getByRole('heading',{name:'Your earned coins'}).waitFor();await page.getByRole('button',{name:/₹5 sweet saving/}).click();await page.getByRole('status').filter({hasText:'Reward applied'}).waitFor();assert.equal(rewardCode,'SWEET_5');assert.equal(await page.locator('.mobile-checkout').count(),0);await context.close();continue;}
 await page.getByRole('button',{name:'Pay now',exact:true}).waitFor();await page.waitForFunction(()=>!document.querySelector('.mobile-checkout-pay button')?.disabled);
 if(!enabled){assert.equal(await page.getByRole('heading',{name:'Your earned coins'}).count(),0);await context.close();continue;}
 if(scenario.startsWith('reward-')){
  await page.getByRole('button',{name:/₹5 sweet saving/}).click();
  if(scenario==='reward-failure'){await page.getByRole('alert').filter({hasText:'Savings verification unavailable'}).waitFor();assert.equal(await page.getByRole('button',{name:'Remove reward'}).count(),0);assert.match(await page.locator('.mobile-checkout-pay strong').innerText(),/142.80/);}
  else{await page.getByRole('button',{name:'Remove reward'}).waitFor();assert.match(await page.locator('.mobile-checkout-pay strong').innerText(),/147.90/);await page.getByRole('button',{name:'Change offer'}).click();assert.equal(await page.getByRole('dialog').getByText('Save 20',{exact:true}).count(),0);await page.getByRole('dialog').getByRole('button',{name:'Close ×'}).click();}
 }else{
  await page.getByRole('button',{name:'Change offer'}).click();const dialog=page.getByRole('dialog');await dialog.waitFor();await dialog.locator('article').filter({hasText:'Save 8'}).getByRole('button',{name:'Apply',exact:true}).click();
  if(scenario==='offer-failure'){await dialog.getByRole('alert').waitFor();assert.match(await page.locator('.mobile-checkout-pay strong').innerText(),/142.80/);await dialog.getByRole('button',{name:'Close ×'}).click();}
  else{await dialog.waitFor({state:'hidden'});assert.match(await page.locator('.mobile-checkout-pay strong').innerText(),/155.04/);await page.getByRole('button',{name:'Pay now',exact:true}).click();await page.waitForURL('https://gateway.example.invalid/rewards');assert.deepEqual(applies,['SAVE8']);assert.equal(posts[0].offerCode,'SAVE8');}
 }
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);await context.close();
 }}finally{await browser.close();}
console.log('Rewards checkout browser scenarios passed');
