import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
async function documentBox(page,selector){return page.locator(selector).evaluate(node=>{const box=node.getBoundingClientRect();return {y:box.top+scrollY,x:box.left+scrollX};});}
async function waitForMock(check){const deadline=Date.now()+15000;while(!check()){assert.ok(Date.now()<deadline,'mock request started before deadline');await new Promise(r=>setTimeout(r,50));}}
async function openPairings(page){
 const sheet=page.getByRole('dialog',{name:'Optional additions',exact:true});if(await sheet.isVisible())return;
 await page.waitForFunction(()=>{const button=document.getElementById('mobile-menu-pairings-open');return button&&!button.disabled;});
 await page.getByRole('button',{name:'View optional additions',exact:true}).click();await sheet.waitFor();
}
async function closePairings(page){const sheet=page.getByRole('dialog',{name:'Optional additions',exact:true});if(await sheet.isVisible())await sheet.getByRole('button',{name:/^Close/}).click();}
const browser=await chromium.launch({headless:true}),base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
const today=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata'}).format(new Date());
const date=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata'}).format(new Date(Date.now()+86400000));
const branch={id:1,code:'GUIDE',name:'Gokul Tamkuhi Road',active:true,operational:true,pickupAvailable:true,address:'Main Road, opposite the bus stand',city:'Tamkuhi Road',pincode:'274407',coverImageUrl:'/logo.png'};
const sweet={id:1,categoryId:1,categoryName:'Sweets',name:'Fresh peda',description:'Fresh milk sweet.',price:400,imageUrl:null,available:true,saleMode:'WEIGHT',minimumWeightGrams:250,weightStepGrams:50};
const tea={...sweet,id:2,categoryId:2,categoryName:'Drinks',name:'Special tea',price:30,saleMode:'UNIT',minimumWeightGrams:null,weightStepGrams:null};
const favourite={...tea,id:4,name:'Masala chai',price:35};
const sold={...tea,id:3,name:'Sold-out samosa',available:false};
const slot={id:7,branchId:1,slotDate:date,startTime:'15:00:00',endTime:'16:00:00',active:true,remainingCapacity:10,priorityEnabled:false,priorityRemainingCapacity:0,priorityCharge:0};
const offer={rebateId:1,code:'SAVE',name:'Sweet saving',description:'Eligible food only',scope:'GENERAL',rebateType:'SLAB',rebateAmount:0,payableAfterRebate:100,minimumOrderAmount:150,maximumDiscountAmount:20,nextSlabMinimumOrderAmount:150,nextSlabRebateAmount:10,amountNeededForNextSlab:25};
try{for(const [width,enabled,constrained] of [[390,true,false],[390,true,true]]){
 const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:'block'}),page=await context.newPage();page.setDefaultTimeout(15000);
 let previewCalls=0,addonChecks=0,historyCalls=0,inventoryCalls=0,favouriteStock=false,confirmationFails=false,availabilityCalls=0,smart=true,signedIn=true,availabilityFails=false,holdRecommendations=false,releaseRecommendations,hold=false,release,releasePreview,checkStarted,recommendationsEmpty=false,longText=false,releaseRatings,releaseAvailability;const ratingsGate=new Promise(r=>releaseRatings=r),availabilityGate=new Promise(r=>releaseAvailability=r); const started=new Promise(r=>checkStarted=r);const previewGate=new Promise(r=>releasePreview=r);const errors=[];page.on('pageerror',e=>errors.push(e.message));
 const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Methods':'GET,POST,OPTIONS','Access-Control-Allow-Headers':'content-type,idempotency-key'};
 await context.route('**/api/**',async route=>{const p=new URL(route.request().url()).pathname;let json=[];
  if(route.request().method()==='OPTIONS')return route.fulfill({status:204,headers});
  if(constrained)await new Promise(r=>setTimeout(r,150));
  if(p==='/api/storefront/features')json={futuristicStorefrontV2:enabled,checkoutExperienceV2:enabled&&smart,contextualStorefrontV2:true,simplifiedCheckout:true,acceptedCheckoutQuote:true,branchExperience:true,preHomeIntentGateway:true,smartAvailability:smart,pickupAddOns:true,gokulRewards:true,futureOrderingDays:30,today};
  else if(p==='/api/branches')json=[branch];else if(p==='/api/branches/1')json=branch;
  else if(p==='/api/menu')json=[{id:1,name:'Sweets',products:[sweet]},{id:2,name:'Drinks',products:[tea,sold,favourite]}];
  else if(p==='/api/menu/portion-groups')json={groups:[]};
  else if(p==='/api/reviews/product-summaries'){await ratingsGate;json=[{productId:1,averageRating:4.8,ratingCount:12}];}
  else if(p==='/api/storefront/customer-identity')json={enabled:true,guestCheckoutEnabled:false};
  else if(p==='/api/customer/identity/orders'){historyCalls++;json=[{orderNumber:'PREVIOUS',branchId:1,orderStatus:'PICKED_UP'},{orderNumber:'UNPAID',branchId:1,orderStatus:'CONFIRMED'}];}
  else if(p==='/api/customer/identity/orders/PREVIOUS')json={branchId:1,orderStatus:'PICKED_UP',paymentStatus:'PAID',items:[{productId:4}]};
  else if(p==='/api/customer/identity/me')json={authenticated:signedIn,name:'Test customer',phone:'+919876543210'};
  else if(p==='/api/branches/1/availability'){
   availabilityCalls++;await availabilityGate;if(availabilityFails)return route.fulfill({status:503,json:{message:'Availability temporarily unavailable'},headers});if(!smart)return route.fulfill({status:404,json:{message:'Disabled'},headers});const body=route.request().postDataJSON();const valid=body.startDate===date;
   json={today,maximumDate:date,dates:[{date:body.startDate,available:valid,items:[{productId:1,available:true},{productId:2,available:true}],slots:valid?[{slot,normalAvailable:true,priorityAvailable:false,issues:[{productId:3,available:false}]}]:[]},...(body.days>1?[{date,available:true,slots:[{slot,normalAvailable:true,priorityAvailable:false,issues:[{productId:3,available:false}]}]}]:[])]};
   if(body.days===1&&body.startDate===date&&confirmationFails&&page.url().includes('/menu')&&await page.getByRole('dialog').count())json.dates[0].slots=[];
  }
  else if(p==='/api/menu/pickup-addons'){if(holdRecommendations)await new Promise(r=>releaseRecommendations=r);json=recommendationsEmpty?[]:[{product:tea,weightGrams:null,portionPrice:30,portionTotal:30,reason:longText?'A carefully described optional addition. '.repeat(40):'Often ordered with Fresh peda'},{product:sold,weightGrams:null,portionPrice:30,portionTotal:30,reason:'A branch favourite'}];}
  else if(p==='/api/menu/pickup-addons/check'){addonChecks++;checkStarted();if(hold)await new Promise(r=>release=r);json={orderable:true};}
  else if(p==='/api/branches/1/inventory/check'){inventoryCalls++;json={enforcementEnabled:true,requestedDate:date,orderable:favouriteStock,items:[{productId:4,orderable:favouriteStock,availableQuantity:favouriteStock?10:0}]};}
  else if(p==='/api/orders/mobile-preview'){previewCalls++;await previewGate;json={quote:{token:'preview',expiresAt:new Date(Date.now()+600000).toISOString(),subtotal:100,taxAmount:0,totalAmount:100,convenienceFee:0,paymentFee:0},offers:[],spendTargets:[offer],totalBeforeOffer:100,rewards:{balance:27,pendingCoins:33,rewards:[{code:'SWEET_5',name:'₹5 sweet saving',coins:30,minimumSubtotal:149,eligible:false,unavailableReason:'Not enough coins'}],terms:'Fees are excluded.'}};}
  try{return await route.fulfill({json,headers});}catch{/* Navigation may abort a deliberately delayed request. */}
 });
 await context.addInitScript(({branch,sweet,slot,date})=>{if(!localStorage.getItem('gokul-cart')){localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));localStorage.setItem('gokul-cart',JSON.stringify({branchId:1,items:[{product:sweet,quantity:0,weightGrams:250}]}));localStorage.setItem('gokul-selected-pickup-slot',JSON.stringify({date,slot,pickupType:'NORMAL'}));localStorage.setItem('gokul-pickup-intent',JSON.stringify({branchId:1,date}));}}, {branch,sweet,slot,date});
 if(constrained)await context.addInitScript(()=>Object.defineProperty(navigator,'connection',{configurable:true,value:{effectiveType:'3g',saveData:false}}));
 await page.goto(`${base}/menu`); await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'}); await page.locator('#gokul-product-2').waitFor();await page.locator('.mobile-menu-pickup').waitFor();const beforeRatings=await documentBox(page,'#gokul-product-2'),pickupBeforeRatings=await documentBox(page,'.mobile-menu-pickup');releaseRatings();await page.getByRole('button',{name:'Add Fresh peda from favourites',exact:true}).waitFor();assert.ok(Math.abs((await documentBox(page,'#gokul-product-2')).y-beforeRatings.y)<=1,'late ratings cannot move products');assert.ok(Math.abs((await documentBox(page,'.mobile-menu-pickup')).y-pickupBeforeRatings.y)<=1,'late ratings cannot move pickup controls');releaseAvailability();await page.getByRole('button',{name:'Add Fresh peda from favourites',exact:true}).waitFor();await page.waitForFunction(()=>!document.querySelector('[aria-label="Add Fresh peda from favourites"]')?.disabled);assert.ok(Math.abs((await documentBox(page,'#gokul-product-2')).y-beforeRatings.y)<=1,'late availability cannot move products');assert.ok(Math.abs((await documentBox(page,'.mobile-menu-pickup')).y-pickupBeforeRatings.y)<=1,'late availability cannot move pickup');console.log('Late ratings and availability preserve menu layout');const pairing=page.getByRole('region',{name:'Pairs well with your selection'});await openPairings(page);await pairing.getByRole('button',{name:'Add Special tea',exact:true}).waitFor({timeout:4000});
 if(!constrained)await pairing.getByText('Your completed-order favourite',{exact:true}).waitFor();assert.ok(constrained?historyCalls===0:historyCalls>=1,'constrained phones skip optional history downloads');
 await page.waitForTimeout(350); // Let the sheet's focus/scroll transition settle before comparing geometry.
 const suggestionAction=await pairing.getByRole('button',{name:'Add Special tea',exact:true}).boundingBox();const following=await page.locator('#gokul-product-2').boundingBox();const cartAction=await page.locator('.gokul-floating-cart a').boundingBox();
 assert.ok(previewCalls>0);assert.equal(await pairing.getByText(/Unlock/).count(),0,'pairings are usable while offer verification is pending');releasePreview();await page.locator('.mobile-cart-offer-target').getByText(/Unlock/).waitFor();
 const settledAction=await pairing.getByRole('button',{name:'Add Special tea',exact:true}).boundingBox();
 assert.ok(Math.abs(settledAction.y-suggestionAction.y)<=1&&Math.abs(settledAction.x-suggestionAction.x)<=1,'late offer cannot move a suggestion Add control in either direction');
 assert.ok(Math.abs((await page.locator('#gokul-product-2').boundingBox()).y-following.y)<=1,'late offer cannot move the following product');
 assert.ok(Math.abs((await page.locator('.gokul-floating-cart a').boundingBox()).y-cartAction.y)<=1,'late offer cannot move Continue');
 if(!constrained){
  holdRecommendations=true;signedIn=false;await page.evaluate(()=>window.dispatchEvent(new Event('gokul-customer-identity-changed')));
  await pairing.getByText('Your completed-order favourite',{exact:true}).waitFor({state:'hidden',timeout:1000});
  await page.waitForFunction(()=>!Array.from(document.querySelectorAll('.mobile-menu-suggestions p')).some(e=>e.textContent==='Your completed-order favourite'));
  await waitForMock(()=>!!releaseRecommendations);holdRecommendations=false;releaseRecommendations();
  signedIn=true;await page.evaluate(()=>window.dispatchEvent(new Event('gokul-customer-identity-changed')));await openPairings(page);await pairing.getByText('Your completed-order favourite',{exact:true}).waitFor();
 }
 if(!constrained){
  const other=await context.newPage();await other.goto(`${base}/about`);signedIn=false;
  await other.evaluate(()=>localStorage.setItem('gokul-customer-identity-revision',crypto.randomUUID()));
  await page.getByRole('dialog',{name:'Optional additions',exact:true}).waitFor({state:'hidden',timeout:2000});
  assert.equal(await pairing.getByText('Your completed-order favourite',{exact:true}).count(),0,'another tab logout removes personalized content');
  signedIn=true;await other.evaluate(()=>localStorage.setItem('gokul-customer-identity-revision',crypto.randomUUID()));await other.close();await page.bringToFront();await openPairings(page);await pairing.getByText('Your completed-order favourite',{exact:true}).waitFor();
  console.log('Cross-tab identity invalidation and resume passed');
 }
 await closePairings(page);
 assert.ok((await page.locator('.mobile-menu-pairing-slot').boundingBox()).height<280,'photo-led suggestions retain their bounded footprint');
 assert.equal(await page.locator('.mobile-menu-pairing-slot').evaluate(node=>getComputedStyle(node).overflowY),'visible','menu suggestions do not trap vertical scrolling');
 await openPairings(page);await page.keyboard.press('Escape');await page.getByRole('dialog',{name:'Optional additions',exact:true}).waitFor({state:'hidden'});
 assert.equal(await page.getByRole('button',{name:'View optional additions',exact:true}).evaluate(node=>document.activeElement===node),true,'dismissal restores focus');
 longText=true;await page.evaluate(()=>window.dispatchEvent(new Event('gokul-customer-identity-changed')));await openPairings(page);
 const longSheet=page.getByRole('dialog',{name:'Optional additions',exact:true});await longSheet.getByText(/A carefully described optional addition/).waitFor();
 assert.ok(await longSheet.evaluate(node=>node.scrollHeight>node.clientHeight),'long text remains accessible through the explicit sheet scroll');
 await page.keyboard.press('Tab');assert.equal(await longSheet.evaluate(node=>node.contains(document.activeElement)),true,'keyboard focus stays in the modal');
 await page.mouse.click(8,8);await longSheet.waitFor({state:'hidden'});longText=false;
 recommendationsEmpty=true;signedIn=false;await page.evaluate(()=>window.dispatchEvent(new Event('gokul-customer-identity-changed')));
 await pairing.getByText('No optional additions right now.',{exact:true}).waitFor();
 assert.ok((await page.locator('.mobile-menu-pairing-slot').boundingBox()).height<280,'empty suggestions do not reserve 420px');
 recommendationsEmpty=false;signedIn=true;await page.evaluate(()=>window.dispatchEvent(new Event('gokul-customer-identity-changed')));
  availabilityFails=true;await page.reload();const retry=page.getByRole('button',{name:'Retry availability',exact:true});await retry.waitFor();
 assert.equal(await page.getByRole('button',{name:'Add Fresh peda from favourites',exact:true}).isDisabled(),true);
 availabilityFails=false;await retry.click();await retry.waitFor({state:'hidden'});
 await page.waitForFunction(()=>Array.from(document.querySelectorAll('button')).some(b=>b.getAttribute('aria-label')==='Add Fresh peda from favourites'&&!b.disabled));
 console.log('Availability failure exposes recovery and restores favourites');await page.reload();await openPairings(page);await pairing.getByRole('button',{name:'Add Special tea',exact:true}).waitFor();
 hold=true;await pairing.getByRole('button',{name:'Add Special tea',exact:true}).click();await started;assert.equal(addonChecks,1);
 await closePairings(page);await page.locator('a.future-brand').click();await page.waitForURL(base+'/');release();await page.waitForTimeout(500);assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-cart')).items.length),1,'an abandoned Add cannot change cart');console.log('Delayed Add cancellation passed');
 await page.evaluate(()=>{const cart=JSON.parse(localStorage.getItem('gokul-cart'));cart.items=cart.items.filter(i=>i.product.id===1);localStorage.setItem('gokul-cart',JSON.stringify(cart));});smart=false;hold=false;
 const checksBefore=availabilityCalls;await page.goto(`${base}/menu`);await openPairings(page);await pairing.getByRole('button',{name:'Add Special tea',exact:true}).waitFor();await pairing.getByRole('button',{name:'Add Special tea',exact:true}).click();await page.waitForFunction(()=>JSON.parse(localStorage.getItem('gokul-cart')).items.length===2);assert.equal(availabilityCalls,checksBefore,'smart availability OFF never calls its disabled endpoint');console.log('Independent add-on flag passed');
 if(!constrained){assert.ok(inventoryCalls>0,'historical candidates are stock-checked with smart availability OFF');assert.equal(await pairing.getByRole('button',{name:'Add Masala chai',exact:true}).count(),0,'sold-out historical favourites are hidden');
  smart=true;await page.evaluate(()=>localStorage.removeItem('gokul-selected-pickup-slot'));await page.goto(`${base}/menu`);await pairing.getByText('No optional additions right now.',{exact:true}).waitFor();assert.equal(await page.getByRole('button',{name:'View optional additions',exact:true}).isDisabled(),true);assert.equal(await pairing.getByRole('button',{name:'Add Masala chai',exact:true}).count(),0);
  favouriteStock=true;await page.reload();await openPairings(page);await pairing.getByRole('button',{name:'Add Masala chai',exact:true}).waitFor();assert.ok(inventoryCalls>1,'date-only selections batch-check historical favourites');
 }
 await closePairings(page);smart=true;await page.evaluate(()=>{const cart=JSON.parse(localStorage.getItem('gokul-cart'));cart.items=cart.items.filter(i=>i.product.id===1);localStorage.setItem('gokul-cart',JSON.stringify(cart));window.dispatchEvent(new Event('gokul-cart-change'));});await page.goto(`${base}/menu`);await page.locator('.mobile-menu-pickup').waitFor();await page.goto(`${base}/checkout/mobile`);await page.getByRole('button',{name:/Price details/}).waitFor();await page.evaluate(()=>window.scrollTo({top:900,behavior:"instant"}));const before=await page.evaluate(()=>scrollY);await page.evaluate(()=>document.dispatchEvent(new Event('visibilitychange')));const after=await page.evaluate(()=>scrollY);console.log('Checkout resume scroll', {before,after});assert.ok(before>0&&after===before,'ordinary app resume preserves checkout position');assert.deepEqual(errors,[]);
 const checkoutAdds=page.getByRole('region',{name:'Pickup add-ons'});await checkoutAdds.getByRole('button',{name:'Add Special tea',exact:true}).waitFor();
 hold=true;const abandoned=addonChecks;await checkoutAdds.getByRole('button',{name:'Add Special tea',exact:true}).click();
 await waitForMock(()=>addonChecks>abandoned);
 const saved=await page.evaluate(()=>localStorage.getItem('gokul-cart'));await page.getByRole('link',{name:'Back to menu',exact:true}).click();await page.waitForURL('**/menu');release();
 await page.waitForTimeout(500);assert.equal(await page.evaluate(()=>localStorage.getItem('gokul-cart')),saved,'abandoned checkout addition cannot change cart');
 await page.goto(`${base}/checkout/mobile`);await checkoutAdds.getByRole('button',{name:'Add Special tea',exact:true}).waitFor();
 await checkoutAdds.getByRole('button',{name:'Add Special tea',exact:true}).click();
 await checkoutAdds.getByRole('status').filter({hasText:'We couldn’t complete this addition'}).waitFor({timeout:20000});
 assert.equal(await checkoutAdds.getByRole('button',{name:'Add Special tea',exact:true}).isEnabled(),true);assert.equal(await page.getByRole('button',{name:'Clear cart',exact:true}).isEnabled(),true);
 hold=false;release();await checkoutAdds.getByRole('button',{name:'Add Special tea',exact:true}).click();await page.waitForFunction(()=>JSON.parse(localStorage.getItem('gokul-cart')).items.some(i=>i.product.id===2));
 console.log('Checkout Add abandonment, deadline and retry passed');assert.deepEqual(errors,[]);

 await context.close();}}finally{await browser.close();}
