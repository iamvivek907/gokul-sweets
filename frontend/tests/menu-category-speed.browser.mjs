import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const browser=await chromium.launch({headless:true}),base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
const today=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata'}).format(new Date());
const date=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata'}).format(new Date(Date.now()+86400000));
const branch={id:1,code:'GUIDE',name:'Gokul Tamkuhi Road',active:true,operational:true,pickupAvailable:true,address:'Main Road, opposite the bus stand',city:'Tamkuhi Road',pincode:'274407',coverImageUrl:'/logo.png'};
const sweet={id:1,categoryId:1,categoryName:'Sweets',name:'Fresh peda',description:'Fresh milk sweet.',price:400,imageUrl:null,available:true,saleMode:'WEIGHT',minimumWeightGrams:250,weightStepGrams:50};
const tea={...sweet,id:2,categoryId:2,categoryName:'Drinks',name:'Special tea',price:30,saleMode:'UNIT',minimumWeightGrams:null,weightStepGrams:null};
const sold={...tea,id:3,name:'Sold-out samosa',available:false};
const slot={id:7,branchId:1,slotDate:date,startTime:'15:00:00',endTime:'16:00:00',active:true,remainingCapacity:10,priorityEnabled:false,priorityRemainingCapacity:0,priorityCharge:0};
const offer={rebateId:1,code:'SAVE',name:'Sweet saving',description:'Eligible food only',scope:'GENERAL',rebateType:'SLAB',rebateAmount:0,payableAfterRebate:100,minimumOrderAmount:150,maximumDiscountAmount:20,nextSlabMinimumOrderAmount:150,nextSlabRebateAmount:10,amountNeededForNextSlab:25};
try{for(const [width,enabled,constrained] of [[320,true,false],[390,true,false],[640,true,false]]){
 const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:'block'}),page=await context.newPage();page.setDefaultTimeout(15000);
 let menuReads=0,pickupReads=0,releaseHistory,releaseRatings,releaseAvailability,releasePreview;
 const historyGate=new Promise(r=>releaseHistory=r),ratingsGate=new Promise(r=>releaseRatings=r),availabilityGate=new Promise(r=>releaseAvailability=r),previewGate=new Promise(r=>releasePreview=r);
 const errors=[];page.on('pageerror',e=>errors.push(e.message));const smart=true,signedIn=false;
 const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Methods':'GET,POST,OPTIONS','Access-Control-Allow-Headers':'content-type,idempotency-key'};
 await context.route('**/api/**',async route=>{const p=new URL(route.request().url()).pathname;let json=[];
  if(route.request().method()==='OPTIONS')return route.fulfill({status:204,headers});
  if(constrained)await new Promise(r=>setTimeout(r,150));
  if(p==='/api/storefront/features')json={futuristicStorefrontV2:enabled,checkoutExperienceV2:enabled&&smart,contextualStorefrontV2:true,simplifiedCheckout:true,acceptedCheckoutQuote:true,branchExperience:true,preHomeIntentGateway:true,smartAvailability:smart,pickupAddOns:true,gokulRewards:true,futureOrderingDays:30,today};
  else if(p==='/api/branches')json=[branch];else if(p==='/api/branches/1')json=branch;
  else if(p==='/api/menu'){menuReads++;json=Array.from({length:10},(_,i)=>({id:i+1,name:i===0?'Sweets':`Category ${i+1}`,products:Array.from({length:i===0?44:38},(_,j)=>({...tea,id:i*100+j+1,categoryId:i+1,categoryName:i===0?'Sweets':`Category ${i+1}`,name:`Item ${i+1}-${j+1}`}))}));}
  else if(p==='/api/menu/portion-groups')json={groups:[]};
  else if(p==='/api/reviews/product-summaries'){await ratingsGate;json=[{productId:1,averageRating:4.8,ratingCount:12}];}
  else if(p==='/api/storefront/customer-identity')json={enabled:true,guestCheckoutEnabled:false};
  else if(p==='/api/customer/identity/orders'){await historyGate;json=[{orderNumber:'PREVIOUS',branchId:1,orderStatus:'PICKED_UP'},{orderNumber:'UNPAID',branchId:1,orderStatus:'CONFIRMED'}];}
  else if(p==='/api/customer/identity/orders/PREVIOUS')json={branchId:1,orderStatus:'PICKED_UP',paymentStatus:'PAID',items:[{productId:4}]};
  else if(p==='/api/customer/identity/me')json={authenticated:signedIn,name:'Test customer',phone:'+919876543210'};
  else if(p==='/api/branches/1/availability'){pickupReads++;
   await availabilityGate;const body=route.request().postDataJSON();const valid=body.startDate===date;
   json={today,maximumDate:date,dates:[{date:body.startDate,available:valid,items:[{productId:1,available:true},{productId:2,available:true}],slots:valid?[{slot,normalAvailable:true,priorityAvailable:false,issues:[{productId:3,available:false}]}]:[]},...(body.days>1?[{date,available:true,slots:[{slot,normalAvailable:true,priorityAvailable:false,issues:[{productId:3,available:false}]}]}]:[])]};
  }
  else if(p==='/api/menu/pickup-addons'){json=[{product:tea,slotVerified:true,weightGrams:null,portionPrice:30,portionTotal:30,reason:'Often ordered with Fresh peda'},{product:{...sold,available:true,categoryName:"Sides"},slotVerified:true,weightGrams:null,portionPrice:30,portionTotal:30,reason:'A branch favourite'}];}
  else if(p==='/api/orders/mobile-preview'){await previewGate;json={quote:{token:'preview',expiresAt:new Date(Date.now()+600000).toISOString(),subtotal:100,taxAmount:0,totalAmount:100,convenienceFee:0,paymentFee:0},offers:[],spendTargets:[offer],totalBeforeOffer:100,rewards:{balance:27,pendingCoins:33,rewards:[{code:'SWEET_5',name:'₹5 sweet saving',coins:30,minimumSubtotal:149,eligible:false,unavailableReason:'Not enough coins'}],terms:'Fees are excluded.'}};}
  try{return await route.fulfill({json,headers});}catch{/* Navigation may abort a deliberately delayed request. */}
 });
 await context.addInitScript(({branch,sweet,slot,date})=>{if(!localStorage.getItem('gokul-cart')){localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));localStorage.setItem('gokul-cart',JSON.stringify({branchId:1,items:[{product:sweet,quantity:0,weightGrams:250}]}));localStorage.setItem('gokul-selected-pickup-slot',JSON.stringify({date,slot,pickupType:'NORMAL'}));localStorage.setItem('gokul-pickup-intent',JSON.stringify({branchId:1,date}));}}, {branch,sweet,slot,date});
 if(constrained)await context.addInitScript(()=>Object.defineProperty(navigator,'connection',{configurable:true,value:{effectiveType:'3g',saveData:false}}));
 await page.goto(`${base}/menu`);await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});

 await page.emulateMedia({reducedMotion:'reduce'});
 const button=page.getByRole('button',{name:'Browse Category 10',exact:true});
 await page.locator('#menu-category-10').waitFor();
 assert.equal(await page.locator('.mobile-menu-filters,.mobile-filter-chips').count(),0,'no duplicate filter rows');
 const before={menu:menuReads,pickup:pickupReads};const timings=[];
 for(let i=0;i<12;i++){
  const started=Date.now();await button.click();
  await page.waitForFunction(()=>{const n=document.getElementById('menu-category-10');const tools=document.querySelector('.customer-site-header');const top=n.getBoundingClientRect().top,bottom=tools.getBoundingClientRect().bottom;return top>=bottom-1&&top<=bottom+24&&document.body.style.position!=='fixed';});
  assert.equal(await page.locator('#menu-category-10 .gokul-menu-category-heading h3').evaluate(n=>{const r=n.getBoundingClientRect();return n.contains(document.elementFromPoint(r.x+r.width/2,r.y+r.height/2));}),true,'category heading remains visible below the compact shared header');
  timings.push(Date.now()-started);assert.equal(await page.locator('.gokul-menu-category-section').count(),1);
 }
 assert.equal(menuReads,before.menu,'collection switches never refetch the catalog');
 assert.equal(pickupReads,before.pickup,'collection switches never restart pending pickup checks');
 assert.deepEqual(errors,[],'large-menu repeated taps produce no page errors');
 await page.getByLabel('Find a favourite',{exact:true}).fill('Item 1-1');
 await button.click();
 await page.waitForFunction(()=>{const top=document.getElementById('menu-category-10')?.getBoundingClientRect().top,bottom=document.querySelector('.customer-site-header').getBoundingClientRect().bottom;return top>=bottom-1&&top<=bottom+24;});
 assert.equal(await page.getByLabel('Find a favourite',{exact:true}).inputValue(),'','jump clears a search that would hide the category');
 await page.getByRole('button',{name:'All',exact:true}).click();await page.locator('#gokul-menu-items').scrollIntoViewIfNeeded();
 releaseHistory();releaseRatings();releaseAvailability();releasePreview();await context.close();console.log(`Measured category jumps and repeated filters passed at ${width}px`);
}}finally{await browser.close();}
