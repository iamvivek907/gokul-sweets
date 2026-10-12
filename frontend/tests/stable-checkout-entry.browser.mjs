import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const browser=await chromium.launch({headless:true}),base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
const date=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata'}).format(new Date(Date.now()+86400000));
const today=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata'}).format(new Date());
const branch={id:1,name:'Test branch',code:'TEST',address:'Main Road',active:true,operational:true,pickupAvailable:true};
const product={id:1,name:'Fresh cake',categoryId:1,categoryName:'Cakes',price:300,available:true,saleMode:'UNIT',description:'Freshly prepared.',imageUrl:null};
const slot={id:1,branchId:1,slotDate:date,startTime:'18:00:00',endTime:'19:00:00',remainingCapacity:20,active:true,priorityEnabled:false};
try{for(const [width,mode] of [[320,'guest'],[390,'verified'],[640,'outage']]){
 const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:'block'}),page=await context.newPage();page.setDefaultTimeout(15000);
 let releaseIdentity,releasePickup,releasePrice,writes=0;
 const identityGate=new Promise(r=>releaseIdentity=r),pickupGate=new Promise(r=>releasePickup=r),priceGate=new Promise(r=>releasePrice=r);
 const errors=[];page.on('pageerror',e=>errors.push(e.message));
 const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Methods':'GET,POST,OPTIONS','Access-Control-Allow-Headers':'content-type,idempotency-key'};
 await context.route('**/api/**',async route=>{const request=route.request(),path=new URL(request.url()).pathname;let json=[];
  if(request.method()==='OPTIONS')return route.fulfill({status:204,headers});
  if(path==='/api/storefront/features')json={futuristicStorefrontV2:true,checkoutExperienceV2:true,contextualStorefrontV2:true,simplifiedCheckout:true,acceptedCheckoutQuote:true,branchExperience:true,smartAvailability:true,today,futureOrderingDays:30};
  else if(path==='/api/branches')json=[branch];else if(path==='/api/branches/1')json=branch;
  else if(path==='/api/menu')json=[{id:1,name:'Cakes',products:[product]}];
  else if(path==='/api/menu/portion-groups')json={groups:[]};
  else if(path==='/api/storefront/customer-identity')json={enabled:true,guestCheckoutEnabled:false};
  else if(path==='/api/customer/identity/me'){await identityGate;if(mode==='outage')return route.fulfill({status:503,headers,json:{message:'Unavailable'}}).catch(()=>{});json={authenticated:mode==='verified',name:'Test customer',phone:'+919876543210'};}
  else if(path==='/api/branches/1/availability'){await pickupGate;json={today,maximumDate:date,dates:[{date,available:true,items:[{productId:1,available:true}],slots:[{slot,normalAvailable:true,priorityAvailable:false,issues:[]}]}]};}
  else if(path==='/api/orders/mobile-preview'){await priceGate;json={quote:{token:'preview',expiresAt:new Date(Date.now()+600000).toISOString(),subtotal:300,taxAmount:0,totalAmount:300,convenienceFee:0,paymentFee:0},offers:[{rebateId:1,code:'SAVE12',name:'Welcome saving',rebateAmount:12,payableAfterRebate:288}],totalBeforeOffer:300};}
  else if(request.method()!=='GET'&&(path==='/api/orders'||path.startsWith('/api/payments')||path.includes('/payment')))writes++;
  return route.fulfill({headers,json}).catch(()=>{});
 });
 await context.addInitScript(({branch,product,slot,date})=>{localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));localStorage.setItem('gokul-cart',JSON.stringify({branchId:1,items:[{product,quantity:1}]}));localStorage.setItem('gokul-selected-pickup-slot',JSON.stringify({date,slot,pickupType:'NORMAL'}));},{branch,product,slot,date});
 await page.goto(`${base}/menu`);await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});await page.locator('.gokul-floating-cart a').click();await page.waitForURL('**/checkout/mobile');
 const identity=page.getByRole('region',{name:'Phone verification',exact:true}),cart=page.getByRole('region',{name:'Cart items',exact:true});
 await identity.getByRole('status').getByText('Checking phone verification…',{exact:true}).waitFor();await cart.waitFor();
 const top=(await cart.boundingBox()).y,cartBefore=await page.evaluate(()=>localStorage.getItem('gokul-cart'));
 assert.equal(await identity.getByRole('button',{name:'Verify with SMS',exact:true}).count(),0,'no false sign-in action before identity resolves');
 assert.equal(await page.locator('.mobile-checkout-pay button').isDisabled(),true);assert.equal(await page.locator('dialog.offer-arrival').isVisible(),false,'identity and pickup discovery never open a checking modal');
 assert.equal(await page.locator('.mobile-checkout-pay button').evaluate(n=>getComputedStyle(n).backgroundColor),'rgb(143, 24, 56)');
 releaseIdentity();releasePickup();releasePrice();
 if(mode==='guest'){await identity.getByRole('heading',{name:'Verify your phone',exact:true}).waitFor();assert.equal(await identity.getByRole('button',{name:'Verify with SMS',exact:true}).count(),0);assert.equal(await page.getByRole('button',{name:'Verify phone to continue',exact:true}).isEnabled(),true);}
 else if(mode==='verified'){await identity.getByRole('heading',{name:'Phone verified',exact:true}).waitFor();await page.locator('.mobile-checkout-savings').getByText(/₹12.00/).waitFor();}
 else await identity.getByRole('heading',{name:'Phone verification is unavailable',exact:true}).waitFor();
 if(mode==='verified'){
  const panel=await identity.boundingBox();
  assert.ok(panel.height<125,'verified phone card fits its content instead of reserving action space');
  assert.ok((await cart.boundingBox()).y<top-50,'verified checkout releases the unused phone-verification space');
 }else assert.ok(Math.abs((await cart.boundingBox()).y-top)<=1,'unverified settlement preserves cart position');
 const celebration=page.locator('dialog.offer-arrival');
 if(mode==='verified'){
  await celebration.waitFor({state:'visible'});await celebration.getByRole('heading',{name:'SAVE12 applied',exact:true}).waitFor();
  assert.equal(await celebration.locator('.offer-confetti i').count(),24);
  await celebration.getByRole('button',{name:'Woohoo! Thanks',exact:true}).click();
 }else assert.equal(await celebration.isVisible(),false,'guest and identity outage never claim verified savings');
 assert.equal(await page.evaluate(()=>localStorage.getItem('gokul-cart')),cartBefore);assert.equal(writes,0,'entry makes no order or payment writes');
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);assert.deepEqual(errors,[]);
 await context.close();console.log(`Stable checkout entry ${width}px ${mode} passed`);
}}finally{await browser.close();}
