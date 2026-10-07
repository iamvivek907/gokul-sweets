import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {mkdir} from 'node:fs/promises';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const browser=await chromium.launch({headless:true}),base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
const date=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata'}).format(new Date(Date.now()+86400000));
const branch={id:1,code:'TEST',name:'Test Gokul branch',active:true,address:'Test address',city:'Test city',phone:'9876543210',pickupAvailable:true};
const half={id:10,name:'Paneer meal Half',categoryId:1,categoryName:'Meals',description:'Fresh paneer with rice.',price:100,imageUrl:null,available:true,saleMode:'UNIT',minimumWeightGrams:null,weightStepGrams:null},full={...half,id:11,name:'Paneer meal Full',price:180},drink={...half,id:12,name:'Fresh drink',categoryId:2,categoryName:'Drinks',price:40};
const baseSlot={id:1,branchId:1,slotDate:date,startTime:'18:00:00',endTime:'19:00:00',active:true,capacity:50,remainingCapacity:50,priorityEnabled:true,priorityCharge:25};
try{for(const scenario of ['stalled-offer','pincode-change','phone','boundary','desktop','changed-total','uncertain-order','uncertain-identity','stale-identity','priority-only','stored-priority','uncertain-changed','fee-with-offer','fee-no-offer','cart-removal','clear-cart','pickup-popup','pickup-unavailable','pickup-network','gateway-loading','cart-entry','menu-pickup-tomorrow','menu-pickup-later','menu-pickup-quantity','menu-pickup-priority','menu-pickup-date-only','menu-pickup-no-selection','menu-pickup-expired'].filter(s=>!process.env.MOBILE_CHECKOUT_SCENARIOS||process.env.MOBILE_CHECKOUT_SCENARIOS.split(',').includes(s))){
 console.log('Scenario:',scenario);
 const pickupRegression=scenario.startsWith('menu-pickup-');
 const slot=pickupRegression?{...baseSlot,startTime:'20:30:00',endTime:'21:00:00'}:baseSlot;
 const secondDate=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata'}).format(new Date(Date.now()+2*86400000));
 const emptyDate=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata'}).format(new Date(Date.now()+3*86400000));
 const width=scenario==='desktop'?1280:scenario==='boundary'?640:390;
 const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:'block'}),page=await context.newPage();
 const handoffEvents=[];page.on("console",message=>{if(message.text().startsWith("GOKUL_HANDOFF:"))handoffEvents.push(JSON.parse(message.text().slice(14)));});
 const errors=[];page.on("pageerror",e=>errors.push(e.message));page.on("console",m=>{if(m.type()==="error"&&/hydration|hydrated/i.test(m.text()))errors.push(m.text());});
 page.setDefaultTimeout(15000);page.setDefaultNavigationTimeout(15000);
 if(pickupRegression)await page.clock.setFixedTime(new Date(`${date}T18:00:00+05:30`));
 let signedIn=false,orders=[],payments=0,previews=0,previewRequests=[],firstAttempt=true,liveItems=[],livePickup="NORMAL",checkoutRequest=null,paymentStatus="PENDING",cancels=0,offerCommitted=false,holdOffers=false,releaseOffers;
 const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Methods':'GET,POST,PUT,OPTIONS','Access-Control-Allow-Headers':'content-type,idempotency-key'};
 const subtotal=()=>liveItems.reduce((sum,i)=>sum+({10:100,11:180,12:40,13:80}[i.productId]??0)*(i.weightGrams?i.weightGrams/1000:i.quantity),0);
 const payable=()=>subtotal()+(livePickup==="PRIORITY"?25:0);
 const rebate=scenario==='fee-no-offer'?0:20;
 const fee=base=>scenario.startsWith('fee-')?Math.round(base*2)/100:0;
 const feeTax=value=>Math.round((value-value/1.18)*100)/100;
 const finalTotal=()=>payable()-rebate+fee(payable()-rebate);
 let releaseRebate,releaseGateway,rebateStarted,gatewayStarted;
 const rebateGate=new Promise(resolve=>{releaseRebate=resolve;}),gatewayGate=new Promise(resolve=>{releaseGateway=resolve;});
 const rebateReady=new Promise(resolve=>{rebateStarted=resolve;}),gatewayReady=new Promise(resolve=>{gatewayStarted=resolve;});
 const payment=()=>({paymentId:10,orderNumber:"TEST-SINGLE",provider:"PHONEPE",paymentStatus,amount:finalTotal(),currency:"INR",paymentUrl:"https://gateway.example.invalid/pay",expiresAt:new Date(Date.now()+(pickupRegression?172800000:600000)).toISOString()});
 const order=()=>({id:1,orderNumber:'TEST-SINGLE',branchId:1,pickupSlotId:1,pickupDate:date,pickupStartTime:'18:00:00',pickupEndTime:'19:00:00',pickupType:livePickup,customerName:'Verified customer',customerPhone:'9876543210',orderStatus:paymentStatus==='FAILED'?'PAYMENT_FAILED':'PENDING_PAYMENT',paymentStatus:scenario==='stalled-offer'&&!payments?null:paymentStatus,fulfillmentType:'PICKUP',branchName:branch.name,subtotal:subtotal(),taxAmount:0,priorityCharge:livePickup==="PRIORITY"?25:0,totalAmount:scenario==='stalled-offer'&&offerCommitted?finalTotal():payable()+fee(payable()),items:[],reservationExpiresAt:new Date(Date.now()+(pickupRegression?172800000:600000)).toISOString(),createdAt:new Date().toISOString()});
 await context.route('https://gateway.example.invalid/**',async route=>{if(scenario==='gateway-loading'){gatewayStarted();await gatewayGate;}return route.fulfill({contentType:'text/html',body:'<h1>Secure provider checkout</h1>'});});
 await context.route('**/api/**',async route=>{
  const req=route.request(),p=new URL(req.url()).pathname;let json=[];
  if(req.method()==='OPTIONS')return route.fulfill({status:204,headers});
  if(p==='/api/storefront/features')json={branchExperience:true,futuristicStorefrontV2:true,checkoutExperienceV2:true,contextualStorefrontV2:true,simplifiedCheckout:true,smartPickupSelection:true,smartAvailability:true,authoritativePickupCommitment:true,acceptedCheckoutQuote:true,persistentPickupContext:true,cartSwitchPreview:true,inPlaceBranchSwitch:true,accessibleOrderingV2:true,paymentPollingV2:true,paidCartRecovery:true,pickupAddOns:true,futureOrderingDays:30,today:date};
  else if(p==='/api/storefront/customer-identity')json={enabled:true,guestCheckoutEnabled:false};
  else if(p==='/api/customer/identity/me')json={authenticated:signedIn,...(signedIn?{phone:'+919876543210',name:'Verified customer'}:{})};
  else if(p==='/api/customer/identity/start')json={};
  else if(p==='/api/customer/identity/exchange'){signedIn=true;json={authenticated:true};}
  else if(p==='/api/branches')json=[branch];else if(p==='/api/branches/1')json=branch;
  else if(p==='/api/menu')json=[{id:1,name:'Meals',displayOrder:0,products:[half,full]},{id:2,name:'Drinks',displayOrder:1,products:[drink]}];
  else if(p==='/api/menu/portion-groups')json={version:1,groups:[{key:'paneer',title:'Paneer meal',choices:[{productId:10,label:'Half'},{productId:11,label:'Full'}]}]};
  else if(p.endsWith('/availability')&&req.postDataJSON().days===1&&req.postDataJSON().startDate===secondDate&&scenario==='pickup-network')return route.fulfill({status:503,json:{message:'Availability unavailable'},headers});
  else if(p.endsWith('/availability')&&req.postDataJSON().days===1&&req.postDataJSON().startDate===secondDate&&scenario==='pickup-unavailable')json={today:date,maximumDate:secondDate,dates:[{date:secondDate,available:false,slots:[]}]};
  else if(p.endsWith('/availability')&&pickupRegression){
   const items=req.postDataJSON().items??[];
   const conflict=scenario!=='menu-pickup-expired'&&(scenario!=='menu-pickup-quantity'||items.some(item=>item.quantity>1));
   const sameDate=scenario==='menu-pickup-later'||scenario==='menu-pickup-priority';
   const alternative={...slot,id:scenario==='menu-pickup-priority'?1:2,slotDate:sameDate?date:secondDate,startTime:scenario==='menu-pickup-priority'?'20:30:00':sameDate?'21:30:00':'10:00:00',endTime:scenario==='menu-pickup-priority'?'21:00:00':sameDate?'22:00:00':'11:00:00'};
   const initial={slot,normalAvailable:!conflict,priorityAvailable:scenario==='menu-pickup-priority'};
   json={today:date,maximumDate:secondDate,dates:[{date,available:!conflict||sameDate,slots:[initial,...(scenario==='menu-pickup-later'?[{slot:alternative,normalAvailable:true,priorityAvailable:false}]:[])]},...(!sameDate?[{date:secondDate,available:true,slots:[{slot:alternative,normalAvailable:true,priorityAvailable:false}]}]:[])]};
  }
  else if(p.endsWith('/availability'))json={today:date,maximumDate:date,dates:[{date,available:true,slots:[{slot,normalAvailable:scenario!=='priority-only',priorityAvailable:true}]},...(scenario.startsWith('pickup-')?[{date:secondDate,available:true,slots:[{slot:{...slot,id:2,slotDate:secondDate,startTime:'10:00:00',endTime:'11:00:00'},normalAvailable:true,priorityAvailable:false}]},{date:emptyDate,available:false,slots:[],reason:'No pickup times for this cart on this date.'}]:[])]};
  else if(p==='/api/menu/pickup-addons')json=[{product:drink,weightGrams:null,portionPrice:40,portionTotal:40,reason:'Pairs well with your order'}];else if(p==='/api/menu/pickup-addons/check')json={orderable:true};
  else if(p==='/api/orders/mobile-preview'){previews++;previewRequests.push(req.postDataJSON());liveItems=req.postDataJSON().items;livePickup=req.postDataJSON().pickupType;json={quote:{token:'signed-quote',subtotal:String(subtotal()),taxAmount:'0',priorityCharge:req.postDataJSON().pickupType==='PRIORITY'?'25':'0',convenienceFee:'0',paymentFeeRate:scenario.startsWith('fee-')?'2':'0',paymentFee:String(fee(payable())),paymentFeeTax:String(feeTax(fee(payable()))),totalAmount:String(payable()+fee(payable())),currency:'INR',items:[],expiresAt:new Date(Date.now()+(pickupRegression?172800000:600000)).toISOString()},paymentFee:fee(payable()-rebate),paymentFeeTax:feeTax(fee(payable()-rebate)),offers:rebate?[{code:'SAVE20',name:'Save 20',rebateAmount:rebate,payableAfterRebate:finalTotal()}]:[]};}
  else if(p==='/api/orders'&&req.method()==='POST'){orders.push({key:req.headers()['idempotency-key'],request:req.postDataJSON()});checkoutRequest=req.postDataJSON();if(scenario.startsWith('uncertain-')&&firstAttempt){firstAttempt=false;return route.abort('failed');}if(scenario==='uncertain-identity'&&orders.length===2){signedIn=false;return route.fulfill({status:401,json:{message:'Verify your phone'},headers});}json=order();}
  else if(p==='/api/orders/TEST-SINGLE')json=order();
  else if(p==='/api/orders/TEST-SINGLE/available-rebates'){if(holdOffers)await new Promise(r=>releaseOffers=r);try{return await route.fulfill({json:[],headers});}catch{return;}}
  else if(p==='/api/payments/providers')json={defaultProvider:'PHONEPE',enabledProviders:['PHONEPE']};
  else if(p==='/api/payments/order/TEST-SINGLE')json={payment:scenario==='stalled-offer'&&!payments?null:payment()};
  else if(p==='/api/payments/10/refresh')json=payment();
  else if(p==='/api/payments/10/cancel-checkout'){cancels++;paymentStatus='FAILED';json=payment();}
  else if(p==='/api/orders/TEST-SINGLE/rebate'&&req.method()==='POST'){offerCommitted=true;rebateStarted();await rebateGate;try{return await route.fulfill({json:{totalAmount:finalTotal(),rebateAmount:rebate},headers});}catch{return;}}
  else if(p==='/api/orders/TEST-SINGLE/rebate/best'){if(scenario==='gateway-loading'){rebateStarted();await rebateGate;}json={totalAmount:finalTotal()+(scenario==='changed-total'?5:0),rebateAmount:rebate};}
  else if(p==='/api/payments'&&req.method()==='POST'){payments++;json={paymentId:10,orderNumber:'TEST-SINGLE',provider:'PHONEPE',paymentStatus:'PENDING',amount:finalTotal(),currency:'INR',paymentUrl:'https://gateway.example.invalid/pay',expiresAt:new Date(Date.now()+(pickupRegression?172800000:600000)).toISOString()};}
  return route.fulfill({json,headers});
 });
 await context.addInitScript(({branch})=>{if(!localStorage.getItem('gokul-selected-branch'))localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));window.initSendOTP=options=>options.success({type:'success',accessToken:'synthetic-test-proof'});},{branch});
 if(pickupRegression&&scenario!=='menu-pickup-date-only'&&scenario!=='menu-pickup-no-selection')await context.addInitScript(({date,slot})=>{if(!localStorage.getItem('gokul-selected-pickup-slot'))localStorage.setItem('gokul-selected-pickup-slot',JSON.stringify({date,slot,pickupType:'NORMAL'}));},{date,slot});
 if(scenario==='menu-pickup-date-only')await context.addInitScript(({date})=>{if(!localStorage.getItem('gokul-pickup-intent'))localStorage.setItem('gokul-pickup-intent',JSON.stringify({branchId:1,date}));},{date});
 if(scenario==='stored-priority')await context.addInitScript(({date,slot})=>localStorage.setItem("gokul-selected-pickup-slot",JSON.stringify({date,slot,pickupType:"PRIORITY"})),{date,slot});
 await page.goto(`${base}/menu`);await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});await page.locator('.gokul-menu-product-card').first().waitFor();
 if(width>640){assert.equal(await page.locator('.mobile-portion-card').count(),0);assert.equal(await page.locator('.mobile-menu-filters').count(),0);await page.getByRole('button',{name:'Add Paneer meal Half to cart',exact:true}).click();assert.equal(await page.locator('.gokul-floating-cart a').getAttribute('href'),'/cart');await page.goto(`${base}/checkout/mobile`);await page.waitForURL('**/cart');assert.equal(previews,0);assert.equal(orders.length,0);await context.close();continue;}
 const portion=page.locator('.mobile-portion-card');await portion.waitFor();
 assert.equal(await page.getByRole('button',{name:'View Paneer meal on the menu',exact:true}).count(),0);
 assert.equal(await page.getByRole('button',{name:'View Paneer meal Full on the menu',exact:true}).count(),0);
 if(!pickupRegression){await page.getByLabel('Find a favourite',{exact:true}).fill('Paneer meal');
 await portion.waitFor();await page.locator('#gokul-menu-items').getByRole('button',{name:'Clear filters',exact:true}).click();assert.equal(await page.locator('.gokul-menu-product-card').count(),2);}
 const menuDocument=await page.evaluate(()=>{window.__menuDocument=crypto.randomUUID();return window.__menuDocument;});
 const menuRows=page.locator('.gokul-menu-product-card');const firstRow=await menuRows.first().elementHandle();
 await portion.getByRole('button',{name:'Choose options for Paneer meal',exact:true}).click();const choices=page.getByRole('dialog',{name:'Paneer meal',exact:true});await choices.getByRole('button',{name:'Add Paneer meal Full to cart',exact:true}).click();await choices.getByRole('button',{name:'Add Paneer meal Half to cart',exact:true}).click();await choices.getByRole('button',{name:'Done',exact:true}).click();assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-cart')).items.map(i=>i.product.id).sort()),[10,11]);
 assert.equal(await page.evaluate(()=>window.__menuDocument),menuDocument,'first Add preserves the document');assert.equal(await firstRow.evaluate(node=>node.isConnected),true,'first Add preserves the rendered menu');
 assert.equal(await page.locator('.mobile-menu-filters').count(),0,'duplicate filters below Search are removed');assert.equal(await page.locator('.mobile-filter-chips').count(),0,'categories live only in Items');assert.equal(await page.locator('.gokul-menu-product-card').count(),pickupRegression?1:2,'initial Food collection is scoped; clearing filters opens the full catalogue');assert.equal(await portion.getByRole('button',{name:'Choose options for Paneer meal'}).count(),1,'variant management remains available without filters');assert.equal(await page.getByRole('checkbox',{name:/veg/i}).count(),0);
 await page.locator('.gokul-floating-cart a').click();await page.waitForURL('**/checkout/mobile');await page.getByRole('heading',{name:'Verify your phone',exact:true}).waitFor();assert.equal(await page.getByRole('dialog').count(),0);assert.equal(await page.getByRole('button',{name:'Verify phone to continue',exact:true}).getAttribute('data-payment-ready'),'false');assert.equal(previews,0);assert.equal(orders.length,0);
 assert.equal(await page.getByRole('button',{name:'Verify with SMS',exact:true}).count(),0);assert.equal(orders.length,0);
 await page.getByRole('button',{name:'Verify phone to continue',exact:true}).click();await page.getByRole('heading',{name:'Phone verified',exact:true}).waitFor();
 const ready=async()=>{await page.getByRole('region',{name:'Checkout pickup',exact:true}).getByRole('button',{name:/^(Confirm pickup|Change pickup)$/}).waitFor();const confirm=page.getByRole('button',{name:'Confirm pickup',exact:true});if(await confirm.count()){await confirm.click();const picker=page.getByRole('dialog',{name:'Choose pickup date & time',exact:true});await picker.getByRole('button',{name:'Use this pickup',exact:true}).click();await picker.waitFor({state:'hidden'});}await page.getByRole('button',{name:'Pay now',exact:true}).waitFor();const branch=page.getByRole('checkbox',{name:'I will collect my order at this branch.',exact:true});if(await branch.count())await branch.check();return page.getByRole('button',{name:'Pay now',exact:true}).waitFor().then(()=>page.waitForFunction(()=>{const button=Array.from(document.querySelectorAll('button')).find(b=>b.textContent==='Pay now');return !!button&&!button.disabled;},null,{timeout:15000}));};
 assert.equal(await page.getByRole('button',{name:'Pay now',exact:true}).getAttribute('data-payment-ready'),'false');
 await page.getByRole('button',{name:'Pay now',exact:true}).click();await page.getByRole('status').filter({hasText:'Confirm this collection branch'}).waitFor();assert.equal(orders.length,0);await page.getByRole('checkbox',{name:'I will collect my order at this branch.',exact:true}).check();
 if(scenario==='stored-priority'){
  const before=await page.evaluate(()=>localStorage.getItem('gokul-selected-pickup-slot'));
  const confirm=page.getByRole('button',{name:'Confirm pickup time',exact:true});await confirm.waitFor();
  assert.equal(await confirm.getAttribute('data-payment-ready'),'false');assert.equal(orders.length,0);assert.equal(payments,0);
  await confirm.click();const picker=page.getByRole('dialog',{name:'Choose pickup date & time',exact:true});
  await picker.getByRole('button',{name:'Cancel',exact:true}).click();assert.equal(await confirm.getAttribute('data-payment-ready'),'false');
  assert.equal(await page.evaluate(()=>localStorage.getItem('gokul-selected-pickup-slot')),before);
  await page.reload();await page.getByRole('heading',{name:'Phone verified',exact:true}).waitFor();
  await page.getByRole('checkbox',{name:'I will collect my order at this branch.',exact:true}).check();await confirm.waitFor();
  assert.equal(await confirm.getAttribute('data-payment-ready'),'false');assert.equal(orders.length,0);assert.equal(payments,0);
 }
 if(pickupRegression){
  const before=await page.evaluate(()=>localStorage.getItem('gokul-selected-pickup-slot'));
  if(scenario==='menu-pickup-quantity'){await ready();await page.getByRole('button',{name:'Add one Paneer meal Half',exact:true}).click();}
  if(scenario==='menu-pickup-expired'){await ready();await page.clock.setFixedTime(new Date(`${date}T21:01:00+05:30`));await page.getByText('Your pickup time has passed. Choose another time. Your pickup hasn’t changed.',{exact:true}).waitFor();}
  const warning=page.getByRole('alert').filter({hasText:/Your pickup hasn’t changed|Choose when you’ll collect your order/});
  if(scenario==='menu-pickup-date-only')await page.getByRole('region',{name:'Checkout pickup'}).getByText(/Today ·/).waitFor();
  await warning.waitFor();
  await page.getByText('Choose a pickup time to see your final total.',{exact:true}).waitFor();
  assert.equal(await page.getByRole('button',{name:'Choose pickup time',exact:true}).getAttribute('data-payment-ready'),'false');assert.equal(await page.getByRole('button',{name:'Choose pickup time',exact:true}).isEnabled(),true);
  assert.equal(await page.evaluate(()=>localStorage.getItem('gokul-selected-pickup-slot')),before);
  assert.equal(orders.length,0);assert.equal(payments,0);
  await page.reload();await warning.waitFor();
  assert.equal(await page.evaluate(()=>localStorage.getItem('gokul-selected-pickup-slot')),before,'reload preserves customer intent');
  await page.getByRole('heading',{name:'Phone verified',exact:true}).waitFor();
  await page.getByRole('checkbox',{name:'I will collect my order at this branch.',exact:true}).check();
  assert.equal(await page.getByRole('button',{name:'Choose pickup time',exact:true}).getAttribute('data-payment-ready'),'false');assert.equal(await page.getByRole('button',{name:'Choose pickup time',exact:true}).isEnabled(),true);
  if(scenario==='menu-pickup-quantity'){await page.getByRole('button',{name:'Remove one Paneer meal Half',exact:true}).click();await ready();assert.equal(await page.evaluate(()=>localStorage.getItem('gokul-selected-pickup-slot')),before);await page.getByRole('button',{name:'Add one Paneer meal Half',exact:true}).click();await warning.waitFor();assert.equal(await page.getByRole('button',{name:'Choose pickup time',exact:true}).getAttribute('data-payment-ready'),'false');assert.equal(await page.getByRole('button',{name:'Choose pickup time',exact:true}).isEnabled(),true);}
  const open=()=>page.getByRole('button',{name:'Choose pickup time',exact:true}).click();
  const dialog=page.getByRole('dialog',{name:'Choose pickup date & time',exact:true});
  await open();if(scenario==='menu-pickup-date-only')assert.equal(await dialog.getByRole('button',{name:date,exact:true}).getAttribute('aria-pressed'),'true');await dialog.getByRole('button',{name:'Cancel',exact:true}).click();
  assert.equal(await page.evaluate(()=>localStorage.getItem('gokul-selected-pickup-slot')),before,'cancel does not select an alternative');
  await open();
  const targetDate=scenario==='menu-pickup-later'||scenario==='menu-pickup-priority'?date:secondDate;
  await dialog.getByRole('button',{name:targetDate,exact:true}).click();
  await dialog.getByRole('button',{name:scenario==='menu-pickup-priority'?/20:30–21:00.*Priority/:scenario==='menu-pickup-later'?'21:30–22:00 Standard':'10:00–11:00 Standard',exact:scenario!=='menu-pickup-priority'}).click();
  assert.equal(await page.evaluate(()=>localStorage.getItem('gokul-selected-pickup-slot')),before,'draft remains uncommitted');
  await dialog.getByRole('button',{name:'Use this pickup',exact:true}).click();await dialog.waitFor({state:'hidden'});await ready();
  const selected=await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-selected-pickup-slot')));
  assert.equal(selected.date,targetDate);assert.equal(selected.pickupType,scenario==='menu-pickup-priority'?'PRIORITY':'NORMAL');
  assert.equal(orders.length,0);assert.equal(payments,0);
  await page.getByRole('button',{name:'Pay now',exact:true}).click();await page.waitForURL('https://gateway.example.invalid/**');
  assert.equal(orders.length,1);assert.equal(payments,1);assert.equal(orders[0].request.pickupSlotId,selected.slot.id);assert.equal(orders[0].request.pickupType,selected.pickupType);
  assert.deepEqual(errors,[]);await context.close();continue;
 }
 if(await page.getByRole('button',{name:'Choose pickup',exact:true}).count()){await page.getByRole('button',{name:'Choose pickup',exact:true}).click();const picker=page.getByRole('dialog',{name:'Choose pickup date & time',exact:true});await picker.getByRole('button',{name:scenario==='priority-only'?/Priority/:'18:00–19:00 Standard',exact:scenario!=='priority-only'}).click();await picker.getByRole('button',{name:'Use this pickup',exact:true}).click();await picker.waitFor({state:'hidden'});}
 await ready();
 if(scenario==='cart-removal'){
  const cart=page.getByRole('region',{name:'Cart items',exact:true});
  assert.equal(await cart.evaluate(node=>node.nextElementSibling?.getAttribute('aria-label')),'Pickup add-ons','Complete your meal immediately follows items');
  for(const button of [page.getByRole('button',{name:'Clear cart',exact:true}),page.getByRole('button',{name:'Remove Paneer meal Half from cart',exact:true})]){
   const metrics=await button.evaluate(node=>{const lum=c=>{const x=c.match(/\d+/g).slice(0,3).map(Number).map(v=>{const s=v/255;return s<=.04045?s/12.92:((s+.055)/1.055)**2.4;});return x[0]*.2126+x[1]*.7152+x[2]*.0722;};const style=getComputedStyle(node),fg=lum(style.color),bg=lum(style.backgroundColor);return {contrast:(Math.max(fg,bg)+.05)/(Math.min(fg,bg)+.05),height:node.getBoundingClientRect().height};});
   assert.ok(metrics.contrast>=4.5);assert.ok(metrics.height>=44);
  }
  assert.equal(await cart.locator('.mobile-cart-row').evaluateAll(rows=>rows.every(row=>parseFloat(getComputedStyle(row).borderRadius)>=12&&getComputedStyle(row).borderTopStyle==='solid')),true,'items retain separate visible card boundaries');
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
  if(process.env.SCREENSHOT_DIR){await mkdir(process.env.SCREENSHOT_DIR,{recursive:true});await cart.evaluate(node=>scrollTo({top:node.getBoundingClientRect().top+scrollY-100,behavior:'instant'}));await page.screenshot({path:`${process.env.SCREENSHOT_DIR}/checkout-cart-cards.png`});}
 }
 if(scenario==='pincode-change'){
  await page.evaluate(()=>{const branch=JSON.parse(localStorage.getItem('gokul-selected-branch'));branch.pincode='274408';localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));window.dispatchEvent(new Event('storage'));});
  await page.locator('.mobile-branch-review').getByText(/274408/).waitFor();
  assert.equal(await page.getByRole('checkbox',{name:'I will collect my order at this branch.',exact:true}).isChecked(),false);
  assert.equal(await page.getByRole('button',{name:'Pay now',exact:true}).getAttribute('data-payment-ready'),'false');
  assert.equal(orders.length,0);assert.equal(payments,0);await ready();
 }if(scenario==='priority-only'||scenario==='stored-priority'){await page.getByText('Change pickup',{exact:true}).click();const options=page.getByRole('dialog',{name:'Choose pickup date & time',exact:true});await options.getByRole('button',{name:/Priority/}).waitFor();assert.equal(await options.locator('.mobile-pickup-times button').count(),scenario==='priority-only'?1:2);assert.equal(await options.getByRole('button',{name:/Priority/}).getAttribute('aria-pressed'),'true');assert.match(await options.textContent(),/25/);await options.getByRole('button',{name:'Close pickup selector',exact:true}).click();}
 assert.equal(new URL(page.url()).pathname,'/checkout/mobile');assert.equal(orders.length,0);assert.equal(payments,0);
 await page.getByRole('button',{name:'Add one Paneer meal Half',exact:true}).click();await ready();assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-cart')).items.find(i=>i.product.id===10).quantity),2);
 await page.getByRole('button',{name:'Add Fresh drink',exact:true}).click();await ready();
 if(scenario==='cart-entry'){
  await page.getByRole('link',{name:'Branch home',exact:true}).click();await page.waitForURL('**/branches/1');assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-cart')).items.length),3);
  await page.goto(`${base}/cart`);await page.waitForURL('**/checkout/mobile');await ready();assert.equal(await page.getByRole('heading',{name:'Your order',exact:true}).count(),1);assert.equal(await page.getByText(/items ready for review/).count(),0);
  await page.reload();await page.getByRole('heading',{name:'Your order',exact:true}).waitFor();assert.equal(new URL(page.url()).pathname,'/checkout/mobile');assert.equal(orders.length,0);assert.equal(payments,0);await context.close();continue;
 }
 if(scenario==='pickup-unavailable'||scenario==='pickup-network'){
  const before=await page.evaluate(()=>localStorage.getItem('gokul-selected-pickup-slot'));
  await page.getByRole('button',{name:'Change pickup',exact:true}).click();const dialog=page.getByRole('dialog',{name:'Choose pickup date & time',exact:true});
  await dialog.getByRole('button',{name:secondDate,exact:true}).click();await dialog.getByRole('button',{name:'10:00–11:00 Standard',exact:true}).click();
  await dialog.getByRole('button',{name:'Use this pickup',exact:true}).click();await dialog.getByRole('alert').waitFor();
  assert.equal(await dialog.isVisible(),true);assert.equal(await page.evaluate(()=>localStorage.getItem('gokul-selected-pickup-slot')),before);
  assert.equal(orders.length,0);assert.equal(payments,0);await dialog.getByRole('button',{name:'Close pickup selector',exact:true}).click();await context.close();continue;
 }
 if(scenario==='pickup-popup'){
  const before=await page.evaluate(()=>localStorage.getItem('gokul-selected-pickup-slot'));
  const open=()=>page.getByRole('button',{name:'Change pickup',exact:true}).click();const dialog=page.getByRole('dialog',{name:'Choose pickup date & time',exact:true});
  await open();await dialog.waitFor();const box=await dialog.boundingBox();assert.ok(box.height<900*.7);assert.ok(box.width<width);assert.equal(await dialog.getByRole('button',{name:'Close pickup selector',exact:true}).isVisible(),true);
  if(process.env.SCREENSHOT_DIR)await page.screenshot({path:`${process.env.SCREENSHOT_DIR}/pickup-popup.png`});
  await dialog.getByRole('button',{name:emptyDate,exact:true}).click();await dialog.getByText('No pickup times for this cart on this date.',{exact:true}).waitFor();assert.equal(await dialog.getByRole('button',{name:'Use this pickup',exact:true}).isDisabled(),true);await dialog.getByRole('button',{name:'Cancel',exact:true}).click();assert.equal(await page.evaluate(()=>localStorage.getItem('gokul-selected-pickup-slot')),before);
  await open();await page.mouse.click(4,4);await dialog.waitFor({state:'hidden'});await open();await page.keyboard.press('Escape');await dialog.waitFor({state:'hidden'});assert.equal(await page.getByRole('button',{name:'Change pickup',exact:true}).evaluate(e=>e===document.activeElement),true);
  await open();await dialog.getByRole('button',{name:secondDate,exact:true}).click();assert.equal(await dialog.locator('.mobile-pickup-times button').count(),1);await dialog.getByRole('button',{name:'10:00–11:00 Standard',exact:true}).click();assert.equal(await page.evaluate(()=>localStorage.getItem('gokul-selected-pickup-slot')),before);await dialog.getByRole('button',{name:'Use this pickup',exact:true}).click();await dialog.waitFor({state:'hidden'});await ready();assert.equal(previewRequests.at(-1).pickupSlotId,2);assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-selected-pickup-slot')).date),secondDate);assert.equal(orders.length,0);assert.equal(payments,0);await context.close();continue;
 }
 if(scenario==='cart-removal'||scenario==='clear-cart'){
  await page.getByRole('link',{name:'Back to menu',exact:true}).click();await page.waitForURL('**/menu');assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-cart')).items.length),3);
  await page.locator('.gokul-floating-cart a').click();await page.waitForURL('**/checkout/mobile');await ready();
  if(scenario==='clear-cart'){await page.getByRole('button',{name:'Clear cart',exact:true}).click();await page.waitForURL('**/menu');}
  else{
   await page.evaluate(()=>{const cart=JSON.parse(localStorage.getItem('gokul-cart'));const product={...cart.items.find(i=>i.product.id===12).product,id:13,name:'Gulab Jamun',price:80,saleMode:'WEIGHT',weightStepGrams:250};cart.items.push({product,quantity:1,weightGrams:2000});localStorage.setItem('gokul-cart',JSON.stringify(cart));window.dispatchEvent(new CustomEvent('gokul-cart-change'));});await ready();const weighted=page.locator('.mobile-cart-row').filter({hasText:'Gulab Jamun'});await weighted.locator('.mobile-cart-item-meta p').getByText('2 kg · ₹160.00',{exact:true}).waitFor();await weighted.getByRole('button',{name:'Add one Gulab Jamun',exact:true}).click();await ready();assert.match(await weighted.locator('.mobile-cart-item-meta p').textContent(),/₹180.00/);await weighted.getByRole('button',{name:'Remove one Gulab Jamun',exact:true}).click();await ready();assert.match(await weighted.locator('.mobile-cart-item-meta p').textContent(),/₹160.00/);
   await page.getByRole('button',{name:'Remove Paneer meal Half from cart',exact:true}).click();await ready();assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-cart')).items.map(i=>i.product.id).sort()),[11,12,13]);
   await page.getByRole('button',{name:'Select items',exact:true}).click();await page.getByRole('checkbox',{name:'Select Paneer meal Full',exact:true}).check();await page.getByRole('checkbox',{name:'Select Fresh drink',exact:true}).check();await page.getByRole('button',{name:'Remove selected (2)',exact:true}).click();await ready();assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-cart')).items.map(i=>i.product.id)),[13]);
   await page.getByRole('checkbox',{name:'Select all',exact:true}).check();await page.getByRole('button',{name:'Remove selected (1)',exact:true}).click();await page.waitForURL('**/menu');
  }
  assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-cart')??'{"items":[]}').items.length),0);assert.equal(orders.length,0);assert.equal(payments,0);await context.close();continue;
 }
 if(scenario.startsWith('fee-')){await page.getByRole('button',{name:/^Price details/}).click();const line=page.locator('dl div').filter({hasText:'Online payment fee'});assert.match(await line.textContent(),/2%/);assert.match(await line.textContent(),new RegExp(`₹${fee(payable()-rebate).toFixed(2)}`));assert.match(await line.textContent(),new RegExp(`₹${feeTax(fee(payable()-rebate)).toFixed(2)}`));const breakdown=await page.locator('dl').textContent();assert.match(breakdown,new RegExp(`₹${finalTotal().toFixed(2)}`));await page.getByRole('dialog',{name:'Bill summary'}).getByRole('button',{name:/Close/}).click();}
 if(process.env.SCREENSHOT_DIR){await mkdir(process.env.SCREENSHOT_DIR,{recursive:true});await page.screenshot({path:`${process.env.SCREENSHOT_DIR}/single-checkout-${scenario}.png`,fullPage:true});}
 if(scenario==='gateway-loading')await page.evaluate(()=>{const report=()=>console.info('GOKUL_HANDOFF:'+JSON.stringify({loading:!!document.querySelector('.mobile-checkout-loading'),reservation:Array.from(document.querySelectorAll('h2')).some(e=>e.textContent==='Continue your existing order')}));new MutationObserver(report).observe(document.body,{childList:true,subtree:true});report();});
 if(scenario==='stalled-offer'){await page.getByRole('button',{name:'Change offer',exact:true}).click();const choice=page.getByRole('dialog',{name:'Choose an offer',exact:true});await choice.getByLabel('Have an offer code?',{exact:true}).fill('SAVE20');await choice.locator('form').getByRole('button',{name:'Apply',exact:true}).click();await ready();}
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);if(scenario==='stale-identity')signedIn=false;await page.getByRole('button',{name:'Pay now',exact:true}).dblclick();
 if(scenario==='stalled-offer'){
  await rebateReady;await page.getByRole('alert').filter({hasText:'confirm the offer update'}).waitFor({timeout:22000});assert.equal(orders.length,1);assert.equal(payments,0);
  const cartBefore=await page.evaluate(()=>localStorage.getItem('gokul-cart'));await page.reload();await page.getByRole('link',{name:'Review reserved total',exact:true}).waitFor();
  assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-pending-order')).offerRecheckRequired),true);
  releaseRebate();await page.goto(`${base}/checkout/payment/TEST-SINGLE`);await page.waitForURL('**/checkout/offers/TEST-SINGLE');
  const review=page.getByRole('checkbox',{name:'I have reviewed the updated total and offers.',exact:true});await review.waitFor();assert.equal(await review.isDisabled(),true);assert.equal(payments,0);
  await page.getByRole('button',{name:'Recheck reserved total',exact:true}).click();await page.getByText('Reserved total recovered. Review the confirmed amount before payment.',{exact:true}).waitFor();
  assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-pending-order')).totalAmount),finalTotal());assert.equal(await review.isChecked(),false);
  await page.reload();await review.waitFor();assert.equal(await review.isChecked(),false);assert.equal(payments,0);assert.equal(await page.evaluate(()=>localStorage.getItem('gokul-cart')),cartBefore);
  holdOffers=true;await page.getByRole('button',{name:'Find available offers',exact:true}).click();await page.getByRole('button',{name:'Find available offers',exact:true}).waitFor({state:'hidden'});await page.getByRole('button',{name:'Find available offers',exact:true}).waitFor({timeout:22000});assert.equal(payments,0);holdOffers=false;releaseOffers();console.log('Stalled optional offer discovery releases controls');
  await review.check();await page.getByRole('button',{name:'Continue to payment',exact:true}).last().click();await page.waitForURL('**/checkout/payment/TEST-SINGLE');await page.waitForFunction(()=>JSON.parse(localStorage.getItem('gokul-pending-payment')??'null')?.orderNumber==='TEST-SINGLE');assert.equal(orders.length,1);assert.equal(payments,1);
  console.log('Stalled manual offer recovery, reload and payment guard passed');await context.close();continue;
 }
 if(scenario==='gateway-loading'){
  await rebateReady;await page.getByText('Opening secure payment…',{exact:true}).waitFor();assert.equal(await page.getByRole('heading',{name:'Continue your existing order',exact:true}).count(),0);assert.equal(orders.length,1);assert.equal(payments,0);
  if(process.env.SCREENSHOT_DIR)await page.screenshot({path:`${process.env.SCREENSHOT_DIR}/gateway-loading.png`});
  releaseRebate();await gatewayReady;await new Promise(resolve=>setTimeout(resolve,150));assert.equal(handoffEvents.at(-1)?.loading,true);assert.equal(handoffEvents.at(-1)?.reservation,false);releaseGateway();await page.waitForURL('https://gateway.example.invalid/pay');assert.equal(orders.length,1);assert.equal(payments,1);await context.close();continue;
 }
 if(scenario==='stale-identity'){await page.getByRole('alert').filter({hasText:'Verify the order'}).waitFor();assert.equal(orders.length,0);assert.equal(payments,0);await page.getByRole('button',{name:'Verify phone to continue',exact:true}).click();await ready();await page.getByRole('button',{name:'Pay now',exact:true}).click();await page.waitForURL('https://gateway.example.invalid/pay');assert.equal(orders.length,1);assert.equal(payments,1);}
 else if(scenario==='changed-total'){await page.getByRole('alert').filter({hasText:'total changed'}).waitFor();assert.equal(orders.length,1);assert.equal(payments,0);assert.equal(await page.getByRole('link',{name:'Continue payment',exact:true}).count(),1);}
 else if(scenario==='uncertain-changed'){await page.getByRole('alert').filter({hasText:'couldn’t confirm'}).waitFor();await page.evaluate(()=>{localStorage.setItem('gokul-cart',JSON.stringify({branchId:2,items:[]}));localStorage.removeItem('gokul-selected-branch');localStorage.removeItem('gokul-selected-pickup-slot');});await page.reload();await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});await page.getByRole('button',{name:'Retry checkout',exact:true}).click();await page.getByRole('alert').filter({hasText:'checkout changed elsewhere'}).waitFor();assert.equal(orders.length,2);assert.equal(payments,0);assert.deepEqual(orders[0],orders[1]);assert.equal(await page.getByRole('link',{name:'Continue payment',exact:true}).count(),1);assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-cart')).items.length),0);}
 else{if(scenario.startsWith('uncertain-')){await page.getByRole('alert').filter({hasText:'couldn’t confirm'}).waitFor();assert.equal(await page.getByRole('button',{name:'Add one Paneer meal Half',exact:true}).isDisabled(),true);assert.equal(await page.getByRole('button',{name:'Clear cart',exact:true}).isDisabled(),true);assert.equal(await page.getByRole('button',{name:'Remove Paneer meal Half from cart',exact:true}).isDisabled(),true);await page.reload();await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});await page.getByRole('button',{name:'Retry checkout',exact:true}).click();if(scenario==='uncertain-identity'){await page.getByRole('button',{name:'Verify phone to continue',exact:true}).click();await page.getByRole('button',{name:'Retry checkout',exact:true}).click();}}await page.waitForURL('https://gateway.example.invalid/pay');assert.equal(payments,1);assert.equal(orders.length,scenario==='uncertain-identity'?3:scenario==='uncertain-order'?2:1);assert.equal(checkoutRequest.items.find(i=>i.productId===11).quantity,1);assert.equal(checkoutRequest.quoteToken,'signed-quote');if(scenario==='priority-only'||scenario==='stored-priority')assert.equal(checkoutRequest.pickupType,'PRIORITY');if(scenario.startsWith('uncertain-')){for(const attempt of orders){assert.equal(orders[0].key,attempt.key);assert.deepEqual(orders[0].request,attempt.request);}}}
 if(scenario==='phone'){
  await page.goto(`${base}/checkout/mobile`);await page.waitForURL('**/checkout/payment/TEST-SINGLE');await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});
  await page.getByRole('link',{name:'Back to menu',exact:true}).click();
  await page.waitForURL('**/menu');
  await page.locator('.gokul-floating-cart a').click();await page.waitForURL('**/checkout/mobile');
  await ready();await page.reload();await ready();assert.deepEqual(errors,[]);
  assert.equal(cancels,1);assert.equal(orders.length,1);assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-cart')).items.length),3);
  assert.equal(await page.getByRole('heading',{name:'Continue your existing order',exact:true}).count(),0);
  await page.getByRole('button',{name:'Remove one Paneer meal Half',exact:true}).click();
  await page.getByRole('button',{name:'Remove one Paneer meal Half',exact:true}).click();
  await page.getByRole('button',{name:'Remove one Paneer meal Full',exact:true}).click();
  await page.getByRole('button',{name:'Remove one Fresh drink',exact:true}).click();
  await page.waitForURL('**/menu');
  assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-cart')??'{"items":[]}').items.length),0);
 }
 await context.close();
}console.log('PASS: phone-only portions and filters, inline OTP, direct payment, changed totals blocked, safe interrupted retries and unchanged desktop route.');}finally{await browser.close();}
