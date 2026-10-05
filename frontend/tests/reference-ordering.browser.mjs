import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
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
try{for(const [width,enabled,constrained] of [[320,true,false],[390,true,false],[640,true,false]]){
 const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:'block'}),page=await context.newPage();page.setDefaultTimeout(15000);
 let branchReads=0,releaseHistory,releaseRatings,releaseAvailability,releasePreview;
 const historyGate=new Promise(r=>releaseHistory=r),ratingsGate=new Promise(r=>releaseRatings=r),availabilityGate=new Promise(r=>releaseAvailability=r),previewGate=new Promise(r=>releasePreview=r);
 const errors=[];page.on('pageerror',e=>errors.push(e.message));const smart=true,signedIn=false;
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
  else if(p==='/api/customer/identity/orders'){await historyGate;json=[{orderNumber:'PREVIOUS',branchId:1,orderStatus:'PICKED_UP'},{orderNumber:'UNPAID',branchId:1,orderStatus:'CONFIRMED'}];}
  else if(p==='/api/customer/identity/orders/PREVIOUS')json={branchId:1,orderStatus:'PICKED_UP',paymentStatus:'PAID',items:[{productId:4}]};
  else if(p==='/api/customer/identity/me')json={authenticated:signedIn,name:'Test customer',phone:'+919876543210'};
  else if(p==='/api/branches/1/availability'){
   await availabilityGate;const body=route.request().postDataJSON();const valid=body.startDate===date;
   json={today,maximumDate:date,dates:[{date:body.startDate,available:valid,items:[{productId:1,available:true},{productId:2,available:true}],slots:valid?[{slot,normalAvailable:true,priorityAvailable:false,issues:[{productId:3,available:false}]}]:[]},...(body.days>1?[{date,available:true,slots:[{slot,normalAvailable:true,priorityAvailable:false,issues:[{productId:3,available:false}]}]}]:[])]};
  }
  else if(p==='/api/menu/pickup-addons'){branchReads++;json=[{product:tea,slotVerified:true,weightGrams:null,portionPrice:30,portionTotal:30,reason:'Often ordered with Fresh peda'},{product:{...sold,available:true,categoryName:"Sides"},slotVerified:true,weightGrams:null,portionPrice:30,portionTotal:30,reason:'A branch favourite'}];}
  else if(p==='/api/orders/mobile-preview'){await previewGate;json={quote:{token:'preview',expiresAt:new Date(Date.now()+600000).toISOString(),subtotal:100,taxAmount:0,totalAmount:100,convenienceFee:0,paymentFee:0},offers:[],spendTargets:[offer],totalBeforeOffer:100,rewards:{balance:27,pendingCoins:33,rewards:[{code:'SWEET_5',name:'₹5 sweet saving',coins:30,minimumSubtotal:149,eligible:false,unavailableReason:'Not enough coins'}],terms:'Fees are excluded.'}};}
  try{return await route.fulfill({json,headers});}catch{/* Navigation may abort a deliberately delayed request. */}
 });
 await context.addInitScript(({branch,sweet,slot,date})=>{if(!localStorage.getItem('gokul-cart')){localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));localStorage.setItem('gokul-cart',JSON.stringify({branchId:1,items:[{product:sweet,quantity:0,weightGrams:250}]}));localStorage.setItem('gokul-selected-pickup-slot',JSON.stringify({date,slot,pickupType:'NORMAL'}));localStorage.setItem('gokul-pickup-intent',JSON.stringify({branchId:1,date}));}}, {branch,sweet,slot,date});
 if(constrained)await context.addInitScript(()=>Object.defineProperty(navigator,'connection',{configurable:true,value:{effectiveType:'3g',saveData:false}}));
 await page.goto(`${base}/menu`);await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});

 const recommended=page.getByRole('region',{name:'Recommended menu items',exact:true}),sweets=page.getByRole('region',{name:'Sweets menu items',exact:true});
 assert.equal(await page.locator('.menu-category-disclosure:not([open])').count(),0,'all menu sections start expanded');
 assert.equal(await recommended.locator('.menu-recommended-grid').evaluate(n=>getComputedStyle(n).gridTemplateColumns.split(' ').length),2);
 assert.equal(await sweets.locator('.gokul-menu-product-grid').evaluate(n=>getComputedStyle(n).gridTemplateColumns.split(' ').length),1,'categories use full-width rows');
 const card=sweets.locator('.gokul-product-card').first();
 const photo=await card.locator('.product-card-image').boundingBox(),copy=await card.locator('.product-card-copy').boundingBox();
 assert.ok(copy.x<photo.x&&copy.y<photo.y+photo.height,'description sits left of the photo');
 await card.getByText('Fresh milk sweet.',{exact:true}).waitFor();
 await recommended.locator('summary').click();assert.equal(await recommended.getByRole('button',{name:'Add Fresh peda from recommendations',exact:true}).isVisible(),false);
 releaseRatings();await page.waitForFunction(()=>document.querySelector('#menu-category-1 .product-card-rating')?.textContent.includes('4.8'));
 assert.equal(await recommended.locator('details').evaluate(n=>n.open),false,'late ratings do not reopen a collapsed section');
 await sweets.locator('summary').click();assert.equal(await card.isVisible(),false);
 await page.reload();await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});await card.waitFor();
 assert.equal(await page.locator('.menu-category-disclosure:not([open])').count(),0,'refresh opens every section again');
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
 await page.screenshot({path:`/tmp/reference-menu-${width}.png`,fullPage:true});
 const drinks=page.getByRole('region',{name:'Drinks menu items',exact:true});await drinks.locator('summary').click();
 await page.getByRole('button',{name:'Browse all item categories',exact:true}).click();const categories=page.getByRole('navigation',{name:'Item categories',exact:true});await categories.waitFor();
 await categories.getByRole('button',{name:/Drinks/}).click();assert.equal(await categories.isVisible(),false);
 await drinks.locator('details[open]').waitFor();assert.equal(await sweets.isVisible(),true,'Items navigates without filtering away other sections');
 await card.locator('.product-rating-summary').waitFor();
 await page.evaluate(()=>{document.documentElement.style.scrollBehavior='auto';window.scrollTo({top:500,behavior:'instant'});});
 await page.waitForTimeout(300);
 const scrollBefore=await page.evaluate(()=>window.scrollY),itemsButton=page.getByRole('button',{name:'Browse all item categories',exact:true});
 await itemsButton.click();const itemsDialog=page.getByRole('dialog',{name:'Items',exact:true});await itemsDialog.waitFor();
 assert.equal(await page.evaluate(()=>document.body.style.position),'fixed');
 const lockedScroll=await page.evaluate(()=>-parseFloat(document.body.style.top));assert.ok(Math.abs(lockedScroll-scrollBefore)<=3,'opening preserves the visible scroll position');
 const beforeWheel=await sweets.boundingBox();await page.mouse.move(2,100);await page.mouse.wheel(0,600);await page.waitForTimeout(150);
 const afterWheel=await sweets.boundingBox();assert.ok(Math.abs(afterWheel.y-beforeWheel.y)<1,'background menu stays still while sheet is open');
 assert.equal(await page.evaluate(()=>document.querySelector('.mobile-menu-suggestion-dialog')?.contains(document.activeElement)),true,'focus remains inside modal');
 await page.keyboard.press('Escape');assert.equal(await categories.isVisible(),false);
 assert.equal(await page.evaluate(()=>window.scrollY),lockedScroll,'dismissal restores original scroll position');
 assert.equal(await itemsButton.evaluate(n=>n===document.activeElement),true,'dismissal restores Items focus');
 assert.equal(await page.evaluate(()=>document.body.style.position),'');
 await itemsButton.click();await itemsDialog.waitFor();await page.mouse.click(2,20);assert.equal(await categories.isVisible(),false,'outside tap dismisses categories');
 const badge=card.locator('.product-rating-summary');assert.equal(await badge.count(),1);
 const contrast=await badge.locator('span').nth(1).evaluate(n=>{const luminance=c=>{const v=c.match(/\d+/g).slice(0,3).map(Number).map(x=>{const s=x/255;return s<=.04045?s/12.92:((s+.055)/1.055)**2.4;});return v[0]*.2126+v[1]*.7152+v[2]*.0722;};const fg=luminance(getComputedStyle(n).color),bg=luminance(getComputedStyle(n.parentElement).backgroundColor);return (Math.max(fg,bg)+.05)/(Math.min(fg,bg)+.05);});assert.ok(contrast>=4.5,'rating badge stays readable');
 await page.screenshot({path:`/tmp/reference-menu-ratings-${width}.png`});
 await itemsButton.click();await itemsDialog.waitFor();await page.screenshot({path:`/tmp/reference-menu-items-${width}.png`});await itemsDialog.getByRole('button',{name:/Close/}).click();
 await page.goto(`${base}/checkout/mobile`);
 await page.getByRole('heading',{name:'Verify your phone',exact:true}).waitFor();
 const meal=page.getByRole('region',{name:'Pickup add-ons',exact:true});await meal.getByRole('heading',{name:'Complete your meal',exact:true}).waitFor();
 await meal.getByRole('button',{name:'Add Special tea',exact:true}).waitFor();
 assert.equal(await meal.getByRole('button',{name:'Add Special tea',exact:true}).isDisabled(),true,'stalled pickup never permits additions');
 const before=branchReads;
 await meal.getByRole('tab',{name:'Beverages',exact:true}).click();await meal.getByRole('tabpanel').getByText('Special tea',{exact:true}).waitFor();
 await meal.getByRole('tab',{name:'Sides',exact:true}).click();await meal.getByRole('tabpanel').getByText('Sold-out samosa',{exact:true}).waitFor();
 assert.equal(branchReads,before,'tab changes make no recommendation request');
 assert.equal(await page.getByRole('button',{name:'Verify phone to continue',exact:true}).isEnabled(),true);
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
 await page.evaluate(()=>window.scrollTo(0,0));await page.screenshot({path:'/tmp/reference-checkout.png',fullPage:true});
 releaseHistory();releaseRatings();releaseAvailability();releasePreview();
 assert.deepEqual(errors,[]);await context.close();console.log('Local Items picker, phone-first checkout and local meal tabs work with stalled pickup');
}}finally{await browser.close();}
