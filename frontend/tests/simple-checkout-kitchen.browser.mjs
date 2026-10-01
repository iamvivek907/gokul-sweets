import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {mkdir} from 'node:fs/promises';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const browser=await chromium.launch({headless:true}),base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
const date=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());
const branch={id:1,code:'TEST',name:'Test branch',active:true,address:'Test address',city:'Test city',phone:'9000000000',openingTime:'08:00:00',closingTime:'23:59:00',pickupAvailable:true};
const product={id:1,name:'Test sweet',categoryId:1,categoryName:'Sweets',description:'Test item',price:100,imageUrl:null,available:true,saleMode:'UNIT'};
const slot={id:1,branchId:1,slotDate:date,startTime:'23:55:00',endTime:'23:59:00',active:true,remainingCapacity:100,capacity:100,priorityEnabled:false,priorityCharge:0};
try{for(const width of [1280,390]){
 const context=await browser.newContext({viewport:{width,height:900}}),page=await context.newPage();let quoteCalls=0,mutations=0,starts=0;
 const errors=[];page.on('pageerror',error=>errors.push(error.message));
 const row=(bucket)=>({orderNumber:'TEST-KITCHEN',customerName:'Test customer',fulfillmentType:'PICKUP',orderStatus:bucket==='PREPARING'?'PREPARING':'CONFIRMED',bucket,date,start:'18:00:00',end:'18:30:00',preparationAt:`${date}T17:00:00`});
 await context.route('**/api/**',async route=>{
  const req=route.request(),u=new URL(req.url()),p=u.pathname,headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Methods':'GET,POST,PUT,PATCH,OPTIONS','Access-Control-Allow-Headers':'content-type,idempotency-key,x-staff-csrf'};
  if(req.method()==='OPTIONS')return route.fulfill({status:204,headers});
  let json=[];
  if(p==='/api/storefront/features')json={simplifiedCheckout:true,bilingualStorefront:true,adminPreparationBoard:true,smartAvailability:true,smartPickupSelection:true,authoritativePickupCommitment:true,acceptedCheckoutQuote:true,persistentPickupContext:true,cartSwitchPreview:true,inPlaceBranchSwitch:true,accessibleOrderingV2:true,paymentPollingV2:true,checkoutExperienceV2:true,futuristicStorefrontV2:true,contextualStorefrontV2:true,futureOrderingDays:30,today:date};
  else if(p==='/api/storefront/customer-identity')json={enabled:true};
  else if(p==='/api/customer/identity/me')json={authenticated:true,phone:'+919876543210',name:''};
  else if(p==='/api/branches'||p==='/api/admin/branches')json=[branch];
  else if(p==='/api/branches/1')json=branch;
  else if(p.endsWith('/availability'))json={today:date,maximumDate:date,dates:[{date,available:true,slots:[{slot,normalAvailable:true,priorityAvailable:false}]}]};
  else if(p==='/api/orders/quote'){
   quoteCalls++;assert.equal(req.postDataJSON().customerName,'GOKUL_GUEST');assert.equal(req.postDataJSON().customerPhone,'9876543210');await new Promise(resolve=>setTimeout(resolve,500));
   json={token:'test',items:[],subtotal:'100',taxAmount:'0',priorityCharge:'0',convenienceFee:'0',convenienceFeeTax:'0',totalAmount:'100',currency:'INR',expiresAt:new Date(Date.now()+300000).toISOString()};
  }else if(p==='/api/orders'||p==='/api/payments'){mutations++;json={};}
  else if(p==='/api/admin/auth/me')return route.fulfill({json:{staffId:1,username:'test',fullName:'Test staff',roleName:'OWNER_ADMIN',branchIds:[1],permissions:['ORDER_VIEW','ORDER_START_PREPARATION']},headers:{...headers,'X-Staff-CSRF':'test-csrf','Access-Control-Expose-Headers':'X-Staff-CSRF'}});
  else if(p==='/api/admin/orders/planning'){
   const filter=u.searchParams.get('filter');json={orders:[row(starts?'PREPARING':filter==='SCHEDULED'?'SCHEDULED':'ELIGIBLE')],slots:[{date,start:'18:00:00',end:'18:30:00',fulfillmentType:'PICKUP',waiting:starts?0:1,preparing:starts?1:0,ready:0,total:1}],counts:{ALL:1,OVERDUE:0,ELIGIBLE:starts?0:1,SCHEDULED:1,PREPARING:starts?1:0,READY:0},page:0,total:1,generatedAt:`${date}T17:01:00`};
  }else if(p==='/api/admin/orders/queue/counts')json={overdue:0,eligible:starts?0:1,scheduled:1,preparing:starts?1:0,ready:0,actionableTotal:starts?0:1,confirmedTotal:1};
  else if(p==='/api/admin/orders/queue')json={orders:[],returnedCount:0,limit:50,hasMore:false};
  else if(p==='/api/admin/orders')json={orders:[],page:0,size:20,totalElements:0,totalPages:0};
  else if(p==='/api/admin/orders/queue/start-selected'){starts++;assert.deepEqual(req.postDataJSON().orderNumbers,['TEST-KITCHEN']);json={requested:1,attempted:1,started:1,skipped:0,results:[{orderNumber:'TEST-KITCHEN',result:'STARTED',message:'Started'}]};}
  else if(p==='/api/admin/notifications/settings')json={enabled:false};
  return route.fulfill({json,headers});
 });
 await context.addInitScript(({branch,product})=>{window.__chimes=0;window.AudioContext=class{currentTime=0;destination={};async resume(){}async close(){}createOscillator(){return {frequency:{value:0},connect(){},start(){window.__chimes++},stop(){}}}createGain(){return {gain:{setValueAtTime(){},exponentialRampToValueAtTime(){}},connect(){}}}};localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));localStorage.setItem('gokul-cart',JSON.stringify({branchId:1,items:[{product,quantity:1,weightGrams:null}]}));},{branch,product});
 await page.goto(`${base}/checkout/pickup`);await page.getByRole('button',{name:/23:55/}).click();await page.getByRole('button',{name:'Continue',exact:true}).filter({visible:true}).click();
 await page.waitForURL('**/checkout/review');await page.waitForFunction(()=>JSON.parse(localStorage.getItem('gokul-customer-details')).name==='GOKUL_GUEST');
 await page.getByRole('button',{name:/Accept price and reserve pickup|Review & reserve/}).filter({visible:true}).first().waitFor();assert.equal(quoteCalls,1);assert.equal(mutations,0);
 await page.getByLabel('Language / भाषा').selectOption('hi');await page.getByRole('button',{name:/राशि देखकर आगे बढ़ें|राशि स्वीकार करके आगे बढ़ें/}).waitFor();assert.equal(await page.locator('html').getAttribute('lang'),'hi');
 await page.reload();await page.getByRole('button',{name:/राशि देखकर आगे बढ़ें|राशि स्वीकार करके आगे बढ़ें/}).waitFor();assert.equal(await page.getByLabel('Language / भाषा').inputValue(),'hi');
 await page.getByLabel('Language / भाषा').selectOption('en');
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
 if(process.env.SCREENSHOT_DIR){await mkdir(process.env.SCREENSHOT_DIR,{recursive:true});await page.screenshot({path:`${process.env.SCREENSHOT_DIR}/simple-checkout-${width}.png`,fullPage:true});}
 await page.goto(`${base}/admin/orders`);await page.getByRole('heading',{name:'Plan, prepare, hand over'}).waitFor();
 await page.getByRole('button',{name:/Scheduled future/}).click();await page.waitForURL('**kitchen=SCHEDULED');await page.getByText('Scheduled future',{exact:true}).last().waitFor();assert.equal(await page.getByRole('checkbox',{name:'Select TEST-KITCHEN'}).count(),0);
 await page.reload();assert.match(page.url(),/kitchen=SCHEDULED/);await page.getByRole('heading',{name:'Plan, prepare, hand over'}).waitFor();
 await page.getByRole('button',{name:'Enable kitchen alarm'}).click();await page.waitForFunction(()=>window.__chimes>=6);
 await page.getByRole('button',{name:/Needs preparation/}).click();await page.getByRole('checkbox',{name:'Select TEST-KITCHEN'}).check();await page.getByRole('button',{name:'Start selected in KOT (1)'}).click();await page.getByRole('button',{name:'Confirm start'}).click();await page.getByText(/1 started · 0 skipped/).waitFor();assert.equal(starts,1);await page.getByText('No orders need preparation. Alarm is watching for new work.').waitFor();const stopped=await page.evaluate(()=>window.__chimes);await page.waitForTimeout(2500);assert.equal(await page.evaluate(()=>window.__chimes),stopped);
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
 if(process.env.SCREENSHOT_DIR)await page.screenshot({path:`${process.env.SCREENSHOT_DIR}/kitchen-board-${width}.png`,fullPage:true});assert.deepEqual(errors,[]);await context.close();
 }}finally{await browser.close();}
console.log('Verified contact, reduced checkout, Hindi persistence and kitchen filtering/KOT: desktop/mobile passed.');
