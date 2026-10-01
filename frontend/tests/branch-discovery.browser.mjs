import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {mkdir} from 'node:fs/promises';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const browser=await chromium.launch({headless:true}),base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
const branch={id:1,code:'TEST',name:'Test Gokul branch',active:true,address:'Test address',city:'Test city',phone:'9000000000',openingTime:'08:00:00',closingTime:'22:00:00',pickupAvailable:true};
const product=(id,name,categoryId,categoryName,price)=>({id,name,categoryId,categoryName,description:'Synthetic menu item',price,imageUrl:null,available:true,saleMode:'UNIT',minimumWeightGrams:null,weightStepGrams:null});
const main=product(1,'Test meal',2,'Meals',340),addon=product(2,'Test sweet',1,'Sweets',140),drink=product(3,'Test drink',3,'Drinks',20);
const offer={rebateId:1,code:'TESTSAVE',name:'Test spend saving',scope:'GENERAL',rebateType:'SLAB',description:null,rebateAmount:69,payableAfterRebate:293,minimumOrderAmount:100,maximumDiscountAmount:100,nextSlabMinimumOrderAmount:500,nextSlabRebateAmount:100,amountNeededForNextSlab:143};
try {
 for(const width of [390,1440]) {
  const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:'block'}),page=await context.newPage();
  const expires=new Date(Date.now()+14*60000).toISOString(),date=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata'}).format(new Date(Date.now()+86400000));
  const slot={id:1,branchId:1,slotDate:date,startTime:'18:00:00',endTime:'19:00:00',capacity:50,bookedCount:1,remainingCapacity:49,priorityCapacity:0,priorityBookedCount:0,remainingPriorityCapacity:0,priorityCharge:0,active:true};
  let quoteFailure=true;
  let updated=false,quoteCalls=0,updateCalls=0,checkCalls=0,paymentCalls=0,version=0,conflict=false;
  const line=p=>({id:p.id,productId:p.id,productName:p.name,saleMode:'UNIT',quantity:1,weightGrams:null,unitPrice:p.price,taxRate:5,taxAmount:p.price*.05,lineTotal:p.price*1.05});
  const order=()=>({id:1,orderNumber:'GKS-SYNTHETIC',branchId:1,pickupSlotId:1,branchName:branch.name,branchAddress:branch.address,pickupDate:date,pickupStartTime:'18:00:00',pickupEndTime:'19:00:00',pickupType:'NORMAL',fulfillmentType:'PICKUP',customerName:'Test customer',maskedCustomerPhone:'******0000',orderStatus:'PENDING_PAYMENT',paymentStatus:null,items:updated?[line(main),line(addon)]:[line(main)],subtotal:updated?480:340,taxAmount:updated?24:17,priorityCharge:0,convenienceFee:5,convenienceFeeTax:0,totalAmount:updated?509:362,reservationExpiresAt:expires,createdAt:new Date().toISOString(),updatedAt:new Date().toISOString()});
  const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','X-Staff-CSRF':'csrf','Access-Control-Expose-Headers':'X-Staff-CSRF'};
  await context.route('**/api/**',async route=>{
   const req=route.request(),u=new URL(req.url()),p=u.pathname;let json=[];
   if(req.method()==='OPTIONS')return route.fulfill({status:204,headers:{...headers,'Access-Control-Allow-Methods':'GET,POST,PUT,DELETE,OPTIONS','Access-Control-Allow-Headers':'content-type,x-staff-csrf,if-match'}});
   if(p==='/api/storefront/features')json={futuristicStorefrontV2:true,contextualStorefrontV2:true,branchExperience:true,pickupAddOns:true,acceptedCheckoutQuote:true,checkoutExperienceV2:true,futureOrderingDays:30,today:date};
   else if(p==='/api/branches')json=[branch];
   else if(p==='/api/branches/1')json=branch;
   else if(p==='/api/branches/1/discovery')json={offerings:[{title:'Configured branch speciality',description:'A published description supplied by the branch.'}],overallExperience:{average:4.3,count:12},topRatedItems:[{productId:2,name:addon.name,imageUrl:null,categoryId:1,average:4.6,count:7,review:{comment:'Carefully packed and a pleasant pickup experience.',overallRating:5}}]};
   else if(p==='/api/menu')json=[{id:2,name:'Meals',description:'Meal choices',displayOrder:0,products:[main]},{id:1,name:'Sweets',description:'Sweet choices',displayOrder:1,products:[addon]},{id:3,name:'Drinks',displayOrder:2,products:[drink]}];
   else if(p==='/api/reviews/product-summaries')json=[];
   else if(p==='/api/orders/GKS-SYNTHETIC')json=order();
   else if(p.endsWith('/rebate-spend-targets'))json=updated?[]:[offer];
   else if(p.endsWith('/available-rebates'))json=[updated?{...offer,rebateAmount:100,amountNeededForNextSlab:null,nextSlabRebateAmount:null}:offer];
   else if(p==='/api/menu/pickup-addons')json=updated?[]:[{product:addon,weightGrams:null,portionPrice:140,portionTotal:147,reason:'Often ordered with Test meal'}];
   else if(p==='/api/menu/pickup-addons/check'){assert.equal(u.searchParams.get('orderNumber'),'GKS-SYNTHETIC');assert.equal(req.postDataJSON().items.length,2);checkCalls++;json={orderable:true};}
   else if(p==='/api/orders/GKS-SYNTHETIC/quote'){quoteCalls++;assert.equal(req.postDataJSON().items.length,2);if(quoteFailure)return route.fulfill({status:409,json:{message:'Pickup availability changed. Reduce quantity or choose another pickup time.'},headers});json={token:'test-quote',subtotal:'480.00',taxAmount:'24.00',priorityCharge:'0.00',convenienceFee:'5.00',convenienceFeeTax:'0.00',totalAmount:'509.00',currency:'INR',expiresAt:expires,items:[]};}
   else if(p==='/api/orders/GKS-SYNTHETIC/checkout'){updateCalls++;assert.equal(req.method(),'PUT');assert.equal(req.postDataJSON().quoteToken,'test-quote');updated=true;json=order();}
   else if(p==='/api/payments'){paymentCalls++;json={};}
   else if(p==='/api/admin/auth/me')json={staffId:1,username:'test',fullName:'Test admin',roleName:'OWNER_ADMIN',branchIds:[1],permissions:['BRANCH_MANAGE','MENU_MANAGE']};
   else if(p==='/api/admin/branches')json=[branch];
   else if(p==='/api/admin/branches/1/offerings') {
    if(req.method()==='PUT'){assert.equal(req.headers()['if-match'],String(version));if(conflict)return route.fulfill({status:409,json:{message:'Branch offerings changed. Reload before saving.'},headers});version++;}
    json={version,draft:[{title:'Draft offering',description:'Private draft description'}],published:[]};
   }
   else if(p==='/api/admin/branches/1/experience')json={branchId:1,editVersion:0,publishedRevision:0};
   else if(p.endsWith('/availability'))json={today:date,maximumDate:date,dates:[{date,available:true,slots:[{slot,normalAvailable:true,priorityAvailable:false}]}]};
   else if(p.includes('inventory'))json={orderable:true,available:true,items:[],pickupSlots:[slot],earliestDate:date};
   return route.fulfill({json,headers});
  });
  await context.addInitScript(({branch,main,slot,date,expires})=>{
   if(localStorage.getItem('gokul-cart'))return;
   localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));localStorage.setItem('gokul-cart',JSON.stringify({branchId:1,items:[{product:main,quantity:1,weightGrams:null}]}));
   localStorage.setItem('gokul-customer-details',JSON.stringify({name:'Test customer',phone:'9000000000'}));localStorage.setItem('gokul-selected-pickup-slot',JSON.stringify({date,slot,pickupType:'NORMAL'}));
   localStorage.setItem('gokul-pending-order',JSON.stringify({orderId:1,orderNumber:'GKS-SYNTHETIC',orderStatus:'PENDING_PAYMENT',branchId:1,pickupSlotId:1,totalAmount:362,reservationExpiresAt:expires,createdAt:new Date().toISOString(),cartFingerprint:'1:UNIT:1:-'}));
  },{branch,main,slot,date,expires});
  await page.goto(`${base}/branches/1`);await page.getByRole('heading',{name:'Configured branch speciality'}).waitFor();await page.getByText('12 published reviews · overall experience').waitFor();await page.getByText('Carefully packed and a pleasant pickup experience.').waitFor();assert.equal(await page.getByText('Private customer').count(),0);
  if(process.env.SCREENSHOT_DIR){await mkdir(process.env.SCREENSHOT_DIR,{recursive:true});await page.screenshot({path:`${process.env.SCREENSHOT_DIR}/branch-discovery-${width}.png`,fullPage:true});}
  await page.goto(`${base}/menu`);await page.getByRole('region',{name:'Meals menu items'}).waitFor();assert.equal(await page.getByRole('region',{name:'Sweets menu items'}).getByText('Test sweet',{exact:true}).count(),1);assert.equal(await page.getByRole('region',{name:'Meals menu items'}).getByText('Test sweet',{exact:true}).count(),0);
  if(process.env.SCREENSHOT_DIR)await page.screenshot({path:`${process.env.SCREENSHOT_DIR}/grouped-menu-${width}.png`,fullPage:true});
  await page.goto(`${base}/checkout/review`);await page.getByRole('heading',{name:'Save on this order'}).waitFor();await page.getByRole('button',{name:'Find available offers'}).click();await page.getByText('Test spend saving',{exact:true}).waitFor();await page.getByRole('button',{name:'Add Test sweet',exact:true}).waitFor();
  const addY=await page.getByRole('button',{name:'Add Test sweet',exact:true}).evaluate(node=>node.getBoundingClientRect().top+scrollY),codeY=await page.getByText('Have a creator or exclusive code?',{exact:true}).evaluate(node=>node.getBoundingClientRect().top+scrollY);assert.ok(addY<codeY,'add-ons must be next to offer cards before the code and payment controls');
  if(width===390)assert.equal(await page.getByRole('complementary',{name:'Checkout action'}).evaluate(node=>getComputedStyle(node).position),'fixed');
  if(process.env.SCREENSHOT_DIR)await page.screenshot({path:`${process.env.SCREENSHOT_DIR}/offer-addons-${width}.png`,fullPage:true});
  await page.getByRole('button',{name:'Add Test sweet',exact:true}).click();await page.getByText('We couldn’t complete this addition.',{exact:false}).waitFor();
  assert.equal(new URL(page.url()).pathname,'/checkout/review');assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-cart')).items.length),1);assert.equal(await page.getByRole('checkbox',{name:'I have reviewed the updated total and offers.'}).isDisabled(),true);assert.equal(await page.getByRole('button',{name:'Continue to Payment',exact:true}).isDisabled(),true);
  await page.getByRole('button',{name:'Adjust quantities or pickup here',exact:true}).click();await page.getByRole('dialog').waitFor();await page.getByRole('button',{name:'Keep current pickup',exact:true}).click();quoteFailure=false;
  await page.getByRole('button',{name:'Add Test sweet',exact:true}).click();await page.getByText('Addition checked. Review the updated total and choose any available offer before payment.',{exact:true}).waitFor();await page.getByRole('checkbox',{name:'I have reviewed the updated total and offers.'}).waitFor();
  assert.equal(checkCalls,2);assert.equal(quoteCalls,2);assert.equal(updateCalls,1);assert.equal(paymentCalls,0);assert.equal(await page.getByRole('button',{name:'Continue to Payment',exact:true}).isDisabled(),true);
  assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-pending-order')).reservationExpiresAt),expires);assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-pending-order')).totalAmount),509);
  await page.getByRole('checkbox',{name:'I have reviewed the updated total and offers.'}).check();assert.equal(await page.getByRole('button',{name:'Continue to Payment',exact:true}).isEnabled(),true);
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
  await page.getByRole('button',{name:'Adjust quantities or pickup',exact:true}).click();await page.getByLabel('Test meal quantity',{exact:true}).fill('2');quoteFailure=true;
  await page.getByRole('button',{name:'Check & apply changes',exact:true}).click();await page.getByRole('dialog').getByRole('alert').filter({hasText:'Pickup availability changed'}).waitFor();
  assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-cart')).items[0].quantity),1);assert.equal(updateCalls,1);assert.equal(paymentCalls,0);
  await page.getByRole('button',{name:'Keep current pickup',exact:true}).click();assert.equal(new URL(page.url()).pathname,'/checkout/review');
  await page.goto(`${base}/checkout/offers/GKS-SYNTHETIC`);await page.getByRole('button',{name:'Adjust quantities or pickup',exact:true}).waitFor();await page.getByRole('button',{name:'Adjust quantities or pickup',exact:true}).click();await page.getByRole('dialog').waitFor();await page.getByRole('button',{name:'Keep current pickup',exact:true}).click();
  await page.goto(`${base}/admin/branches`);await page.getByRole('button',{name:/Test Gokul branch/}).first().click();await page.getByLabel('Offering 1 title',{exact:true}).fill('Updated draft');conflict=true;await page.getByRole('button',{name:'Save offerings draft'}).click();await page.getByRole('alert').filter({hasText:'changed'}).waitFor();assert.equal(version,0);
  await context.close();
 }
 console.log('PASS: branch offerings, scoped rating/review display, grouped menu, offer-adjacent add-ons, own-reservation check, signed quote refresh, unchanged expiry, mobile fixed action and required total review.');
}finally{await browser.close();}
