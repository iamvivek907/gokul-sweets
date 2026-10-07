import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {mkdir} from 'node:fs/promises';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const browser=await chromium.launch({headless:true}),base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
const today=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata'}).format(new Date());
const date=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata'}).format(new Date(Date.now()+86400000));
const branch={id:1,name:'Gokul Test branch',code:'TEST',address:'Main Road',active:true,operational:true,pickupAvailable:true};
const products=Array.from({length:5},(_,i)=>({id:i+1,name:`Fresh meal ${i+1}`,categoryId:1,categoryName:'Meals',price:100,available:true,saleMode:'UNIT',description:'Prepared fresh for pickup.',imageUrl:'/logo.png'}));
const slot={id:1,branchId:1,slotDate:date,startTime:'18:00:00',endTime:'19:00:00',remainingCapacity:20,active:true,priorityEnabled:false};
const offer={rebateId:1,name:'Meal saving',code:'SAVE10',description:'Eligible food subtotal',scope:'GENERAL',rebateType:'SLAB',rebateAmount:10,payableAfterRebate:90,minimumOrderAmount:100,maximumDiscountAmount:30,nextSlabMinimumOrderAmount:null,nextSlabRebateAmount:null,amountNeededForNextSlab:null};
const target={...offer,rebateId:2,name:'Bigger saving',code:'SAVE30',rebateAmount:10,nextSlabMinimumOrderAmount:300,nextSlabRebateAmount:30,amountNeededForNextSlab:200};
try{for(const [width,mode] of [[320,'offer'],[390,'offer'],[640,'offer'],[390,'none'],[390,'failure'],[390,'slow'],[390,'reduced'],[390,'stale'],[390,'dismiss'],[390,'empty']]){
 const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:'block',reducedMotion:mode==='reduced'?'reduce':'no-preference'}),page=await context.newPage();page.setDefaultTimeout(15000);const errors=[];page.on('pageerror',e=>errors.push(e.message));let started=false,checkoutReads=0;let release;const gate=new Promise(r=>release=r);
 const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Methods':'GET,POST,PUT,OPTIONS','Access-Control-Allow-Headers':'content-type,idempotency-key'};
 await context.route('**/api/**',async route=>{const path=new URL(route.request().url()).pathname;let json=[];
  if(route.request().method()==='OPTIONS')return route.fulfill({status:204,headers});
  if(path==='/api/storefront/features')json={futuristicStorefrontV2:true,checkoutExperienceV2:true,contextualStorefrontV2:true,simplifiedCheckout:true,acceptedCheckoutQuote:true,pickupAddOns:true,smartAvailability:true,reviews:true,today,futureOrderingDays:30};
  else if(path==='/api/branches')json=[branch];else if(path==='/api/branches/1')json=branch;
  else if(path==='/api/menu')json=[{id:1,name:'Meals',products}];else if(path==='/api/menu/offers')json=[{rebateId:3,code:'PUBLIC20',name:'Public menu offer',description:'A standard pickup promotion',rebateType:'FIXED_AMOUNT',rebateValue:20,minimumOrderAmount:200,maximumDiscountAmount:null,tiers:[]}];else if(path==='/api/menu/portion-groups')json={groups:[]};
  else if(path==='/api/reviews/product-summaries')json=[{productId:2,averageRating:4.8,ratingCount:12}];
  else if(path==='/api/storefront/customer-identity')json={enabled:true,guestCheckoutEnabled:false};
  else if(path==='/api/customer/identity/me')json={authenticated:true,name:'Test customer',phone:'+919876543210'};
  else if(path==='/api/branches/1/availability')json={today,maximumDate:date,dates:[{date,available:true,items:products.map(p=>({productId:p.id,available:true})),slots:[{slot,normalAvailable:true,priorityAvailable:false,issues:[]}]}]};
  else if(path==='/api/menu/pickup-addons')json=[];
  else if(path==='/api/orders/mobile-preview'){
   if(page.url().includes('/checkout/mobile')){started=true;checkoutReads++;await gate;if(mode==='failure')return route.fulfill({status:503,json:{message:'Unavailable'},headers}).catch(()=>{});}
   json={quote:{token:'preview',expiresAt:new Date(Date.now()+600000).toISOString(),subtotal:100,taxAmount:0,totalAmount:100,convenienceFee:0,paymentFee:0},offers:mode==='none'?[]:[offer],selectedOffer:mode==='none'?null:offer,spendTargets:[target],totalBeforeOffer:100};
  }
  await route.fulfill({json,headers}).catch(()=>{});
 });
 await context.addInitScript(({branch,slot,date,product})=>{localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));localStorage.setItem('gokul-selected-pickup-slot',JSON.stringify({date,slot,pickupType:'NORMAL'}));localStorage.setItem('gokul-cart',JSON.stringify({branchId:1,items:[{product,quantity:1}]}));},{branch,slot,date,product:products[0]});
 if(mode==='empty')await context.addInitScript(()=>localStorage.setItem('gokul-cart',JSON.stringify({branchId:1,items:[]})));
 await page.goto(`${base}/menu`);await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});if(mode==='empty'){await page.locator('#menu-offers-open').getByText('₹20.00 off above ₹200.00',{exact:true}).waitFor();await page.locator('#menu-offers-open').click();await page.getByRole('dialog',{name:'Offers & savings',exact:true}).getByText('Public menu offer',{exact:true}).waitFor();assert.equal(await page.locator('.gokul-floating-cart').count(),0);assert.equal(checkoutReads,0);await context.close();console.log('Public offers visible before adding items');continue;}await page.locator('#menu-offers-open').getByText(mode==='none'?/off above/:/Save ₹10/).waitFor();
 assert.equal(await page.getByRole('navigation',{name:'Branch pages'}).isVisible(),false);assert.equal(await page.getByRole('button',{name:'View customer favourites',exact:true}).count(),0);
 const firstProduct=page.locator('.gokul-menu-product-card').first();await firstProduct.waitFor();
 assert.ok((await firstProduct.boundingBox()).y<750,'first catalogue product appears in the opening viewport');assert.ok((await firstProduct.boundingBox()).y>(await page.locator('.gokul-menu-tools').boundingBox()).y);
 await page.getByLabel('Find a favourite',{exact:true}).fill('Fresh meal 3');await page.locator('#gokul-product-3').waitFor();assert.equal(await page.locator('#gokul-product-2').count(),0);await page.getByLabel('Find a favourite',{exact:true}).fill('');
 await page.locator('.mobile-cart-offer-target').click();const offers=page.getByRole('dialog',{name:'Offers & savings',exact:true});await offers.getByText('Bigger saving',{exact:true}).waitFor();await offers.locator('.menu-offer-card').getByText('Add ₹200.00 in eligible items',{exact:true}).waitFor();assert.equal(await offers.getByText('Meal saving',{exact:true}).count(),mode==='none'?0:1);await page.keyboard.press('Escape');
 if(process.env.SCREENSHOT_DIR){await mkdir(process.env.SCREENSHOT_DIR,{recursive:true});await page.evaluate(()=>scrollTo({top:0,behavior:'instant'}));await page.screenshot({path:`${process.env.SCREENSHOT_DIR}/menu-discovery-${width}-${mode}.png`});}
 if(mode==='stale')await page.evaluate(()=>sessionStorage.setItem('gokul-offer-arrival',JSON.stringify({context:'outdated basket',at:Date.now()})));
 if(mode==='stale')await page.goto(`${base}/checkout/mobile`);else await page.locator('.gokul-floating-cart>a').click();await page.waitForURL('**/checkout/mobile');
 await page.getByRole('heading',{name:'Your order',exact:true}).waitFor();
 const cartSection=page.getByRole('region',{name:'Cart items',exact:true});await cartSection.waitFor();
 await page.getByRole('heading',{name:'Phone verified',exact:true}).waitFor();
 await page.locator('.mobile-phone-entry[data-verified="true"]').waitFor();
 await page.waitForFunction(()=>{const card=document.querySelector('.mobile-phone-entry[data-verified="true"]');return card&&card.getBoundingClientRect().height<125;});
 const cartTop=await cartSection.evaluate(n=>n.getBoundingClientRect().top+window.scrollY);
 for(let i=0;i<50&&!started;i++)await page.waitForTimeout(100);assert.equal(started,true,'checkout preview loads without an entry modal');assert.ok(checkoutReads>0);
 assert.equal(await page.locator('dialog.offer-arrival').isVisible(),false,'checking savings never covers checkout');assert.equal(await page.locator('.offer-confetti').count(),0);
 if(mode==='slow')await page.waitForTimeout(5500);
 release();
 if(mode==='failure')await page.getByText('We couldn’t confirm your price or offers. Review pickup and verification, then try again.',{exact:true}).waitFor();
 else if(mode==='none')await page.getByText('Your current menu price',{exact:true}).waitFor();
 else await page.locator('.mobile-checkout-savings').getByText(/₹10.00/).waitFor();
 const arrival=page.locator('dialog.offer-arrival');
 if(mode==='none'||mode==='failure'||mode==='stale')assert.equal(await arrival.isVisible(),false,'no unverified or stale savings celebration');
 else{
  await arrival.waitFor({state:'visible'});await arrival.getByRole('heading',{name:'SAVE10 applied',exact:true}).waitFor();await arrival.getByText('You saved ₹10.00',{exact:true}).waitFor();
  assert.equal(await arrival.locator('.offer-confetti i').count(),24);
  if(mode==='reduced')assert.equal(await arrival.locator('.offer-confetti').evaluate(n=>getComputedStyle(n).display),'none');
  await arrival.getByRole('button',{name:'Woohoo! Thanks',exact:true}).click();await arrival.waitFor({state:'hidden'});
 }

 const finalCartTop=await cartSection.evaluate(n=>n.getBoundingClientRect().top+window.scrollY);
 assert.ok(Math.abs(finalCartTop-cartTop)<=1,`savings discovery does not move cart items after verified identity settles (${mode}: ${cartTop} to ${finalCartTop})`);
 assert.equal(await page.locator('.mobile-checkout-pay button').evaluate(n=>getComputedStyle(n).backgroundColor),'rgb(143, 24, 56)');
 if(process.env.SCREENSHOT_DIR)await page.screenshot({path:`${process.env.SCREENSHOT_DIR}/checkout-inline-${width}-${mode}.png`});
 assert.equal(await page.evaluate(()=>sessionStorage.getItem('gokul-offer-arrival')),null,'legacy arrival markers are cleared');assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);assert.deepEqual(errors,[]);await context.close();console.log(`Menu discovery ${width}px ${mode} passed`);
}}finally{await browser.close();}
