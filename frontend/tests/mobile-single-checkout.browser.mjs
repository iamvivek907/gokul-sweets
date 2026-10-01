import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {mkdir} from 'node:fs/promises';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const browser=await chromium.launch({headless:true}),base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
const date=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata'}).format(new Date(Date.now()+86400000));
const branch={id:1,code:'TEST',name:'Test Gokul branch',active:true,address:'Test address',city:'Test city',phone:'9876543210',pickupAvailable:true};
const half={id:10,name:'Paneer meal Half',categoryId:1,categoryName:'Meals',description:'Fresh paneer with rice.',price:100,imageUrl:null,available:true,saleMode:'UNIT',minimumWeightGrams:null,weightStepGrams:null},full={...half,id:11,name:'Paneer meal Full',price:180},drink={...half,id:12,name:'Fresh drink',categoryId:2,categoryName:'Drinks',price:40};
const slot={id:1,branchId:1,slotDate:date,startTime:'18:00:00',endTime:'19:00:00',active:true,capacity:50,remainingCapacity:50,priorityEnabled:false};
try{for(const scenario of ['phone','boundary','desktop','changed-total','uncertain-order','stale-identity']){
 const width=scenario==='desktop'?1280:scenario==='boundary'?640:390;
 const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:'block'}),page=await context.newPage();
 let signedIn=false,orders=[],payments=0,previews=0,firstAttempt=true,liveItems=[],checkoutRequest=null,paymentStatus="PENDING",cancels=0;
 const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Methods':'GET,POST,PUT,OPTIONS','Access-Control-Allow-Headers':'content-type,idempotency-key'};
 const subtotal=()=>liveItems.reduce((sum,i)=>sum+({10:100,11:180,12:40}[i.productId]??0)*i.quantity,0);
 const payment=()=>({paymentId:10,orderNumber:"TEST-SINGLE",provider:"PHONEPE",paymentStatus,amount:subtotal()-20,currency:"INR",paymentUrl:"https://gateway.example.invalid/pay",expiresAt:new Date(Date.now()+600000).toISOString()});
 const order=()=>({id:1,orderNumber:'TEST-SINGLE',branchId:1,pickupSlotId:1,pickupDate:date,pickupStartTime:'18:00:00',pickupEndTime:'19:00:00',pickupType:'NORMAL',customerName:'Verified customer',customerPhone:'9876543210',orderStatus:paymentStatus==='FAILED'?'PAYMENT_FAILED':'PENDING_PAYMENT',paymentStatus,fulfillmentType:'PICKUP',branchName:branch.name,subtotal:subtotal(),taxAmount:0,priorityCharge:0,totalAmount:subtotal(),items:[],reservationExpiresAt:new Date(Date.now()+600000).toISOString(),createdAt:new Date().toISOString()});
 await context.route('https://gateway.example.invalid/**',route=>route.fulfill({contentType:'text/html',body:'<h1>Secure provider checkout</h1>'}));
 await context.route('**/api/**',async route=>{
  const req=route.request(),p=new URL(req.url()).pathname;let json=[];
  if(req.method()==='OPTIONS')return route.fulfill({status:204,headers});
  if(p==='/api/storefront/features')json={futuristicStorefrontV2:true,checkoutExperienceV2:true,contextualStorefrontV2:true,simplifiedCheckout:true,smartPickupSelection:true,smartAvailability:true,authoritativePickupCommitment:true,acceptedCheckoutQuote:true,persistentPickupContext:true,cartSwitchPreview:true,inPlaceBranchSwitch:true,accessibleOrderingV2:true,paymentPollingV2:true,paidCartRecovery:true,pickupAddOns:true,futureOrderingDays:30,today:date};
  else if(p==='/api/storefront/customer-identity')json={enabled:true,guestCheckoutEnabled:false};
  else if(p==='/api/customer/identity/me')json={authenticated:signedIn,...(signedIn?{phone:'+919876543210',name:'Verified customer'}:{})};
  else if(p==='/api/customer/identity/start')json={};
  else if(p==='/api/customer/identity/exchange'){signedIn=true;json={authenticated:true};}
  else if(p==='/api/branches')json=[branch];else if(p==='/api/branches/1')json=branch;
  else if(p==='/api/menu')json=[{id:1,name:'Meals',displayOrder:0,products:[half,full]},{id:2,name:'Drinks',displayOrder:1,products:[drink]}];
  else if(p==='/api/menu/portion-groups')json={version:1,groups:[{key:'paneer',title:'Paneer meal',choices:[{productId:10,label:'Half'},{productId:11,label:'Full'}]}]};
  else if(p.endsWith('/availability'))json={today:date,maximumDate:date,dates:[{date,available:true,slots:[{slot,normalAvailable:true,priorityAvailable:false}]}]};
  else if(p==='/api/menu/pickup-addons')json=[{product:drink,weightGrams:null,portionPrice:40,portionTotal:40,reason:'Pairs well with your order'}];else if(p==='/api/menu/pickup-addons/check')json={orderable:true};
  else if(p==='/api/orders/mobile-preview'){previews++;liveItems=req.postDataJSON().items;json={quote:{token:'signed-quote',subtotal:String(subtotal()),taxAmount:'0',priorityCharge:'0',convenienceFee:'0',paymentFeeRate:'0',totalAmount:String(subtotal()),currency:'INR',items:[],expiresAt:new Date(Date.now()+600000).toISOString()},offers:[{code:'SAVE20',name:'Save 20',rebateAmount:20,payableAfterRebate:subtotal()-20}]};}
  else if(p==='/api/orders'&&req.method()==='POST'){orders.push({key:req.headers()['idempotency-key'],request:req.postDataJSON()});checkoutRequest=req.postDataJSON();if(scenario==='uncertain-order'&&firstAttempt){firstAttempt=false;return route.abort('failed');}json=order();}
  else if(p==='/api/orders/TEST-SINGLE')json=order();
  else if(p==='/api/payments/providers')json={defaultProvider:'PHONEPE',enabledProviders:['PHONEPE']};
  else if(p==='/api/payments/order/TEST-SINGLE')json={payment:payment()};
  else if(p==='/api/payments/10/refresh')json=payment();
  else if(p==='/api/payments/10/cancel-checkout'){cancels++;paymentStatus='FAILED';json=payment();}
  else if(p==='/api/orders/TEST-SINGLE/rebate/best')json={totalAmount:subtotal()-20+(scenario==='changed-total'?5:0),rebateAmount:20};
  else if(p==='/api/payments'&&req.method()==='POST'){payments++;json={paymentId:10,orderNumber:'TEST-SINGLE',provider:'PHONEPE',paymentStatus:'PENDING',amount:subtotal()-20,currency:'INR',paymentUrl:'https://gateway.example.invalid/pay',expiresAt:new Date(Date.now()+600000).toISOString()};}
  return route.fulfill({json,headers});
 });
 await context.addInitScript(({branch})=>{if(!localStorage.getItem('gokul-selected-branch'))localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));window.initSendOTP=options=>options.success({type:'success',accessToken:'synthetic-test-proof'});},{branch});
 await page.goto(`${base}/menu`);await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});await page.locator('.gokul-menu-product-card').first().waitFor();
 if(width>640){assert.equal(await page.locator('.mobile-portion-card').count(),0);assert.equal(await page.locator('.mobile-menu-filters').count(),0);await page.getByRole('button',{name:'Add Paneer meal Half to cart',exact:true}).click();assert.equal(await page.locator('.gokul-floating-cart a').getAttribute('href'),'/cart');await page.goto(`${base}/checkout/mobile`);await page.waitForURL('**/cart');assert.equal(previews,0);assert.equal(orders.length,0);await context.close();continue;}
 const portion=page.locator('.mobile-portion-card');await portion.waitFor();assert.equal(await page.locator('.gokul-menu-product-card').count(),2);
 await portion.getByRole('radio',{name:/Full/}).check();await portion.getByRole('button',{name:'Add Paneer meal to cart',exact:true}).click();await portion.getByRole('radio',{name:/Half/}).check();await portion.getByRole('button',{name:'Add Paneer meal to cart',exact:true}).click();assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-cart')).items.map(i=>i.product.id).sort()),[10,11]);
 await page.locator('.mobile-menu-filters summary').click();await page.getByLabel('Price up to').selectOption('100');await page.getByRole('checkbox',{name:'Portion choices',exact:true}).check();assert.equal(await page.locator('.gokul-menu-product-card').count(),1);assert.equal(await portion.getByRole('radio',{name:/Full/}).count(),0);await page.locator('.mobile-menu-filters').getByRole('button',{name:'Clear filters',exact:true}).click();assert.equal(await page.locator('.gokul-menu-product-card').count(),2);assert.equal(await page.getByRole('checkbox',{name:/veg/i}).count(),0);
 await page.locator('.gokul-floating-cart a').click();await page.waitForURL('**/checkout/mobile');await page.getByRole('heading',{name:'Verify your phone',exact:true}).waitFor();assert.equal(await page.getByRole('dialog').count(),0);assert.equal(await page.getByRole('button',{name:'Pay now',exact:true}).isDisabled(),true);assert.equal(previews,0);assert.equal(orders.length,0);
 await page.getByRole('button',{name:'Verify with SMS',exact:true}).click();await page.getByRole('heading',{name:'Phone verified',exact:true}).waitFor();
 const ready=()=>page.waitForFunction(()=>!Array.from(document.querySelectorAll('button')).find(b=>b.textContent==='Pay now')?.disabled);
 await ready();assert.equal(new URL(page.url()).pathname,'/checkout/mobile');assert.equal(orders.length,0);assert.equal(payments,0);
 await page.getByRole('button',{name:'Add one Paneer meal Half',exact:true}).click();await ready();assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-cart')).items.find(i=>i.product.id===10).quantity),2);
 await page.getByRole('button',{name:'Add Fresh drink',exact:true}).click();await ready();
 if(process.env.SCREENSHOT_DIR){await mkdir(process.env.SCREENSHOT_DIR,{recursive:true});await page.screenshot({path:`${process.env.SCREENSHOT_DIR}/single-checkout-${scenario}.png`,fullPage:true});}
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);if(scenario==='stale-identity')signedIn=false;await page.getByRole('button',{name:'Pay now',exact:true}).dblclick();
 if(scenario==='stale-identity'){await page.getByRole('alert').filter({hasText:'Verify the order'}).waitFor();assert.equal(orders.length,0);assert.equal(payments,0);}
 else if(scenario==='changed-total'){await page.getByRole('alert').filter({hasText:'total changed'}).waitFor();assert.equal(orders.length,1);assert.equal(payments,0);assert.equal(await page.getByRole('link',{name:'Continue payment',exact:true}).count(),1);}
 else{if(scenario==='uncertain-order'){await page.getByRole('alert').filter({hasText:'couldn’t confirm'}).waitFor();assert.equal(await page.getByRole('button',{name:'Add one Paneer meal Half',exact:true}).isDisabled(),true);await page.reload();await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});await page.getByRole('button',{name:'Retry checkout',exact:true}).click();}await page.waitForURL('https://gateway.example.invalid/pay');assert.equal(payments,1);assert.equal(orders.length,scenario==='uncertain-order'?2:1);assert.equal(checkoutRequest.items.find(i=>i.productId===11).quantity,1);assert.equal(checkoutRequest.quoteToken,'signed-quote');if(scenario==='uncertain-order'){assert.equal(orders[0].key,orders[1].key);assert.deepEqual(orders[0].request,orders[1].request);}}
 if(scenario==='phone'){
  await page.goto(`${base}/checkout/payment/TEST-SINGLE`);await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});
  await page.getByRole('button',{name:'Cancel this order',exact:true}).click();
  const dialog=page.getByRole('dialog',{name:'Cancel this payment?'});await dialog.getByRole('button',{name:'Confirm cancellation',exact:true}).click();
  await page.waitForURL('**/checkout/mobile?paymentRecovery=failed');
  await page.getByText('Payment wasn’t completed. Your cart is saved; review it and try again.',{exact:true}).waitFor();
  assert.equal(cancels,1);assert.equal(orders.length,1);assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-cart')).items.length),3);
  assert.equal(await page.getByRole('heading',{name:'Continue your existing order',exact:true}).count(),0);
 }
 await context.close();
}console.log('PASS: phone-only portions and filters, inline OTP, direct payment, changed totals blocked, safe interrupted retries and unchanged desktop route.');}finally{await browser.close();}
