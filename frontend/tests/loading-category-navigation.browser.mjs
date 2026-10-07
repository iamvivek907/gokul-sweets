import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const base=process.env.BROWSER_BASE??'http://127.0.0.1:3311',date=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata'}).format(new Date(Date.now()+86400000));
const branch={id:1,name:'Main branch',active:true,operational:true,pickupAvailable:true};
const product=(id,name,categoryId,categoryName,price)=>({id,name,categoryId,categoryName,price,available:true,saleMode:'UNIT',description:'Prepared for your pickup.',imageUrl:'/arrival-mithai.webp'});
const sweets=[product(1,'Rasgulla',1,'Sweets',15),{...product(2,'Gulab Jamun',1,'Sweets',500),saleMode:'WEIGHT',minimumWeightGrams:250,weightStepGrams:50}],dairy=[product(3,'Dahi 200 g',2,'Dairy',25),product(4,'Dahi 400 g',2,'Dairy',50),product(5,'Dahi 1 kg',2,'Dairy',120),product(6,'Dahi 5 kg',2,'Dairy',550)],snacks=[product(7,'Butter biscuits',3,'Snacks',40),product(8,'Namkeen',3,'Snacks',60)],products=[...sweets,...dairy,...snacks];
const groups=[{key:'dahi',title:'Dahi',choices:dairy.map(p=>({productId:p.id,label:p.name.replace('Dahi ','')+' pack'}))}],slot={id:1,branchId:1,slotDate:date,startTime:'18:00:00',endTime:'19:00:00',active:true,remainingCapacity:20,priorityEnabled:false};
const browser=await chromium.launch({headless:true});
try{for(const [width,themed] of [[320,true],[390,true],[640,true],[1280,true],[390,false]]){const outcome="populated";
 const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:'block',reducedMotion:width===640?'reduce':'no-preference'}),page=await context.newPage();page.setDefaultTimeout(15000);let holdBranch=true,holdMenu=true,releaseBranch,releaseMenu;let blockRasgulla=false,delay=false,releaseOffers,releasePairings;let pairingOutcome=outcome;const offersGate=new Promise(resolve=>releaseOffers=resolve),pairingsGate=new Promise(resolve=>releasePairings=resolve);const errors=[];page.on('pageerror',e=>errors.push(e.message));
 const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Methods':'GET,POST,OPTIONS','Access-Control-Allow-Headers':'content-type'};
 await context.route('**/api/**',async route=>{const path=new URL(route.request().url()).pathname;let json=[];
  if(route.request().method()==='OPTIONS')return route.fulfill({status:204,headers});
  if(path==='/api/storefront/features')json={futuristicStorefrontV2:themed,checkoutExperienceV2:themed,contextualStorefrontV2:true,simplifiedCheckout:true,acceptedCheckoutQuote:true,branchExperience:true,smartAvailability:true,pickupAddOns:true,reviews:true,notificationInbox:true,today:date,futureOrderingDays:30};
  else if(path==='/api/branches')json=[branch];else if(path==='/api/branches/1'){if(holdBranch)await new Promise(resolve=>releaseBranch=resolve);json=branch;}
  else if(path==='/api/menu'){if(holdMenu)await new Promise(resolve=>releaseMenu=resolve);json=[{id:1,name:'Sweets',products:sweets},{id:2,name:'Dairy',products:dairy},{id:3,name:'Snacks',products:snacks}];}
  else if(path==='/api/menu/offers'){await offersGate;if(outcome==='failed')return route.fulfill({status:503,json:{message:'Unavailable'},headers});json=outcome==='empty'?[]:[{rebateId:3,code:'PUBLIC20',name:'Public offer',description:'Standard pickup',rebateType:'FIXED_AMOUNT',rebateValue:20,minimumOrderAmount:200,maximumDiscountAmount:null,tiers:[]}];}else if(path==='/api/menu/portion-groups')json={version:1,groups};
  else if(path==='/api/storefront/customer-identity')json={enabled:true};else if(path==='/api/customer/identity/me')json={authenticated:true,name:'Vivek',phone:'+919876543210'};
  else if(path.endsWith('/availability')){if(delay)await new Promise(r=>setTimeout(r,1500));json={today:date,maximumDate:date,dates:[{date,available:true,items:products.map(p=>({productId:p.id,available:p.id!==6&&!(p.id===1&&blockRasgulla),code:p.id===6?'NO_INVENTORY':p.id===1&&blockRasgulla?'QUANTITY_TOO_LARGE':null})),slots:[{slot,normalAvailable:true,priorityAvailable:false,issues:[]}]}]};}
  else if(path==='/api/menu/pickup-addons'){await pairingsGate;if(pairingOutcome==='failed')return route.fulfill({status:503,json:{message:'Unavailable'},headers});json=pairingOutcome==='empty'?[]:[snacks[0],dairy[1],dairy[0],sweets[1]].map(product=>({product,weightGrams:product.saleMode==='WEIGHT'?250:null,portionPrice:product.saleMode==='WEIGHT'?125:product.price,portionTotal:product.saleMode==='WEIGHT'?125:product.price,reason:'Something extra',slotVerified:true,includesTax:true}));}else if(path==='/api/menu/pickup-addons/check')json={orderable:true};
  await route.fulfill({json,headers}).catch(()=>{});
 });
 await context.addInitScript(({branch,date,slot})=>{localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));localStorage.setItem('gokul-pickup-intent',JSON.stringify({branchId:1,date}));localStorage.setItem('gokul-selected-pickup-slot',JSON.stringify({date,slot,pickupType:'NORMAL'}));},{branch,date,slot});

 releaseOffers();releasePairings();
 await page.goto(`${base}/menu`);
 if(width<=640){
  await page.locator('.gokul-mobile-launch[open]').waitFor();
  const logo=page.locator('.gokul-mobile-launch .brand-loading-wordmark');
  assert.equal(await logo.evaluate(n=>getComputedStyle(n).color),'rgb(152, 12, 49)');
  if(width===640)assert.equal(await logo.locator('strong').evaluate(n=>getComputedStyle(n).animationName),'none');
 }
 await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});
 await page.locator('.customer-brand-loading .brand-loading-wordmark').waitFor();
 assert.equal(await page.locator('.customer-brand-loading .brand-loading-wordmark').evaluate(n=>getComputedStyle(n).color),'rgb(152, 12, 49)');
 holdBranch=false;releaseBranch();
 await page.locator('.customer-menu-loading').waitFor();
 if(width===640)assert.equal(await page.locator('.customer-brand-loading-spinner>span').evaluate(n=>getComputedStyle(n).animationName),'none');
 holdMenu=false;releaseMenu();await page.locator('#gokul-product-1').waitFor();
 const floating=page.getByRole('button',{name:'Categories',exact:true});
 if(width<=640&&themed){
  await floating.waitFor();
  await page.locator('#menu-offers-open').click();const offers=page.getByRole('dialog',{name:'Offers & savings',exact:true});
  await offers.getByText('Public offer',{exact:true}).waitFor();assert.equal(await offers.locator('.menu-offer-name svg').count(),1);
  await offers.getByRole('button',{name:'Close ×',exact:true}).click();
  await page.locator('#gokul-product-1').getByRole('button',{name:'Add Rasgulla to cart',exact:true}).click();await page.locator('.gokul-floating-cart').waitFor();
  await page.waitForFunction(()=>{const f=document.querySelector('.menu-floating-category-trigger').getBoundingClientRect(),c=document.querySelector('.gokul-floating-cart').getBoundingClientRect();return f.bottom<=c.top-4;});
  assert.equal(await page.locator('.gokul-floating-cart>a').evaluate(n=>getComputedStyle(n).backgroundColor),'rgb(8, 127, 85)');
  const cart=await page.evaluate(()=>localStorage.getItem('gokul-cart'));
  await page.evaluate(()=>window.scrollTo({top:document.documentElement.scrollHeight,behavior:'instant'}));
  await floating.click();let sheet=page.getByRole('dialog',{name:'Jump to a category',exact:true});await sheet.waitFor();
  assert.equal(await sheet.evaluate(n=>getComputedStyle(n).backgroundColor),'rgb(8, 11, 16)');
  assert.equal(await sheet.locator('header').evaluate(n=>getComputedStyle(n).backgroundColor),'rgb(8, 11, 16)');
  assert.ok((await sheet.boundingBox()).width<width,'category panel has side margins');
  if(process.env.SCREENSHOT_DIR)await page.screenshot({path:`${process.env.SCREENSHOT_DIR}/category-panel-${width}.png`});
  await page.keyboard.press('Escape');await sheet.waitFor({state:'hidden'});assert.equal(await floating.evaluate(n=>n===document.activeElement),true);
  const y=await page.evaluate(()=>scrollY);await floating.click();
  await sheet.getByRole('button',{name:/Dairy/}).click();await page.locator('#gokul-product-3').waitFor();
  assert.equal(await page.locator('#gokul-product-1').count(),0);assert.equal(await page.locator('#gokul-product-7').count(),0);
  await page.waitForFunction(()=>{const n=document.querySelector('#menu-category-2');return n&&n.getBoundingClientRect().top>=52&&n.getBoundingClientRect().top<innerHeight/2;});
  assert.equal(await page.evaluate(()=>localStorage.getItem('gokul-cart')),cart);
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
  if(process.env.SCREENSHOT_DIR)await page.screenshot({path:`${process.env.SCREENSHOT_DIR}/floating-categories-${width}.png`});
  await floating.click();await sheet.getByRole('button',{name:/^All items\s*\d+$/}).click();await page.locator('#gokul-product-7').waitFor();
  await page.locator('#gokul-menu-search').fill('No such item');await page.getByText('No matching items',{exact:true}).waitFor();
  await floating.click();await sheet.getByRole('button',{name:/Sweets/}).click();await page.locator('#gokul-product-1').waitFor();assert.equal(await page.locator('#gokul-menu-search').inputValue(),'');
  await page.locator('.customer-bottom-navigation a[data-nav-icon=home]').click();await page.waitForURL('**/branches/1');await page.locator('.branch-overview').waitFor();assert.equal(await page.locator('.gokul-mobile-launch').isVisible(),false,'route changes do not replay launch');
  console.log(`Loading/category ${width}px: slow loads, offer icons, cart separation, Escape/focus, exact category, All, search recovery and navigation passed (${y}px scroll)`);
 }else if(themed){
  await page.locator('#gokul-product-1').getByRole('button',{name:'Add Rasgulla to cart',exact:true}).click();await page.locator('.gokul-floating-cart').waitFor();
  await page.waitForFunction(()=>{const f=document.querySelector('.menu-floating-category-trigger').getBoundingClientRect(),c=document.querySelector('.gokul-floating-cart').getBoundingClientRect();return f.bottom<=c.top-4;});
  const cart=await page.evaluate(()=>localStorage.getItem('gokul-cart'));
  await floating.click();const sheet=page.getByRole('dialog',{name:'Jump to a category',exact:true});
  await sheet.getByRole('button',{name:/Dairy/}).click();await page.locator('#gokul-product-3').waitFor();assert.equal(await page.locator('#gokul-product-1').count(),0);
  await page.waitForFunction(()=>{const n=document.querySelector('#menu-category-2');return n&&n.getBoundingClientRect().top>=52&&n.getBoundingClientRect().top<innerHeight/2;});
  await floating.click();await sheet.getByRole('button',{name:/^All items\s*\d+$/}).click();await page.locator('#gokul-product-1').waitFor();
  assert.equal(await page.evaluate(()=>localStorage.getItem('gokul-cart')),cart);
  console.log(`Loading/category ${width}px: desktop dock separation, exact category, All and cart preservation passed`);
 }else{assert.equal(await floating.count(),0);console.log(`Loading ${width}px enabled=${themed}: shared brand; ordering layout preserved`);}
 assert.deepEqual(errors,[]);await context.close();
 }}finally{await browser.close();}
