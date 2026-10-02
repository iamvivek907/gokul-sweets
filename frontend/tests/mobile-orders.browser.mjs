import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {mkdir, readFile} from 'node:fs/promises';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const browser=await chromium.launch({headless:true}),base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
const branch={id:1,name:'Gokul Test branch',code:'TEST',address:'Test address',phone:'9876543210',active:true,pickupAvailable:true};
const date=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata'}).format(new Date(Date.now()+86400000));
try {
 for (const [width,enabled,paymentStatus,fulfillment='PICKUP'] of [[390,true,'PAID'],[640,true,'PAID'],[1280,true,'PAID'],[390,false,'PAID'],[390,true,'FAILED'],[390,true,'PENDING'],[390,true,'PAID','DELIVERY'],[390,true,'FAILED','DELIVERY']]) {
  const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:'block',acceptDownloads:true}),page=await context.newPage();
  const order={orderNumber:'TEST-ORDER',branchId:1,branchName:branch.name,branchAddress:branch.address,branchPhone:branch.phone,branchFssaiLicenceNumber:'12345678901234',orderStatus:paymentStatus==='PAID'?'CONFIRMED':paymentStatus==='FAILED'?'PAYMENT_FAILED':'PENDING_PAYMENT',paymentStatus,fulfillmentType:'PICKUP',pickupDate:date,pickupStartTime:'18:00:00',pickupEndTime:'19:00:00',pickupType:'NORMAL',customerName:'Test customer',maskedCustomerPhone:'******3210',items:[{id:1,productId:1,productName:'பால்கோவா / Milk sweet',saleMode:'WEIGHT',quantity:0,weightGrams:500,unitPrice:200,taxRate:0,taxAmount:0,lineTotal:100}],subtotal:100,taxAmount:0,priorityCharge:0,convenienceFee:10,convenienceFeeTax:0,paymentFee:2,paymentFeeTax:0,paymentFeeRate:2,totalAmount:92,reservationExpiresAt:new Date(Date.now()+600000).toISOString(),createdAt:new Date().toISOString(),updatedAt:new Date().toISOString()};
  if(width===640){order.items=Array.from({length:40},(_,i)=>({...order.items[0],id:i+1,productId:i+1,productName:`Milk sweet ${i+1}`}));order.subtotal=4000;order.totalAmount=3992;}
  if(fulfillment==='DELIVERY'){Object.assign(order,{fulfillmentType:'DELIVERY',pickupDate:null,pickupStartTime:null,pickupEndTime:null,pickupType:null,deliveryDate:date,deliveryStartTime:'18:00:00',deliveryEndTime:'19:00:00',deliveryAddressLine:'12 Main Road',deliveryLocality:'Test locality',deliveryPostalCode:'226001',deliveryFee:30,totalAmount:122});}
  const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Methods':'GET,POST,OPTIONS','Access-Control-Allow-Headers':'content-type,idempotency-key'};
  let mutations=0,cancels=0,cancelError=true;
  await context.route('**/api/**',async route=>{
   const req=route.request(),path=new URL(req.url()).pathname;let json=[];
   if(req.method()==='OPTIONS')return route.fulfill({status:204,headers});
   if(req.method()!=='GET'&&['/api/orders','/api/payments'].includes(path))mutations++;
   if(path==='/api/storefront/features')json={futuristicStorefrontV2:true,checkoutExperienceV2:true,simplifiedCheckout:enabled,acceptedCheckoutQuote:true,truthfulOrderTracking:true,paidCartRecovery:true,paymentPollingV2:true,branchExperience:true};
   else if(path==='/api/storefront/customer-identity')json={enabled:true,guestCheckoutEnabled:false};
   else if(path==='/api/customer/identity/me')json={authenticated:true,phone:'+919876543210'};
   else if(path==='/api/branches')json=[branch];else if(path==='/api/branches/1')json=branch;
   else if(path==='/api/orders/TEST-ORDER')json=order;
   else if(path==='/api/orders/TEST-ORDER/pickup-code')json={code:'1234'};
   else if(path==='/api/customer/identity/orders')json=[order];
   else if(path==='/api/payments/order/TEST-ORDER')json={payment:{paymentId:10,orderNumber:order.orderNumber,provider:'PHONEPE',paymentStatus,amount:order.totalAmount,currency:'INR',expiresAt:new Date(Date.now()+600000).toISOString()}};
   else if(path==='/api/payments/10/refresh')json={paymentId:10,orderNumber:order.orderNumber,provider:'PHONEPE',paymentStatus,amount:order.totalAmount,currency:'INR'};
   else if(path==='/api/payments/10/cancel-checkout'){cancels++;if(cancelError)return route.fulfill({status:503,json:{message:'Provider check unavailable'},headers});json={paymentId:10,orderNumber:order.orderNumber,provider:'PHONEPE',paymentStatus:'FAILED',amount:order.totalAmount,currency:'INR'};}
   else if(path==='/api/payments/providers')json={defaultProvider:'PHONEPE',enabledProviders:['PHONEPE']};
   return route.fulfill({json,headers});
  });
  await context.addInitScript(({branch})=>localStorage.setItem('gokul-selected-branch',JSON.stringify(branch)),{branch});
  await page.goto(`${base}/orders/TEST-ORDER`);
  await page.getByText('Milk sweet',{exact:false}).first().waitFor();
  assert.equal(await page.locator('.gokul-mobile-launch').isVisible(),false,'order detail never shows splash');
  const compact=width<=640&&enabled;
  assert.equal(await page.locator('.mobile-order-detail').count(),compact?1:0);
  if(compact){
   assert.equal(await page.locator('.mobile-order-detail').evaluate(e=>e.scrollWidth<=e.clientWidth),true);
   if(paymentStatus==='FAILED')assert.equal(await page.getByRole('link',{name:'Retry checkout',exact:true}).getAttribute('href'),'/checkout/payment/TEST-ORDER');
   assert.equal(await page.getByRole('button',{name:'Download invoice',exact:true}).count(),paymentStatus==='PAID'?1:0);
   if(paymentStatus==='PAID'){
    if(fulfillment==='PICKUP')await page.getByLabel('Pickup code: 1 2 3 4',{exact:true}).waitFor();
    if(fulfillment==='DELIVERY')await page.getByText('Delivery fee',{exact:true}).waitFor();
    await page.getByText('Offer savings',{exact:true}).waitFor();
    await page.evaluate(()=>{window.invoiceText=[];const draw=CanvasRenderingContext2D.prototype.fillText;CanvasRenderingContext2D.prototype.fillText=function(text,...args){window.invoiceText.push(text);return draw.call(this,text,...args);};});
    const downloadPromise=page.waitForEvent('download');await page.getByRole('button',{name:'Download invoice',exact:true}).click();
    const download=await downloadPromise;assert.equal(download.suggestedFilename(),'Gokul-TEST-ORDER-invoice.pdf');
    const data=await readFile(await download.path());assert.equal(data.subarray(0,8).toString(),'%PDF-1.4');assert.ok(data.includes(Buffer.from('/Type /Page')));
    if(fulfillment==='DELIVERY')assert.ok((await page.evaluate(()=>window.invoiceText)).includes('Delivery fee: INR 30.00'));
    if(width===640)assert.ok(data.includes(Buffer.from('/Count 3')),'large invoices paginate');
    if(process.env.SCREENSHOT_DIR){await mkdir(process.env.SCREENSHOT_DIR,{recursive:true});await download.saveAs(`${process.env.SCREENSHOT_DIR}/order-invoice.pdf`);}
   }
  }
  await page.evaluate(()=>window.scrollTo(0,0));
  if(process.env.SCREENSHOT_DIR){await mkdir(process.env.SCREENSHOT_DIR,{recursive:true});await page.screenshot({path:`${process.env.SCREENSHOT_DIR}/order-${width}-${enabled}-${paymentStatus}.png`,fullPage:true});}
  await page.goto(`${base}/orders`);await page.getByRole('heading',{name:'My Orders',exact:true}).waitFor();
  await page.locator(compact?'.mobile-order-card':'article').first().waitFor();
  assert.equal(await page.locator('.mobile-order-card').count(),compact?1:0);
  if(compact){await page.getByRole('link',{name:'View order TEST-ORDER →',exact:true}).click();await page.locator('.mobile-order-detail').waitFor();await page.getByRole('link',{name:'← My orders',exact:true}).click();assert.equal(await page.locator('.gokul-mobile-launch').isVisible(),false,'route changes do not replay launch');}
  if(width<=640&&enabled){
   await page.goto(`${base}/checkout/payment/TEST-ORDER`);assert.equal(await page.locator('.gokul-mobile-launch').isVisible(),false);
   if(paymentStatus==='PAID'){await page.waitForURL('**/orders/TEST-ORDER');await page.locator('.mobile-order-detail').waitFor();}
   else if(paymentStatus==='PENDING'){await page.getByRole('heading',{name:'Checking your payment…',exact:true}).waitFor();assert.equal(await page.getByRole('heading',{name:'Payment',exact:true}).count(),0);await page.getByRole('button',{name:'Check Payment Status',exact:true}).waitFor();}
   else {await page.getByRole('heading',{name:'Payment didn’t complete',exact:true}).waitFor();assert.equal(await page.getByRole('heading',{name:'Payment',exact:true}).count(),0);await page.getByRole('link',{name:'View order',exact:true}).click();await page.locator('.mobile-order-detail').waitFor();}
  }
  assert.equal(mutations,0,'viewing/downloading an order never creates or cancels a payment');
  assert.equal(cancels,0);
  if(compact&&paymentStatus==='FAILED'){
   await page.getByRole('link',{name:'Retry checkout',exact:true}).click();await page.waitForURL('**/checkout/payment/TEST-ORDER');
   await page.getByRole('button',{name:'Retry checkout',exact:true}).click();await page.getByRole('alert').filter({hasText:'Provider check unavailable'}).waitFor();assert.equal(new URL(page.url()).pathname,'/checkout/payment/TEST-ORDER');
   cancelError=false;await page.getByRole('button',{name:'Retry checkout',exact:true}).click();
   await page.waitForURL(fulfillment==='DELIVERY'?'**/delivery/check':'**/checkout/review?paymentRecovery=failed');assert.equal(cancels,2);assert.equal(mutations,0);
  }
  await context.close();console.log(`Order browser: ${width} enabled=${enabled} ${paymentStatus} passed`);
 }
} finally {await browser.close();}
