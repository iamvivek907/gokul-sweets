import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {mkdir} from 'node:fs/promises';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const browser=await chromium.launch({headless:true}),base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
// Start at a stable business time; the clock and alarm timers still advance.
const now=new Date('2026-10-01T06:30:00Z'),date='2026-10-01';
const branch={id:1,code:'TEST',name:'Test branch',active:true,address:'Test address',city:'Test city',phone:'9000000000',openingTime:'08:00:00',closingTime:'23:59:00',pickupAvailable:true};
const product={id:1,name:'Test sweet',categoryId:1,categoryName:'Sweets',description:'Test item',price:100,imageUrl:null,available:true,saleMode:'UNIT'};
const slot={id:1,branchId:1,slotDate:date,startTime:'23:55:00',endTime:'23:59:00',active:true,remainingCapacity:100,capacity:100,priorityEnabled:false,priorityCharge:0};
try{async function chooseLanguage(surface,locale){await surface.getByRole('button',{name:'Language / भाषा',exact:true}).click();await surface.getByRole('group',{name:'Language / भाषा',exact:true}).getByRole('button',{name:locale==='hi'?/हिन्दी/:/English/}).click();}
for(const width of [1280,390]){
 const context=await browser.newContext({viewport:{width,height:900}}),page=await context.newPage();await page.clock.install({time:now});let quoteCalls=0,mutations=0,starts=0,simplified=width===1280,visual=true,gateway=false;
 const errors=[];page.on('pageerror',error=>errors.push(error.message));
 const row=(bucket)=>({orderNumber:'TEST-KITCHEN',customerName:'Test customer',fulfillmentType:'PICKUP',orderStatus:bucket==='PREPARING'?'PREPARING':'CONFIRMED',bucket,date,start:'18:00:00',end:'18:30:00',preparationAt:`${date}T17:00:00`});
 await context.route('**/api/**',async route=>{
  const req=route.request(),u=new URL(req.url()),p=u.pathname,headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Methods':'GET,POST,PUT,PATCH,OPTIONS','Access-Control-Allow-Headers':'content-type,idempotency-key,x-staff-csrf'};
  if(req.method()==='OPTIONS')return route.fulfill({status:204,headers});
  let json=[];
  if(p==='/api/storefront/features')json={simplifiedCheckout:simplified,bilingualStorefront:width===1280,adminPreparationBoard:true,smartAvailability:true,smartPickupSelection:true,authoritativePickupCommitment:true,acceptedCheckoutQuote:true,persistentPickupContext:true,cartSwitchPreview:true,inPlaceBranchSwitch:true,accessibleOrderingV2:true,paymentPollingV2:true,checkoutExperienceV2:visual,futuristicStorefrontV2:visual,preHomeIntentGateway:gateway,contextualStorefrontV2:true,futureOrderingDays:30,today:date};
  else if(p==='/api/storefront/customer-identity')json={enabled:true};
  else if(p==='/api/customer/identity/me')json={authenticated:true,phone:'+919876543210',name:''};
  else if(p==='/api/admin/branches')return route.fulfill({status:403,json:{message:'BRANCH_MANAGE required'},headers});
  else if(p==='/api/branches')json=[branch,{...branch,id:2,name:'Unassigned branch'}];
  else if(p==='/api/branches/1')json=branch;
  else if(p.endsWith('/availability'))json={today:date,maximumDate:date,dates:[{date,available:true,slots:[{slot,normalAvailable:true,priorityAvailable:false}]}]};
  else if(p==='/api/orders/quote'){
   quoteCalls++;assert.equal(req.postDataJSON().customerName,'GOKUL_GUEST');assert.equal(req.postDataJSON().customerPhone,'9876543210');await new Promise(resolve=>setTimeout(resolve,500));
   json={token:'test',items:[],subtotal:'200',taxAmount:'0',priorityCharge:'0',convenienceFee:'0',convenienceFeeTax:'0',totalAmount:'200',currency:'INR',expiresAt:new Date(now.getTime()+300000).toISOString()};
  }else if(p==='/api/orders'||p==='/api/payments'){mutations++;json={};}
  else if(p==='/api/admin/auth/me')return route.fulfill({json:{staffId:1,username:'test',fullName:'Test staff',roleName:width===1280?'OWNER_ADMIN':'KITCHEN_STAFF',branchIds:[1],permissions:['ORDER_VIEW','ORDER_START_PREPARATION']},headers:{...headers,'X-Staff-CSRF':'test-csrf','Access-Control-Expose-Headers':'X-Staff-CSRF'}});
  else if(p==='/api/admin/orders/planning'){
   const filter=u.searchParams.get('filter');json={orders:[row(starts?'PREPARING':filter==='SCHEDULED'?'SCHEDULED':'ELIGIBLE')],slots:[{date,start:'18:00:00',end:'18:30:00',fulfillmentType:'PICKUP',waiting:starts?0:1,preparing:starts?1:0,ready:0,total:1}],counts:{ALL:1,OVERDUE:0,ELIGIBLE:starts?0:1,SCHEDULED:1,PREPARING:starts?1:0,READY:0},page:0,total:1,generatedAt:`${date}T17:01:00`};
  }else if(p==='/api/admin/orders/queue/counts'){if(width===390)assert.equal(u.searchParams.get('branchId'),'1');json={overdue:0,eligible:starts?0:1,scheduled:1,preparing:starts?1:0,ready:0,actionableTotal:starts?0:1,confirmedTotal:1};}
  else if(p==='/api/admin/orders/queue')json={orders:[],returnedCount:0,limit:50,hasMore:false};
  else if(p==='/api/admin/orders')json={orders:[],page:0,size:20,totalElements:0,totalPages:0};
  else if(p==='/api/admin/orders/queue/start-selected'){starts++;assert.deepEqual(req.postDataJSON().orderNumbers,['TEST-KITCHEN']);json={requested:1,attempted:1,started:1,skipped:0,results:[{orderNumber:'TEST-KITCHEN',result:'STARTED',message:'Started'}]};}
  else if(p==='/api/admin/notifications/settings')json={enabled:false};
  return route.fulfill({json,headers});
 });
 await context.addInitScript(({branch,product})=>{window.__chimes=0;window.AudioContext=class{currentTime=0;destination={};async resume(){}async close(){}createOscillator(){return {frequency:{value:0},connect(){},start(){window.__chimes++},stop(){}}}createGain(){return {gain:{setValueAtTime(){},exponentialRampToValueAtTime(){}},connect(){}}}};localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));localStorage.setItem('gokul-cart',JSON.stringify({branchId:1,items:[{product,quantity:2,weightGrams:null}]}));},{branch,product});
 await page.goto(`${base}/cart`);await page.getByText(/^2 items ready for review$/).waitFor();await chooseLanguage(page,'hi');await page.getByText(/^2 वस्तुएँ/).waitFor();await chooseLanguage(page,'en');
 await page.goto(`${base}/checkout/pickup`);await page.getByRole('group',{name:'Choose a pickup time'}).getByRole('button',{name:/23:55/}).click();await page.getByRole('button',{name:'Continue',exact:true}).filter({visible:true}).click();
 await page.waitForURL('**/checkout/review');await page.waitForFunction(()=>JSON.parse(localStorage.getItem('gokul-customer-details')).name==='GOKUL_GUEST');
 if(width===390)await page.getByRole('button',{name:'Check price & offers',exact:true}).click();
 await page.getByRole('button',{name:/Accept price and reserve pickup|Review & reserve|Continue to payment/}).filter({visible:true}).first().waitFor();assert.equal(quoteCalls,1);assert.equal(mutations,0);
 await chooseLanguage(page,'hi');await page.getByRole('button',{name:/राशि देखकर आगे बढ़ें|राशि स्वीकार करके आगे बढ़ें|भुगतान/}).filter({visible:true}).first().waitFor();assert.equal(await page.locator('html').getAttribute('lang'),'hi');
 simplified=true;await page.evaluate(()=>sessionStorage.removeItem("gokul-storefront-settings"));
 await page.reload();await page.getByRole('button',{name:/राशि देखकर आगे बढ़ें|राशि स्वीकार करके आगे बढ़ें|भुगतान/}).filter({visible:true}).first().waitFor();assert.equal(await page.locator('html').getAttribute('lang'),'hi');
 if(process.env.SCREENSHOT_DIR){await mkdir(process.env.SCREENSHOT_DIR,{recursive:true});await page.screenshot({path:`${process.env.SCREENSHOT_DIR}/hindi-checkout-${width}.png`,fullPage:true});}
 await chooseLanguage(page,'en');
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
 if(process.env.SCREENSHOT_DIR){await mkdir(process.env.SCREENSHOT_DIR,{recursive:true});await page.screenshot({path:`${process.env.SCREENSHOT_DIR}/simple-checkout-${width}.png`,fullPage:true});}
 if(width===390){
  visual=false;await page.evaluate(()=>sessionStorage.removeItem('gokul-storefront-settings'));await page.reload();
  await page.getByRole('button',{name:'Continue to payment',exact:true}).waitFor();assert.equal(await page.locator('.future-storefront').count(),0);
  assert.equal(await page.getByRole('button',{name:/Accept price and reserve pickup|Review & reserve|Continue to payment/}).count(),1,'one visible review action with both visual flags OFF');
 }
 await page.goto(`${base}/admin/orders`);await page.getByRole('heading',{name:'Plan, prepare, hand over'}).waitFor();
 await page.getByRole('button',{name:/Scheduled future/}).click();await page.waitForURL('**kitchen=SCHEDULED');await page.getByText('Scheduled future',{exact:true}).last().waitFor();assert.equal(await page.getByRole('checkbox',{name:'Select TEST-KITCHEN'}).count(),0);
 await page.reload();assert.match(page.url(),/kitchen=SCHEDULED/);await page.getByRole('heading',{name:'Plan, prepare, hand over'}).waitFor();
 await chooseLanguage(page,'hi');await page.getByRole('heading',{name:'योजना बनाएँ, तैयार करें, सौंपें'}).waitFor();await page.getByRole('button',{name:'रसोई का अलार्म चालू करें'}).waitFor();await chooseLanguage(page,'en');
 await page.getByRole('button',{name:'Enable kitchen alarm'}).click();await page.waitForFunction(()=>window.__chimes>=6);
 await page.getByRole('button',{name:/Needs preparation/}).click();await page.getByRole('checkbox',{name:'Select TEST-KITCHEN'}).check();await page.getByRole('button',{name:'Start selected in KOT (1)'}).click();await page.getByRole('button',{name:'Confirm start'}).click();await page.getByText(/1 started · 0 skipped/).waitFor();assert.equal(starts,1);await page.getByText('No orders need preparation. Alarm is watching for new work.').waitFor();const stopped=await page.evaluate(()=>window.__chimes);await page.waitForTimeout(2500);assert.equal(await page.evaluate(()=>window.__chimes),stopped);
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
 if(process.env.SCREENSHOT_DIR)await page.screenshot({path:`${process.env.SCREENSHOT_DIR}/kitchen-board-${width}.png`,fullPage:true});assert.deepEqual(errors,[]);
 if(width===390){
  visual=true;gateway=true;await page.evaluate(()=>sessionStorage.removeItem('gokul-storefront-settings'));await page.goto(`${base}/`);await page.locator('#gokul-arrival-title').waitFor();
  const topbar=page.getByRole('navigation',{name:'Welcome navigation'}).locator('..');
  for(const phoneWidth of [320,390]){await page.setViewportSize({width:phoneWidth,height:900});
   for(const locale of ['en','hi']){await chooseLanguage(topbar,locale);await page.waitForFunction(locale=>document.documentElement.lang===locale,locale);
    const fits=await topbar.evaluate(node=>{const box=node.getBoundingClientRect(),rects=Array.from(node.children).map(child=>child.getBoundingClientRect());return rects.every(r=>r.left>=box.left&&r.right<=box.right&&r.top>=box.top&&r.bottom<=box.bottom)&&rects.every((r,i)=>rects.slice(i+1).every(s=>r.right<=s.left||s.right<=r.left||r.bottom<=s.top||s.bottom<=r.top));});assert.equal(fits,true,`editorial controls fit without overlap at ${phoneWidth}px in ${locale}`);
   }
  }
 }
 await context.close();
 }}finally{await browser.close();}
console.log('Verified contact, reduced checkout, Hindi persistence and kitchen filtering/KOT: desktop/mobile passed.');
