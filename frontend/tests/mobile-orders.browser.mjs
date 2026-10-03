import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {execFileSync} from 'node:child_process';
import {mkdir, readFile} from 'node:fs/promises';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const browser=await chromium.launch({headless:true}),base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
const branch={id:1,name:'Gokul Test branch',code:'TEST',address:'Test address',phone:'9876543210',active:true,pickupAvailable:true};
const date=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata'}).format(new Date(Date.now()+86400000));
try {
 for (const [width,enabled,paymentStatus,fulfillment='PICKUP'] of [[390,true,'PAID'],[640,true,'PAID'],[1280,true,'PAID'],[390,false,'PAID'],[390,true,'FAILED'],[390,true,'PENDING'],[640,true,'PENDING'],[1280,true,'PENDING'],[390,false,'PENDING'],[390,true,'PENDING','DELIVERY'],[390,true,'PAID','DELIVERY'],[390,true,'FAILED','DELIVERY']]) {
  const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:'block',acceptDownloads:true}),page=await context.newPage();
  const order={orderNumber:'TEST-ORDER',branchId:1,branchName:branch.name,branchAddress:branch.address,branchPhone:branch.phone,branchFssaiLicenceNumber:'12345678901234',orderStatus:paymentStatus==='PAID'?'CONFIRMED':paymentStatus==='FAILED'?'PAYMENT_FAILED':'PENDING_PAYMENT',paymentStatus,fulfillmentType:'PICKUP',pickupDate:date,pickupStartTime:'18:00:00',pickupEndTime:'19:00:00',pickupType:'NORMAL',customerName:'Test customer',maskedCustomerPhone:'******3210',items:[{id:1,productId:1,productName:'पेडा / பால்கோவா / Milk sweet',saleMode:'WEIGHT',quantity:0,weightGrams:500,unitPrice:200,taxRate:0,taxAmount:0,lineTotal:100}],subtotal:100,taxAmount:0,priorityCharge:0,convenienceFee:10,convenienceFeeTax:0,paymentFee:2,paymentFeeTax:0,paymentFeeRate:2,totalAmount:92,reservationExpiresAt:new Date(Date.now()+600000).toISOString(),createdAt:new Date().toISOString(),updatedAt:new Date().toISOString()};
  // Customer responses may have no branch address; both forms must still download a paid invoice.
  if(width===390&&enabled&&paymentStatus==='PAID'&&fulfillment==='PICKUP')order.branchAddress=null;
  if(width===640){delete order.branchAddress;order.items=Array.from({length:40},(_,i)=>({...order.items[0],id:i+1,productId:i+1,productName:`पेडा / பால்கோவா / Milk sweet ${i+1}`}));order.subtotal=4000;order.totalAmount=3992;}
  if(fulfillment==='DELIVERY'){Object.assign(order,{fulfillmentType:'DELIVERY',pickupDate:null,pickupStartTime:null,pickupEndTime:null,pickupType:null,deliveryDate:date,deliveryStartTime:'18:00:00',deliveryEndTime:'19:00:00',deliveryAddressLine:'12 Main Road',deliveryLocality:'Test locality',deliveryPostalCode:'226001',deliveryFee:30,totalAmount:122});}
  const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Methods':'GET,POST,OPTIONS','Access-Control-Allow-Headers':'content-type,idempotency-key'};
  let mutations=0,cancels=0,cancelError=true,cancelPaid=false,failRefresh=false,refreshes=0;
  let fontError=width===390&&enabled&&paymentStatus==='PAID'&&fulfillment==='PICKUP';
  await context.route('**/fonts/invoice-v1/**',route=>fontError?route.fulfill({status:503,body:'Unavailable'}):route.continue());
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
   else if(path==='/api/payments/10/refresh'){refreshes++;if(failRefresh)return route.fulfill({status:503,json:{message:'Temporary provider failure'},headers});json={paymentId:10,orderNumber:order.orderNumber,provider:'PHONEPE',paymentStatus,amount:order.totalAmount,currency:'INR'};
   }
   else if(path==='/api/payments/10/cancel-checkout'){cancels++;if(cancelError)return route.fulfill({status:503,json:{message:'Provider check unavailable'},headers});if(cancelPaid){order.paymentStatus='PAID';order.orderStatus='CONFIRMED';}json={paymentId:10,orderNumber:order.orderNumber,provider:'PHONEPE',paymentStatus:cancelPaid?'PAID':'FAILED',amount:order.totalAmount,currency:'INR'};}
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
   const reference=page.locator('.mobile-order-detail header [data-copyable]');
   assert.equal(await reference.textContent(),order.orderNumber);
   assert.equal(await reference.evaluate(e=>getComputedStyle(e).userSelect),'text','order reference remains selectable in the compact themed view');
   assert.equal(await page.locator('.mobile-order-detail').evaluate(e=>e.scrollWidth<=e.clientWidth),true);
   if(paymentStatus==='FAILED')assert.equal(await page.getByRole('link',{name:'Retry checkout',exact:true}).getAttribute('href'),'/checkout/payment/TEST-ORDER');
   assert.equal(await page.getByRole('button',{name:'Download invoice',exact:true}).count(),paymentStatus==='PAID'?1:0);
   if(paymentStatus==='PAID'){
    if(fulfillment==='PICKUP')await page.getByLabel('Pickup code: 1 2 3 4',{exact:true}).waitFor();
    if(fulfillment==='DELIVERY')await page.getByText('Delivery fee',{exact:true}).waitFor();
    await page.getByText('Offer savings',{exact:true}).waitFor();
    if(fontError){await page.getByRole('button',{name:'Download invoice',exact:true}).click();await page.getByRole('alert').filter({hasText:'Unable to download the invoice'}).waitFor();fontError=false;}
    const downloadPromise=page.waitForEvent('download');await page.getByRole('button',{name:'Download invoice',exact:true}).click();
    const download=await downloadPromise;assert.equal(download.suggestedFilename(),'Gokul-TEST-ORDER-invoice.pdf');
    const data=await readFile(await download.path());assert.equal(data.subarray(0,8).toString(),'%PDF-1.7');assert.ok(data.includes(Buffer.from('/Type /Page')));
    const invoiceText=execFileSync('pdftotext',[await download.path(),'-'],{encoding:'utf8'});
    const invoiceInfo=execFileSync('pdfinfo',[await download.path()],{encoding:'utf8'});
    assert.match(invoiceInfo,/Tagged:\s+yes/);assert.match(invoiceInfo,/Suspects:\s+no/);
    for(const marker of ['/StructTreeRoot','/S /Document','/S /H1','/S /H2','/S /P','/ToUnicode','/FontFile2'])assert.ok(data.includes(Buffer.from(marker)),marker);
    assert.ok(invoiceText.includes(branch.name));assert.ok(invoiceText.includes(`PAID TOTAL: INR ${order.totalAmount.toFixed(2)}`));
    assert.ok(invoiceText.includes(order.items[0].productName),'Hindi and Tamil names copy in logical Unicode order');
    if(!order.branchAddress)assert.equal(/\b(null|undefined)\b/u.test(invoiceText),false,'missing address is omitted from invoice');
    if(fulfillment==='DELIVERY')assert.ok(invoiceText.includes('Delivery fee: INR 30.00'));
    if(width===640){assert.ok(Number(invoiceInfo.match(/Pages:\s+(\d+)/)[1])>=2,'large invoices paginate');
      for(const text of invoiceText.split('\f').filter(text=>text.trim())){
        assert.doesNotMatch(text.trim(),/^500 g @/u,'a page cannot start with an orphaned item quantity');
        assert.doesNotMatch(text.trim(),/Milk sweet \d+$/u,'a page cannot end with an orphaned item name');
      }
      for(const item of order.items){assert.equal(invoiceText.split(`${item.productName} \n`).length-1,1,'each item appears once across page breaks');}
      assert.ok(invoiceText.indexOf(order.items.at(-1).productName)<invoiceText.indexOf('PAID TOTAL:'));
    }
    if(process.env.SCREENSHOT_DIR){await mkdir(process.env.SCREENSHOT_DIR,{recursive:true});await download.saveAs(`${process.env.SCREENSHOT_DIR}/order-invoice-${width}-${fulfillment}.pdf`);}
   }
  }
  await page.evaluate(()=>window.scrollTo(0,0));
  if(process.env.SCREENSHOT_DIR){await mkdir(process.env.SCREENSHOT_DIR,{recursive:true});await page.screenshot({path:`${process.env.SCREENSHOT_DIR}/order-${width}-${enabled}-${paymentStatus}.png`,fullPage:true});}
  await page.goto(`${base}/orders`);await page.getByRole('heading',{name:'My Orders',exact:true}).waitFor();
  await page.locator(compact?'.mobile-order-card':'article').first().waitFor();
  assert.equal(await page.locator('.mobile-order-card').count(),compact?1:0);
  if(compact){await page.getByRole('link',{name:'View order TEST-ORDER →',exact:true}).click();await page.locator('.mobile-order-detail').waitFor();await page.getByRole('link',{name:'← My orders',exact:true}).click();assert.equal(await page.locator('.gokul-mobile-launch').isVisible(),false,'route changes do not replay launch');}
  if((width<=640&&enabled)||paymentStatus==='PENDING'){
   await page.goto(`${base}/checkout/payment/TEST-ORDER`);assert.equal(await page.locator('.gokul-mobile-launch').isVisible(),false);
   if(paymentStatus==='PAID'){await page.waitForURL('**/orders/TEST-ORDER');await page.locator('.mobile-order-detail').waitFor();}
   else if(paymentStatus==='PENDING'){await page.getByRole('heading',{name:compact?'Checking your payment…':'Payment',exact:true}).waitFor();if(compact)assert.equal(await page.getByRole('heading',{name:'Payment',exact:true}).count(),0);await page.getByRole('button',{name:'Check Payment Status',exact:true}).waitFor();
    failRefresh=true;await page.getByRole('button',{name:'Check Payment Status',exact:true}).click();
    await page.getByRole('status').filter({hasText:"We couldn't confirm the latest payment status yet."}).waitFor();
    const attempts=refreshes;await page.getByRole('button',{name:'Check Payment Status',exact:true}).click();
    await page.getByRole('status').filter({hasText:'We checked recently. Please wait a moment before checking again.'}).waitFor();assert.equal(refreshes,attempts,'cooldown explains why no provider check is sent');
   }
   else {await page.getByRole('heading',{name:'Payment didn’t complete',exact:true}).waitFor();assert.equal(await page.getByRole('heading',{name:'Payment',exact:true}).count(),0);await page.getByRole('link',{name:'View order',exact:true}).click();await page.locator('.mobile-order-detail').waitFor();}
  }
  assert.equal(mutations,0,'viewing/downloading an order never creates or cancels a payment');
  assert.equal(cancels,0);
  if(paymentStatus==='PENDING'){
   const paymentPath='/checkout/payment/TEST-ORDER';
   const leave=page.getByRole('dialog',{name:'Before you leave payment',exact:true});
   await page.locator('a[href="/orders"]:visible').first().click();await leave.waitFor();
   assert.equal(new URL(page.url()).pathname,paymentPath);assert.equal(cancels,0);
   await leave.getByRole('button',{name:'Stay & retry payment',exact:true}).click();await leave.waitFor({state:'hidden'});
   await page.locator('a[href="/orders"]:visible').first().click();await leave.waitFor();
   await leave.getByRole('button',{name:'Leave & keep order for later',exact:true}).click();await page.waitForURL('**/orders');assert.equal(cancels,0);
   await page.goto(`${base}${paymentPath}`);await page.getByRole('heading',{name:compact?'Checking your payment…':'Payment',exact:true}).waitFor();
   await page.locator('a[href="/"]:visible').first().click();await leave.waitFor();assert.equal(new URL(page.url()).pathname,paymentPath);
   await leave.getByRole('button',{name:'Stay & retry payment',exact:true}).click();await leave.waitFor({state:'hidden'});
   await page.locator(compact?'a[href="/orders/TEST-ORDER"]:visible':'a[href="/orders"]:visible').first().click();await leave.waitFor();
   await leave.getByRole('button',{name:'Cancel order & keep cart',exact:true}).click();await leave.getByRole('alert').filter({hasText:'Provider check unavailable'}).waitFor();
   assert.equal(new URL(page.url()).pathname,paymentPath);assert.equal(cancels,1,'failed provider check preserves the pending payment');
   cancelError=false;cancelPaid=width===640;
   await leave.getByRole('button',{name:'Cancel order & keep cart',exact:true}).click();
   await page.waitForURL(cancelPaid?'**/orders/TEST-ORDER':fulfillment==='DELIVERY'?'**/delivery/check':width<=640?'**/checkout/review?paymentRecovery=failed':'**/checkout/review');
   assert.equal(cancels,2);assert.equal(mutations,0,'leaving payment cannot create another order or payment');
  }
  if(compact&&paymentStatus==='FAILED'){
   await page.getByRole('link',{name:'Retry checkout',exact:true}).click();await page.waitForURL('**/checkout/payment/TEST-ORDER');
   await page.getByRole('button',{name:'Retry checkout',exact:true}).click();await page.getByRole('alert').filter({hasText:'Provider check unavailable'}).waitFor();assert.equal(new URL(page.url()).pathname,'/checkout/payment/TEST-ORDER');
   cancelError=false;await page.getByRole('button',{name:'Retry checkout',exact:true}).click();
   await page.waitForURL(fulfillment==='DELIVERY'?'**/delivery/check':'**/checkout/review?paymentRecovery=failed');assert.equal(cancels,2);assert.equal(mutations,0);
  }
  await context.close();console.log(`Order browser: ${width} enabled=${enabled} ${paymentStatus} passed`);
 }
} finally {await browser.close();}
