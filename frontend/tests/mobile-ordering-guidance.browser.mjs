import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {mkdir} from 'node:fs/promises';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
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
const offer={rebateId:1,code:'SAVE',name:'Sweet saving',description:'Eligible food only',scope:'GENERAL',rebateType:'SLAB',rebateAmount:0,payableAfterRebate:100,minimumOrderAmount:150,maximumDiscountAmount:20,nextSlabMinimumOrderAmount:150,nextSlabRebateAmount:10,amountNeededForNextSlab:50};
try{for(const [width,enabled] of [[320,true],[390,true],[640,true],[641,true],[390,false]]){
 const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:'block'}),page=await context.newPage();page.setDefaultTimeout(15000);
 let previewCalls=0,addonChecks=0,confirmationFails=true,cartConflict=true,availabilityCalls=0;const errors=[];page.on('pageerror',e=>errors.push(e.message));
 const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Methods':'GET,POST,OPTIONS','Access-Control-Allow-Headers':'content-type,idempotency-key'};
 await context.route('**/api/**',async route=>{const p=new URL(route.request().url()).pathname;let json=[];
  if(route.request().method()==='OPTIONS')return route.fulfill({status:204,headers});
  if(p==='/api/storefront/features')json={futuristicStorefrontV2:enabled,checkoutExperienceV2:enabled,contextualStorefrontV2:true,simplifiedCheckout:true,acceptedCheckoutQuote:true,branchExperience:true,preHomeIntentGateway:true,smartAvailability:true,pickupAddOns:true,gokulRewards:true,futureOrderingDays:30,today};
  else if(p==='/api/branches')json=[branch];else if(p==='/api/branches/1')json=branch;
  else if(p==='/api/menu')json=[{id:1,name:'Sweets',products:[sweet]},{id:2,name:'Drinks',products:[tea,sold,favourite]}];
  else if(p==='/api/menu/portion-groups')json={groups:[]};
  else if(p==='/api/reviews/product-summaries')json=[{productId:1,averageRating:4.8,ratingCount:12}];
  else if(p==='/api/storefront/customer-identity')json={enabled:true,guestCheckoutEnabled:false};
  else if(p==='/api/customer/identity/orders')json=[{orderNumber:'PREVIOUS',branchId:1,orderStatus:'PICKED_UP'},{orderNumber:'UNPAID',branchId:1,orderStatus:'CONFIRMED'}];
  else if(p==='/api/customer/identity/orders/PREVIOUS')json={branchId:1,orderStatus:'PICKED_UP',paymentStatus:'PAID',items:[{productId:4}]};
  else if(p==='/api/customer/identity/me')json={authenticated:true,name:'Test customer',phone:'+919876543210'};
  else if(p==='/api/branches/1/pickup-discovery'){
   json={today,maximumDate:date,dates:[{date:today,available:false,items:[],slots:[]},{date,available:true,items:[],slots:[{slot,normalAvailable:true,priorityAvailable:false,issues:[]}]}]};
  }
  else if(p==='/api/branches/1/availability'){
   availabilityCalls++;const body=route.request().postDataJSON();const valid=body.startDate===date;
   json={today,maximumDate:date,dates:[{date:body.startDate,available:valid,items:[{productId:1,available:true},{productId:2,available:true}],slots:valid?[{slot,normalAvailable:true,priorityAvailable:false,issues:[{productId:3,available:false}]}]:[]},...(body.days>1?[{date,available:true,slots:[{slot,normalAvailable:true,priorityAvailable:false,issues:[{productId:3,available:false}]}]}]:[])]};
   if(body.days===1&&body.startDate===date&&confirmationFails&&page.url().includes('/menu')&&await page.getByRole('dialog').count())json.dates[0].slots=[];
   if(body.startDate===date&&cartConflict&&body.items.some(item=>item.productId===1&&item.weightGrams===750))json.dates[0].slots=[{slot,normalAvailable:false,priorityAvailable:false,issues:[{productId:1,productName:sweet.name,available:false,code:'QUANTITY_TOO_LARGE',reason:'Only 500 g remain.'}]}];
  }
  else if(p==='/api/menu/pickup-addons')json=[{product:tea,weightGrams:null,portionPrice:30,portionTotal:30,reason:'Often ordered with Fresh peda'},{product:sold,weightGrams:null,portionPrice:30,portionTotal:30,reason:'A branch favourite'}];
  else if(p==='/api/menu/pickup-addons/check'){addonChecks++;json={orderable:true};}
  else if(p==='/api/orders/mobile-preview'){previewCalls++;json={quote:{token:'preview',expiresAt:new Date(Date.now()+600000).toISOString(),subtotal:100,taxAmount:0,totalAmount:100,convenienceFee:0,paymentFee:0},offers:[],spendTargets:[offer],totalBeforeOffer:100,rewards:{balance:27,pendingCoins:33,rewards:[{code:'SWEET_5',name:'₹5 sweet saving',coins:30,minimumSubtotal:149,eligible:false,unavailableReason:'Not enough coins'}],terms:'Fees are excluded.'}};}
  return route.fulfill({json,headers});
 });
 await context.addInitScript(({branch,sweet,today})=>{localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));localStorage.setItem('gokul-cart',JSON.stringify({branchId:1,items:[{product:sweet,quantity:0,weightGrams:250}]}));localStorage.setItem('gokul-selected-pickup-slot',JSON.stringify({date:today,slot:{id:1,branchId:1,slotDate:today,startTime:'00:01:00',endTime:'00:02:00',active:true,remainingCapacity:10},pickupType:'NORMAL'}));},{branch,sweet,today});
 await page.goto(`${base}/menu`);await page.locator('.gokul-product-card').first().waitFor();await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});
 const compact=width<=640&&enabled;
 assert.equal(await page.locator('.mobile-menu-pickup').count(),compact?1:0);
 if(compact){
  const tabs=page.getByRole('navigation',{name:'Branch pages'});assert.equal(await tabs.isVisible(),false,'phone menu removes duplicate ribbon');await page.setViewportSize({width:1280,height:900});await tabs.getByRole('button',{name:'Branch details',exact:true}).click();
  const details=page.getByRole('region',{name:`${branch.name} details`,exact:true});await details.waitFor();await details.getByRole('heading',{name:'Address',exact:true}).waitFor();
  await page.setViewportSize({width:1280,height:900});await details.waitFor();await page.setViewportSize({width,height:900});await details.waitFor();
  assert.equal(await tabs.getByRole('button',{name:'Menu',exact:true}).isVisible(),true,'return to menu survives viewport changes');await tabs.getByRole('button',{name:'Menu',exact:true}).click();await page.locator('.gokul-product-card').first().waitFor();
  await page.getByText('Your previous pickup has passed. Choose a new time; your cart is saved.',{exact:true}).waitFor();
  assert.match(await page.locator('.mobile-selected-weight-price').innerText(),/250 g.*100/);
  await page.getByRole('button',{name:'Choose time',exact:true}).click();const dialog=page.getByRole('dialog');await dialog.getByRole('button',{name:date,exact:true}).click();await dialog.getByRole('button',{name:'15:00–16:00 Standard',exact:true}).click();
  const contrast=await dialog.getByRole('button',{name:'15:00–16:00 Standard',exact:true}).evaluate(node=>{const rgb=value=>value.match(/\d+/g).slice(0,3).map(Number).map(v=>{const s=v/255;return s<=.04045?s/12.92:((s+.055)/1.055)**2.4;});const luminance=value=>{const [r,g,b]=rgb(value);return .2126*r+.7152*g+.0722*b;};const bg=luminance(getComputedStyle(node).backgroundColor);return [...node.children].map(child=>{const fg=luminance(getComputedStyle(child).color);return (Math.max(bg,fg)+.05)/(Math.min(bg,fg)+.05);});});assert.ok(contrast.every(value=>value>=4.5),'selected pickup primary and secondary labels have readable contrast');
  await dialog.getByRole('button',{name:'Use this pickup',exact:true}).click();await dialog.getByRole('alert').waitFor();assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-selected-pickup-slot')).slot.id),1);
  await dialog.getByRole('button',{name:'Close pickup selector'}).click();confirmationFails=false;
  await page.evaluate(()=>{const cart=JSON.parse(localStorage.getItem('gokul-cart'));cart.items[0].weightGrams=750;localStorage.setItem('gokul-cart',JSON.stringify(cart));window.dispatchEvent(new Event('storage'));});
  const savedCart=await page.evaluate(()=>localStorage.getItem('gokul-cart'));
  await page.getByRole('button',{name:'Choose time',exact:true}).click();await dialog.getByRole('button',{name:date,exact:true}).click();await dialog.getByRole('button',{name:'15:00–16:00 Standard',exact:true}).click();await dialog.getByRole('button',{name:'Use this pickup',exact:true}).click();
  await dialog.getByRole('alert').filter({hasText:'Only 500 g remain'}).waitFor();assert.equal(await page.evaluate(()=>localStorage.getItem('gokul-cart')),savedCart);assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-selected-pickup-slot')).slot.id),1);
  cartConflict=false;await dialog.getByRole('button',{name:'Use this pickup',exact:true}).click();await dialog.waitFor({state:'hidden'});
  const pairing=page.getByRole('region',{name:'Pairs well with your selection'});await pairing.waitFor();await pairing.locator('.mobile-menu-pairings-inline').getByRole('button',{name:'Add Special tea from pairings',exact:true}).waitFor();assert.equal(await pairing.getByText('Choose pickup to see optional additions.',{exact:true}).count(),0);await openPairings(page);await pairing.getByText('Often ordered with Fresh peda',{exact:true}).waitFor();assert.equal(await pairing.getByText('Sold-out samosa',{exact:true}).count(),0);await closePairings(page);await page.locator('#menu-offers-open').click();await page.getByRole('dialog',{name:'Offers & savings',exact:true}).getByText(/Unlock/).waitFor();await page.keyboard.press('Escape');await openPairings(page);await pairing.getByText('Your completed-order favourite',{exact:true}).waitFor();
  if(process.env.SCREENSHOT_DIR){await mkdir(process.env.SCREENSHOT_DIR,{recursive:true});await closePairings(page);await page.screenshot({path:`${process.env.SCREENSHOT_DIR}/menu-guidance-${width}.png`,fullPage:true});const favourites=page.getByRole('region',{name:'Recommended menu items',exact:true});await favourites.getByRole('button',{name:'Add Fresh peda from recommendations',exact:true}).waitFor();assert.equal(await page.getByRole('button',{name:'View customer favourites',exact:true}).count(),0);assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);await favourites.scrollIntoViewIfNeeded();await page.screenshot({path:`${process.env.SCREENSHOT_DIR}/favourites-inline-${width}.png`});await openPairings(page);await page.screenshot({path:`${process.env.SCREENSHOT_DIR}/suggestion-sheet-${width}.png`});}
  await pairing.getByRole('button',{name:'Add Special tea',exact:true}).click();await page.waitForFunction(()=>JSON.parse(localStorage.getItem('gokul-cart')).items.length===2);assert.equal(addonChecks,1);
  await closePairings(page);await page.locator('.gokul-floating-cart a').click();await page.waitForURL('**/checkout/mobile');await page.getByRole('heading',{name:'Your earned coins',exact:true}).waitFor();
  const pay=page.getByRole('button',{name:'Pay now',exact:true});assert.equal(await pay.evaluate(n=>getComputedStyle(n).backgroundColor),'rgb(143, 24, 56)','Pay uses the requested maroon');
  const payContrast=await pay.evaluate(n=>{const lum=s=>{const [r,g,b]=s.match(/\d+/g).slice(0,3).map(Number).map(v=>{const x=v/255;return x<=.04045?x/12.92:((x+.055)/1.055)**2.4;});return .2126*r+.7152*g+.0722*b;};const style=getComputedStyle(n),a=lum(style.color),b=lum(style.backgroundColor);return (Math.max(a,b)+.05)/(Math.min(a,b)+.05);});assert.ok(payContrast>=4.5,'maroon action retains normal text contrast');assert.ok(Number.parseFloat(await pay.evaluate(n=>getComputedStyle(n).fontSize))>=16,'maroon action uses readable compact text');assert.equal(await page.locator('.checkout-reward-options').getAttribute('open'),null);assert.ok((await page.locator('.checkout-rewards-compact').boundingBox()).height<230);
  await page.getByRole('button',{name:'Add offer code',exact:true}).click();const offers=page.getByRole('dialog',{name:'Choose an offer'});const box=await offers.boundingBox();assert.ok(Math.abs(box.y+box.height-900)<3,'offers rest at bottom');if(process.env.SCREENSHOT_DIR)await page.screenshot({path:`${process.env.SCREENSHOT_DIR}/offers-guidance-${width}.png`});await page.keyboard.press('Escape');
  await page.getByRole('button',{name:/Price details/}).click();await page.getByRole('dialog',{name:'Bill summary'}).getByText('Total to pay',{exact:true}).waitFor();await page.keyboard.press('Escape');
  if(process.env.SCREENSHOT_DIR){await mkdir(process.env.SCREENSHOT_DIR,{recursive:true});await page.screenshot({path:`${process.env.SCREENSHOT_DIR}/checkout-guidance-${width}.png`,fullPage:true});}
  await page.goto(`${base}/`);await page.locator('#gokul-branches article').first().waitFor();const card=page.locator('.arrival-pickup-copy').first();await card.getByText('Main Road, opposite the bus stand, Tamkuhi Road, 274407',{exact:true}).waitFor();assert.equal(await card.evaluate(e=>getComputedStyle(e).position),'relative');
 }
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
 assert.deepEqual(errors,[]);if(process.env.SCREENSHOT_DIR){await mkdir(process.env.SCREENSHOT_DIR,{recursive:true});await page.locator(".gokul-mobile-launch").waitFor({state:"hidden"});await page.screenshot({path:`${process.env.SCREENSHOT_DIR}/guidance-${width}-${enabled}.png`,fullPage:true});}
 await context.close();console.log(`Mobile guidance: ${width} enabled=${enabled} passed; ${previewCalls} previews, ${availabilityCalls} availability checks`);
}}finally{await browser.close();}
