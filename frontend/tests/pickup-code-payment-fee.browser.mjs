import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const browser=await chromium.launch({headless:true}),base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
try {
 for(const width of [390,1280]) {
  const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:'block'}),page=await context.newPage();
  let allowed=true,status='READY_FOR_PICKUP',attempts=0,saves=0,numberLookups=0;
  const branch={id:1,code:'TEST',name:'Pickup code branch',active:true,address:'Test address',city:'Test city',phone:'9000000000',pickupAvailable:true};
  const order=()=>({id:1,orderNumber:'TEST-PICKUP',customerOrderNumber:1,branchId:1,branchName:branch.name,branchAddress:branch.address,customerName:'Test customer',customerPhone:'9876543210',pickupDate:'2026-10-01',pickupStartTime:'18:00:00',pickupEndTime:'19:00:00',pickupType:'NORMAL',fulfillmentType:'PICKUP',orderStatus:status,paymentStatus:'PAID',items:[],subtotal:1000,taxAmount:0,priorityCharge:0,convenienceFee:5,convenienceFeeTax:0,paymentFee:20.1,paymentFeeTax:0,paymentFeeRate:2,totalAmount:1025.1,createdAt:'2026-10-01T12:00:00',updatedAt:'2026-10-01T12:00:00'});
  const row=()=>({orderNumber:'TEST-PICKUP',customerOrderNumber:1,customerName:'Test customer',fulfillmentType:'PICKUP',orderStatus:status,bucket:'READY',date:'2026-10-01',start:'18:00:00',end:'19:00:00',preparationAt:'2026-10-01T17:00:00'});
  await context.route('**/api/**',async route=>{
   const req=route.request(),p=new URL(req.url()).pathname,headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Methods':'GET,POST,PUT,PATCH,OPTIONS','Access-Control-Allow-Headers':'content-type,x-staff-csrf','X-Staff-CSRF':'test-csrf','Access-Control-Expose-Headers':'X-Staff-CSRF'};
   if(req.method()==='OPTIONS')return route.fulfill({status:204,headers});let json=[];
   if(p==='/api/storefront/features')json={adminPreparationBoard:true,futuristicStorefrontV2:true,branchExperience:true,acceptedCheckoutQuote:true};
   else if(p==='/api/admin/auth/me')json={staffId:1,username:'pickup',fullName:'Pickup staff',roleName:'KITCHEN_STAFF',branchIds:[1],permissions:['ORDER_VIEW','BRANCH_MANAGE',...(allowed?['ORDER_MARK_PICKED_UP']:[])]};
   else if(p==='/api/branches'||p==='/api/admin/branches')json=[branch];
   else if(p==='/api/admin/orders/planning')json={orders:status==='PICKED_UP'?[]:[row()],slots:[],counts:{ALL:1,READY:1},page:0,total:1,generatedAt:'2026-10-01T17:01:00'};
   else if(p==='/api/admin/orders/queue/counts')json={overdue:0,eligible:0,scheduled:0,preparing:0,ready:1,actionableTotal:0,confirmedTotal:1};
   else if(p==='/api/admin/orders/queue')json={orders:[],returnedCount:0,limit:50,hasMore:false};
   else if(p==='/api/admin/orders')json={orders:[],page:0,size:20,totalElements:0,totalPages:0};
   else if(p==='/api/admin/orders/number/1'){numberLookups++;json=order();}
   else if(p==='/api/admin/orders/TEST-PICKUP/status'){attempts++;const body=req.postDataJSON();assert.equal(body.status,'PICKED_UP');if(body.pickupCode!=='0042')return route.fulfill({status:400,json:{message:'Incorrect pickup code. Check the four digits with the customer.'},headers});status='PICKED_UP';json=order();}
   else if(p==='/api/admin/orders/TEST-PICKUP'||p==='/api/orders/TEST-PICKUP')json=order();
   else if(p==='/api/orders/TEST-PICKUP/pickup-code')json={code:'0042'};
   else if(p==='/api/admin/branches/1/payment-fee'){json={enabled:false,percentage:0,taxRate:0};if(req.method()==='PUT'){saves++;assert.deepEqual(req.postDataJSON(),{enabled:true,percentage:2,taxRate:18,reviewed:true});json={enabled:true,percentage:2,taxRate:18};}}
   else if(p==='/api/admin/branches/1/pickup-fee')json={amount:0,taxRate:0};
   else if(p==='/api/admin/branches/1/offerings')json={version:0,draft:[],published:[]};
   else if(p==='/api/admin/branches/1/experience')json={branchId:1,editVersion:0,publishedRevision:0};
   return route.fulfill({json,headers});
  });
  await page.goto(`${base}/admin/orders`);const thumb=page.getByRole('button',{name:'Verify pickup code',exact:true});await thumb.waitFor();await thumb.scrollIntoViewIfNeeded();const b=await thumb.boundingBox(),track=await thumb.locator('..').boundingBox();await page.mouse.move(b.x+24,b.y+24);await page.mouse.down();await page.mouse.move(b.x+24+track.width-56,b.y+24,{steps:10});await page.mouse.up();await page.getByRole('dialog',{name:'Verify pickup code'}).waitFor();assert.equal(attempts,0,'swiping only opens verification');await page.getByRole('dialog',{name:'Verify pickup code'}).getByText('Order #1',{exact:true}).waitFor();await page.getByRole('button',{name:'Cancel',exact:true}).click();assert.equal(status,'READY_FOR_PICKUP');
  await page.getByLabel('Search loaded orders',{exact:true}).fill('1');await page.getByRole('button',{name:'Find order #1 across pages',exact:true}).click();const drawer=page.getByRole('dialog',{name:'Order #1',exact:true});await drawer.getByRole('button',{name:'Verify pickup code',exact:true}).waitFor();assert.equal(numberLookups,1,'number lookup finds orders outside the loaded general-orders page');await drawer.getByRole('heading',{name:'#1',exact:true}).waitFor();assert.equal(await drawer.getByRole('button',{name:'Mark Picked Up',exact:true}).count(),0);await drawer.getByRole('button',{name:'Or tap to confirm',exact:true}).click();await page.getByRole('button',{name:'Enter pickup code',exact:true}).click();const popup=page.getByRole('dialog',{name:'Verify pickup code'});await popup.waitFor();await popup.getByLabel('Pickup code',{exact:true}).fill('9999');await popup.getByRole('button',{name:'Confirm pickup'}).click();await popup.getByRole('alert').waitFor();assert.equal(status,'READY_FOR_PICKUP');assert.equal(attempts,1);await popup.getByLabel('Pickup code',{exact:true}).fill('0042');await popup.getByRole('button',{name:'Confirm pickup'}).click();await popup.waitFor({state:'hidden'});assert.equal(status,'PICKED_UP');assert.equal(attempts,2);
  status='READY_FOR_PICKUP';allowed=false;await page.reload();await page.getByRole('heading',{name:'Plan, prepare, hand over'}).waitFor();assert.equal(await page.getByRole('button',{name:'Verify pickup code',exact:true}).count(),0,'staff without pickup permission has no swipe');
  await page.goto(`${base}/orders/TEST-PICKUP`);await page.getByRole('region',{name:'Pickup code'}).getByText('0042',{exact:true}).waitFor();await page.getByText('Online payment fee',{exact:false}).first().waitFor();
  await page.goto(`${base}/admin/branches`);await page.getByRole('button',{name:/Pickup code branch/}).first().click();const settings=page.getByRole('region',{name:'Online payment fee settings'});await settings.getByLabel('Payment fee (%)',{exact:true}).waitFor();await settings.getByLabel('Enable online payment fee').check();await settings.getByLabel('Payment fee (%)',{exact:true}).fill('2');await settings.getByLabel('Payment fee tax rate (%)',{exact:true}).fill('18');assert.equal(await settings.getByRole('button',{name:'Save payment fee'}).isDisabled(),true);await settings.getByLabel('Provider terms and fee tax treatment have been reviewed').check();await settings.getByRole('button',{name:'Save payment fee'}).click();await settings.getByRole('status').waitFor();assert.equal(saves,1);assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
  await context.close();
 }
 console.log('PASS: staff pickup swipe, code dialog, cancellation, wrong and leading-zero codes, permission gating, customer code and reviewed percentage fee settings at desktop/mobile widths.');
}finally{await browser.close();}
