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
try{async function chooseLanguage(surface,locale){await surface.getByRole('button',{name:'Language / भाषा',exact:true}).click();const options=(typeof surface.page==='function'?surface.page():surface).getByRole('group',{name:'Language / भाषा',exact:true});for(const label of [/English/,/हिन्दी/])assert.equal(await options.getByRole('button',{name:label}).evaluate(node=>{const r=node.getBoundingClientRect();return r.top>=0&&r.bottom<=innerHeight&&node.contains(document.elementFromPoint(r.left+r.width/2,r.top+r.height/2));}),true,'both language options are visible and unobstructed');await options.getByRole('button',{name:locale==='hi'?/हिन्दी/:/English/}).click();}
for(const width of [1280,390]){
 const context=await browser.newContext({viewport:{width,height:900}}),page=await context.newPage();await page.clock.install({time:now});let quoteCalls=0,mutations=0,starts=0,readyMoves=0,bestOffers=0,simplified=width===1280,visual=true,gateway=false;
 const errors=[];page.on('pageerror',error=>errors.push(error.message));let authenticated=false;
 const row=(bucket)=>({orderNumber:'TEST-KITCHEN',customerOrderNumber:42,customerName:'Test customer',fulfillmentType:'PICKUP',orderStatus:readyMoves?'READY_FOR_PICKUP':starts?'PREPARING':'CONFIRMED',bucket,date,start:'18:00:00',end:'18:30:00',preparationAt:`${date}T17:00:00`});
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
  }else if(p==='/api/orders'&&req.method()==='POST'){mutations++;assert.equal(req.postDataJSON().customerName,'GOKUL_GUEST');json={id:11,orderNumber:'TEST-CHECKOUT',orderStatus:'PENDING_PAYMENT',branchId:1,pickupSlotId:1,totalAmount:200,reservationExpiresAt:`${date}T23:59:00`,createdAt:`${date}T12:00:00`};}
  else if(p==='/api/orders/TEST-CHECKOUT/rebate/best'){bestOffers++;assert.equal(req.method(),'POST');await new Promise(resolve=>setTimeout(resolve,500));if(width===390)return route.fulfill({status:503,json:{message:'Offer service unavailable'},headers});json={orderNumber:'TEST-CHECKOUT',rebateCode:'BEST',rebateName:'Best offer',rebateAmount:20,amountBeforeRebate:200,totalAmount:180};}
  else if(p==='/api/orders/TEST-CHECKOUT')json={id:11,orderNumber:'TEST-CHECKOUT',orderStatus:'PENDING_PAYMENT',paymentStatus:null,branchId:1,branchName:branch.name,pickupSlotId:1,pickupDate:date,pickupStartTime:slot.startTime,pickupEndTime:slot.endTime,pickupType:'NORMAL',customerName:'GOKUL_GUEST',customerPhone:'9876543210',subtotal:200,taxAmount:0,priorityCharge:0,totalAmount:width===390?200:180,reservationExpiresAt:`${date}T23:59:00`,items:[{id:1,productId:1,productName:product.name,saleMode:'UNIT',quantity:2,weightGrams:null,unitPrice:100,taxRate:0,taxAmount:0,lineTotal:200}]};
  else if(p==='/api/payments'){mutations++;json={};}
  else if(p==='/api/admin/auth/me'){if(!authenticated)return route.fulfill({status:401,json:{message:'Sign in required'},headers});return route.fulfill({json:{staffId:1,username:'test',fullName:'Test staff',roleName:width===1280?'OWNER_ADMIN':'KITCHEN_STAFF',branchIds:[1],permissions:['ORDER_VIEW','ORDER_START_PREPARATION','ORDER_MARK_READY']},headers:{...headers,'X-Staff-CSRF':'test-csrf','Access-Control-Expose-Headers':'X-Staff-CSRF'}});}
  else if(p==='/api/admin/orders/planning'){
   const filter=u.searchParams.get('filter');json={orders:[row(readyMoves?'READY':starts?'PREPARING':filter==='SCHEDULED'?'SCHEDULED':'ELIGIBLE')],slots:[{date,start:'18:00:00',end:'18:30:00',fulfillmentType:'PICKUP',waiting:starts?0:1,preparing:starts?1:0,ready:0,total:1}],counts:{ALL:1,OVERDUE:0,ELIGIBLE:starts?0:1,SCHEDULED:1,PREPARING:starts?1:0,READY:0},page:0,total:1,generatedAt:`${date}T17:01:00`};
  }else if(p==='/api/admin/orders/planning/alerts'){json={needsPreparation:starts?0:1,readyOverdue:0};}
  else if(p==='/api/admin/orders/TEST-KITCHEN/status'){readyMoves++;assert.equal(req.postDataJSON().status,'READY_FOR_PICKUP');json={};}
  else if(p==='/api/admin/orders/queue/counts'){if(width===390)assert.equal(u.searchParams.get('branchId'),'1');json={overdue:0,eligible:starts?0:1,scheduled:1,preparing:starts?1:0,ready:0,actionableTotal:starts?0:1,confirmedTotal:1};}
  else if(p==='/api/admin/orders/queue')json={orders:[],returnedCount:0,limit:50,hasMore:false};
  else if(p==='/api/admin/orders')json={orders:[],page:0,size:20,totalElements:0,totalPages:0};
  else if(p==='/api/admin/orders/queue/start-selected'){starts++;assert.deepEqual(req.postDataJSON().orderNumbers,['TEST-KITCHEN']);json={requested:1,attempted:1,started:1,skipped:0,results:[{orderNumber:'TEST-KITCHEN',result:'STARTED',message:'Started'}]};}
  else if(p==='/api/admin/notifications/settings')json={enabled:false};
  return route.fulfill({json,headers});
 });
 await context.addInitScript(({branch,product})=>{window.__chimes=0;window.AudioContext=class{state="suspended";currentTime=0;destination={};async resume(){this.state="running"}async suspend(){this.state="suspended"}async close(){this.state="closed"}createOscillator(){return {frequency:{value:0},connect(){},start(){window.__chimes++},stop(){}}}createGain(){return {gain:{setValueAtTime(){},exponentialRampToValueAtTime(){}},connect(){}}}};localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));localStorage.setItem('gokul-cart',JSON.stringify({branchId:1,items:[{product,quantity:2,weightGrams:null}]}));},{branch,product});
 await page.goto(`${base}/admin/login`);await page.getByRole('heading',{name:'Gokul Sweets Admin'}).waitFor();
 assert.equal(await page.getByRole('button',{name:'Language / भाषा',exact:true}).count(),1);
 await chooseLanguage(page,'hi');assert.equal(await page.locator('html').getAttribute('lang'),'hi');
 await page.reload();await page.getByRole('button',{name:'Language / भाषा',exact:true}).waitFor();
 await page.waitForFunction(()=>document.documentElement.lang==='hi');
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
 await chooseLanguage(page,'en');authenticated=true;
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
 if(visual)assert.equal(await page.locator('.checkout-experience-head').getByRole('button',{name:'Change branch',exact:true}).evaluate(node=>getComputedStyle(node).color),'rgb(255, 255, 255)','branch switch stays legible on the dark checkout header');
 // One deliberate reserve action applies the best offer and opens payment without a second price confirmation.
 await page.getByRole('button',{name:'Continue to payment',exact:true}).filter({visible:true}).first().click();
 if(width===390){
  await page.getByText('We couldn’t confirm the offer update. Your reservation and cart are saved. Recheck the reserved total before payment.',{exact:true}).waitFor();
  assert.match(page.url(),/checkout\/review$/);assert.equal(mutations,1);assert.equal(bestOffers,1);
  assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-pending-order')).totalAmount),200);
  assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-cart')).items.length),1);
  assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-pending-order')).offerRecheckRequired),true);
  const acknowledgement=page.getByRole('checkbox',{name:'I have reviewed the updated total and offers.',exact:true});await acknowledgement.waitFor();assert.equal(await acknowledgement.isDisabled(),true);
  await page.getByRole('button',{name:'Recheck reserved total',exact:true}).click();await page.getByText('Reserved total recovered. Review the confirmed amount before payment.',{exact:true}).waitFor();
  assert.equal(mutations,1);assert.equal(bestOffers,1);assert.equal(await acknowledgement.isChecked(),false);
  await acknowledgement.check();assert.equal(await acknowledgement.isChecked(),true);
  assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-pending-order')).priceReviewRequired),false);
  await page.getByRole('button',{name:/^Continue to [Pp]ayment$/,exact:true}).filter({visible:true}).first().click();
  console.log('Automatic offer failure requires reserved-total recovery with visual/add-on/reward flags OFF');
 }
 await page.waitForURL('**/checkout/payment/TEST-CHECKOUT',{timeout:10000}).catch(async error=>{console.log(JSON.stringify({width,mutations,bestOffers,quoteCalls,url:page.url(),body:await page.locator('body').innerText(),pending:await page.evaluate(()=>localStorage.getItem('gokul-pending-order'))}));throw error;});
 await page.waitForURL('**/checkout/payment/TEST-CHECKOUT');assert.equal(mutations,1);assert.equal(bestOffers,1);
 assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-pending-order')).totalAmount),width===390?200:180);
 await page.goto(`${base}/admin/orders`);await page.getByRole('heading',{name:'Plan, prepare, hand over'}).waitFor();
 await page.getByRole('button',{name:/Scheduled future/}).click();await page.waitForURL('**kitchen=SCHEDULED');await page.getByText('Scheduled future',{exact:true}).last().waitFor();assert.equal(await page.getByRole('checkbox',{name:'Select #42'}).count(),0);
 await page.reload();assert.match(page.url(),/kitchen=SCHEDULED/);await page.getByRole('heading',{name:'Plan, prepare, hand over'}).waitFor();
 await chooseLanguage(page,'hi');await page.getByRole('heading',{name:'योजना बनाएँ, तैयार करें, सौंपें'}).waitFor();await page.getByRole('button',{name:'रसोई का अलार्म चालू करें'}).waitFor();await chooseLanguage(page,'en');
 await page.getByRole('button',{name:'Enable kitchen alarm'}).click();await page.waitForFunction(()=>window.__chimes>=6);
 await page.reload();await page.getByRole('button',{name:'Resume kitchen sound'}).waitFor();assert.equal(await page.getByRole('button',{name:'Enable kitchen alarm',exact:true}).count(),0);await page.getByRole('button',{name:'Resume kitchen sound'}).click();await page.getByRole('button',{name:'Kitchen alarm enabled',exact:true}).waitFor();await page.waitForFunction(()=>window.__chimes>=3);
 await chooseLanguage(page,'hi');await page.getByRole('button',{name:'रसोई का अलार्म बंद करें',exact:true}).waitFor();await chooseLanguage(page,'en');
 await page.getByRole('button',{name:'Disable kitchen alarm',exact:true}).click();await page.reload();await page.getByRole('button',{name:'Enable kitchen alarm',exact:true}).waitFor();assert.equal(await page.evaluate(()=>window.__chimes),0);await page.getByRole('button',{name:'Enable kitchen alarm'}).click();await page.waitForFunction(()=>window.__chimes>=3);
 await page.getByRole('button',{name:/Needs preparation/}).click();await page.getByRole('checkbox',{name:'Select #42'}).check();await page.getByRole('button',{name:'Start selected in KOT (1)'}).click();const confirmation=page.getByRole('dialog');await confirmation.waitFor();assert.match(await confirmation.innerText(),/#42/);assert.doesNotMatch(await confirmation.innerText(),/TEST-KITCHEN/);await page.getByRole('button',{name:'Confirm start'}).click();await page.getByText(/1 started · 0 skipped/).waitFor();assert.equal(starts,1);await page.getByText('No orders need action. Alarm is watching for new work.').waitFor();const stopped=await page.evaluate(()=>window.__chimes);await page.waitForTimeout(2500);assert.equal(await page.evaluate(()=>window.__chimes),stopped);
 // PREPARING orders can be completed directly, without opening View order.
 await page.getByRole('button',{name:'Or tap to confirm',exact:true}).click();
 await page.getByRole('button',{name:'Yes, mark ready',exact:true}).click();await page.getByText(/Order marked ready/).waitFor();assert.equal(readyMoves,1);
 starts=0;readyMoves=0;await page.reload();await page.getByRole('button',{name:'Start preparation',exact:true}).waitFor();
 for(const phoneWidth of width===390?[320,390]:[1280]){await page.setViewportSize({width:phoneWidth,height:900});for(const locale of ['hi','en']){await chooseLanguage(page,locale);const header=page.locator('header').first();assert.equal(await header.evaluate(node=>{const r=node.getBoundingClientRect();return r.height<=80&&r.width<=innerWidth;}),true,`compact admin header at ${phoneWidth}px in ${locale}`);assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);}}
 const thumb=page.getByRole('button',{name:'Start preparation',exact:true}),rail=thumb.locator('..');
 async function drag(dx,dy=0){
  // Confirmations and focus can scroll the page. Measure the current thumb for every gesture,
  // after its reset animation, rather than reusing coordinates from before those interactions.
  await thumb.scrollIntoViewIfNeeded();
  await page.waitForFunction(node=>new DOMMatrixReadOnly(getComputedStyle(node).transform).m41===0,await thumb.elementHandle());
  const box=await thumb.boundingBox(),track=await rail.boundingBox();
  const distance=dx==='full'?track.width-56:dx;
  await page.mouse.move(box.x+24,box.y+24);await page.mouse.down();await page.mouse.move(box.x+24+distance,box.y+24+dy,{steps:8});await page.mouse.up();
 }
 await drag(30);await drag(30,90);assert.equal(starts,0,'short swipes and scroll gestures do not submit');
 await page.getByRole('button',{name:'Or tap to confirm',exact:true}).click();await page.getByRole('button',{name:'Cancel',exact:true}).click();assert.equal(starts,0);
 await thumb.focus();await page.keyboard.press('Enter');await page.getByRole('group',{name:'Confirm order action'}).waitFor();await page.getByRole('button',{name:'Cancel',exact:true}).click();
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
 if(process.env.SCREENSHOT_DIR)await page.screenshot({path:`${process.env.SCREENSHOT_DIR}/kitchen-swipe-${width}.png`,fullPage:true});
 await drag('full');await page.getByText(/Preparation started. KOT created./).waitFor();assert.equal(starts,1,'full swipe sends exactly one start');
 const readyThumb=page.getByRole('button',{name:'Mark ready',exact:true});await readyThumb.waitFor();
 if(process.env.SCREENSHOT_DIR)await page.screenshot({path:`${process.env.SCREENSHOT_DIR}/kitchen-ready-swipe-${width}.png`,fullPage:true});
 assert.deepEqual(errors,[]);
 if(width===390){
  visual=true;gateway=true;await page.evaluate(()=>sessionStorage.removeItem('gokul-storefront-settings'));await page.goto(`${base}/`);await page.locator('#gokul-arrival-title').waitFor();
  const topbar=page.getByRole('navigation',{name:'Welcome navigation'}).locator('..');
  for(const phoneWidth of [320,390,1280]){await page.setViewportSize({width:phoneWidth,height:900});
   for(const locale of ['en','hi']){await chooseLanguage(topbar,locale);await page.waitForFunction(locale=>document.documentElement.lang===locale,locale);
    assert.equal(await topbar.getByRole('link',{name:'Gokul Sweets · Home'}).getAttribute('href'),'/');assert.equal(await topbar.evaluate(node=>{const [brand,nav,language]=Array.from(node.children).map(child=>child.getBoundingClientRect());return brand.right<=nav.left&&nav.right<=language.left;}),true,'brand left, order action then language right');const fits=await topbar.evaluate(node=>{const box=node.getBoundingClientRect(),rects=Array.from(node.children).map(child=>child.getBoundingClientRect());return rects.every(r=>r.left>=box.left&&r.right<=box.right&&r.top>=box.top&&r.bottom<=box.bottom)&&rects.every((r,i)=>rects.slice(i+1).every(s=>r.right<=s.left||s.right<=r.left||r.bottom<=s.top||s.bottom<=r.top));});assert.equal(fits,true,`editorial controls fit without overlap at ${phoneWidth}px in ${locale}`);
   }
  }
 }
 await context.close();
 }}finally{await browser.close();}
console.log('Verified contact, reduced checkout, Hindi persistence and kitchen filtering/KOT: desktop/mobile passed.');
