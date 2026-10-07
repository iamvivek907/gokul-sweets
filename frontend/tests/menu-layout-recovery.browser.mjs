import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const base=process.env.BROWSER_BASE??'http://127.0.0.1:3311',date=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata'}).format(new Date(Date.now()+86400000));
const branch={id:1,name:'Main branch',active:true,operational:true,pickupAvailable:true};
const product=(id,name,categoryId,categoryName,price)=>({id,name,categoryId,categoryName,price,available:true,saleMode:'UNIT',description:'Prepared for your pickup.',imageUrl:'/arrival-mithai.webp'});
const sweets=[product(1,'Rasgulla',1,'Sweets',15),{...product(2,'Gulab Jamun',1,'Sweets',500),saleMode:'WEIGHT',minimumWeightGrams:250,weightStepGrams:50}],dairy=[product(3,'Dahi 200 g',2,'Dairy',25),product(4,'Dahi 400 g',2,'Dairy',50),product(5,'Dahi 1 kg',2,'Dairy',120),product(6,'Dahi 5 kg',2,'Dairy',550)],snacks=[product(7,'Butter biscuits',3,'Snacks',40),product(8,'Namkeen',3,'Snacks',60)],products=[...sweets,...dairy,...snacks];
const groups=[{key:'dahi',title:'Dahi',choices:dairy.map(p=>({productId:p.id,label:p.name.replace('Dahi ','')+' pack'}))}],slot={id:1,branchId:1,slotDate:date,startTime:'18:00:00',endTime:'19:00:00',active:true,remainingCapacity:20,priorityEnabled:false};
const browser=await chromium.launch({headless:true});
try{for(const width of [320,390,640]){for(const outcome of ["populated","empty","failed"]){
 const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:'block',reducedMotion:width===640?'reduce':'no-preference'}),page=await context.newPage();page.setDefaultTimeout(15000);let blockRasgulla=false,delay=false,releaseOffers,releasePairings;let pairingOutcome=outcome;const offersGate=new Promise(resolve=>releaseOffers=resolve),pairingsGate=new Promise(resolve=>releasePairings=resolve);const errors=[];page.on('pageerror',e=>errors.push(e.message));
 const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Methods':'GET,POST,OPTIONS','Access-Control-Allow-Headers':'content-type'};
 await context.route('**/api/**',async route=>{const path=new URL(route.request().url()).pathname;let json=[];
  if(route.request().method()==='OPTIONS')return route.fulfill({status:204,headers});
  if(path==='/api/storefront/features')json={futuristicStorefrontV2:true,checkoutExperienceV2:true,contextualStorefrontV2:true,simplifiedCheckout:true,acceptedCheckoutQuote:true,smartAvailability:true,pickupAddOns:true,reviews:true,notificationInbox:true,today:date,futureOrderingDays:30};
  else if(path==='/api/branches')json=[branch];else if(path==='/api/branches/1')json=branch;
  else if(path==='/api/menu')json=[{id:1,name:'Sweets',products:sweets},{id:2,name:'Dairy',products:dairy},{id:3,name:'Snacks',products:snacks}];
  else if(path==='/api/menu/offers'){await offersGate;if(outcome==='failed')return route.fulfill({status:503,json:{message:'Unavailable'},headers});json=outcome==='empty'?[]:[{rebateId:3,code:'PUBLIC20',name:'Public offer',description:'Standard pickup',rebateType:'FIXED_AMOUNT',rebateValue:20,minimumOrderAmount:200,maximumDiscountAmount:null,tiers:[]}];}else if(path==='/api/menu/portion-groups')json={version:1,groups};
  else if(path==='/api/storefront/customer-identity')json={enabled:true};else if(path==='/api/customer/identity/me')json={authenticated:true,name:'Vivek',phone:'+919876543210'};
  else if(path.endsWith('/availability')){if(delay)await new Promise(r=>setTimeout(r,1500));json={today:date,maximumDate:date,dates:[{date,available:true,items:products.map(p=>({productId:p.id,available:p.id!==6&&!(p.id===1&&blockRasgulla),code:p.id===6?'NO_INVENTORY':p.id===1&&blockRasgulla?'QUANTITY_TOO_LARGE':null})),slots:[{slot,normalAvailable:true,priorityAvailable:false,issues:[]}]}]};}
  else if(path==='/api/menu/pickup-addons'){await pairingsGate;if(pairingOutcome==='failed')return route.fulfill({status:503,json:{message:'Unavailable'},headers});json=pairingOutcome==='empty'?[]:[snacks[0],dairy[1],dairy[0],sweets[1]].map(product=>({product,weightGrams:product.saleMode==='WEIGHT'?250:null,portionPrice:product.saleMode==='WEIGHT'?125:product.price,portionTotal:product.saleMode==='WEIGHT'?125:product.price,reason:'Something extra',slotVerified:true,includesTax:true}));}else if(path==='/api/menu/pickup-addons/check')json={orderable:true};
  await route.fulfill({json,headers}).catch(()=>{});
 });
 await context.addInitScript(({branch,date,slot})=>{localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));localStorage.setItem('gokul-pickup-intent',JSON.stringify({branchId:1,date}));localStorage.setItem('gokul-selected-pickup-slot',JSON.stringify({date,slot,pickupType:'NORMAL'}));},{branch,date,slot});
 await page.goto(`${base}/menu`);await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});await page.locator('#gokul-product-1').waitFor();

 const documentY=locator=>locator.evaluate(node=>node.getBoundingClientRect().y+scrollY);
 const stable=async(locator,before,label)=>assert.ok(Math.abs(await documentY(locator)-before)<=1,`${label}: ${before} -> ${await documentY(locator)}`);
 const row=page.locator('#gokul-product-1');
 const offerBefore=await documentY(row);assert.equal(await page.locator('#menu-offers-open').isVisible(),true);
 releaseOffers();
 if(outcome==='populated')await page.locator('#menu-offers-open').getByText(/₹20(?:\.00)? off above ₹200(?:\.00)?/).waitFor();
 else if(outcome==='failed')await page.locator('#menu-offers-open').getByText('Offers could not be checked. Try again.',{exact:true}).waitFor();
 else await page.locator('#menu-offers-open').getByText('Add items to discover eligible savings',{exact:true}).waitFor();
 await stable(row,offerBefore,`late ${outcome} offer`);
 for(const family of ['Sweets','Snacks']){
  await page.getByRole('button',{name:`Browse ${family}`,exact:true}).click();
  const filter=page.getByRole('button',{name:'Browse all item categories',exact:true});assert.equal(await filter.isVisible(),true);assert.equal(await page.getByRole('button',{name:'All',exact:true}).isVisible(),true);
  await filter.click();const sheet=page.getByRole('dialog',{name:'Filter menu',exact:true});await sheet.getByRole('checkbox',{name:/Sweets/}).check();await sheet.getByRole('button',{name:'Show items',exact:true}).click();await row.waitFor();assert.equal(await page.locator('#gokul-product-7').count(),0);
  await page.getByRole('button',{name:'All',exact:true}).click();await page.locator('#gokul-product-7').waitFor();
 }
 await row.getByRole('button',{name:'Add Rasgulla to cart',exact:true}).click();await page.locator('.mobile-menu-pairing-skeleton').waitFor();
 const next=page.locator('#gokul-product-3'),pairingBefore=await documentY(next);releasePairings();
 if(outcome==='populated')await page.locator('.mobile-menu-pairings-inline').getByRole('button',{name:'Add Butter biscuits from pairings',exact:true}).waitFor();
 else if(outcome==='empty')await page.locator('.mobile-menu-pairings-inline').getByText('No optional additions right now.',{exact:true}).waitFor();
 else await page.getByRole('button',{name:'Retry pairings',exact:true}).waitFor();
 await page.locator('.mobile-menu-pairing-skeleton').waitFor({state:'hidden'});await stable(next,pairingBefore,`late ${outcome} pairings`);
 if(outcome==='failed'){
  pairingOutcome='populated';await page.getByRole('button',{name:'Retry pairings',exact:true}).click();await page.locator('.mobile-menu-pairings-inline').getByRole('button',{name:'Add Butter biscuits from pairings',exact:true}).waitFor();await stable(next,pairingBefore,'pairing retry');
 }
 if(outcome!=='empty'){
  await page.getByRole('button',{name:'View optional additions',exact:true}).click();const additions=page.getByRole('dialog',{name:'Optional additions',exact:true});await additions.waitFor();await additions.getByRole('button',{name:'Close ×',exact:true}).click();await additions.waitFor({state:'hidden'});
 }
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);assert.deepEqual(errors,[]);await context.close();console.log(`Stable menu ${width}px ${outcome}: controls, offers, pairings and retry passed`);
 }}}finally{await browser.close();}
