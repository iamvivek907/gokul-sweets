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
try{for(const [width,enabled,constrained] of [[390,true,false]]){
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

 await page.screenshot({path:'/tmp/reference-menu-debug.png',fullPage:true});
 await page.locator('.mobile-items-picker summary').click();const categories=page.getByRole('navigation',{name:'Item categories',exact:true});await categories.waitFor();
 await categories.getByRole('button',{name:/Drinks/}).click();assert.equal(await categories.isVisible(),false);
 await page.locator('.mobile-items-picker summary').click();await page.keyboard.press('Escape');assert.equal(await categories.isVisible(),false);
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
