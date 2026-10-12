import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {mkdir} from 'node:fs/promises';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const screenshotDir=process.env.SCREENSHOT_DIR??'/tmp/gokul-premium';
const base=process.env.BROWSER_BASE??'http://127.0.0.1:3311',date=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata'}).format(new Date(Date.now()+86400000));
const branch={id:1,name:'Main branch',active:true,operational:true,pickupAvailable:true};
const product=(id,name,categoryId,categoryName,price)=>({id,name,categoryId,categoryName,price,available:true,saleMode:'UNIT',description:'Prepared for your pickup.',imageUrl:'/arrival-mithai.webp'});
const sweets=[product(1,'Rasgulla',1,'Sweets',15),{...product(2,'Gulab Jamun',1,'Sweets',500),saleMode:'WEIGHT',minimumWeightGrams:250,weightStepGrams:50}],dairy=[product(3,'Dahi 200 g',2,'Dairy',25),product(4,'Dahi 400 g',2,'Dairy',50),product(5,'Dahi 1 kg',2,'Dairy',120),product(6,'Dahi 5 kg',2,'Dairy',550)],snacks=[product(7,'Butter biscuits',3,'Snacks',40),product(8,'Namkeen',3,'Snacks',60)],products=[...sweets,...dairy,...snacks];
const groups=[{key:'dahi',title:'Dahi',choices:dairy.map(p=>({productId:p.id,label:p.name.replace('Dahi ','')+' pack'}))}],slot={id:1,branchId:1,slotDate:date,startTime:'18:00:00',endTime:'19:00:00',active:true,remainingCapacity:20,priorityEnabled:false};
const browser=await chromium.launch({headless:true});
try{for(const width of [320,390,640,1024]){
 const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:'block',reducedMotion:width===640?'reduce':'no-preference'}),page=await context.newPage();page.setDefaultTimeout(15000);let blockRasgulla=false,delay=false,releaseInitial,stockGate=null,releaseStock;const initialGate=new Promise(resolve=>{releaseInitial=resolve;});const errors=[];page.on('pageerror',e=>errors.push(e.message));
 const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Methods':'GET,POST,OPTIONS','Access-Control-Allow-Headers':'content-type'};
 await context.route('**/api/**',async route=>{const path=new URL(route.request().url()).pathname;let json=[];
  if(route.request().method()==='OPTIONS')return route.fulfill({status:204,headers});
  if(path==='/api/storefront/features')json={futuristicStorefrontV2:true,checkoutExperienceV2:true,contextualStorefrontV2:true,simplifiedCheckout:true,acceptedCheckoutQuote:true,smartAvailability:true,pickupAddOns:true,reviews:true,notificationInbox:true,today:date,futureOrderingDays:30};
  else if(path==='/api/branches')json=[branch];else if(path==='/api/branches/1')json=branch;
  else if(path==='/api/menu/catalog')json={revision:'1',categories:[{id:1,name:'Sweets',products:sweets},{id:2,name:'Dairy',products:dairy},{id:3,name:'Snacks',products:snacks}]};
  else if(path==='/api/menu'){await initialGate;json=[{id:1,name:'Sweets',products:sweets},{id:2,name:'Dairy',products:dairy},{id:3,name:'Snacks',products:snacks}];}
  else if(path==='/api/menu/portion-groups')json={version:1,groups};
  else if(path==='/api/storefront/customer-identity')json={enabled:true};else if(path==='/api/customer/identity/me')json={authenticated:true,name:'Vivek',phone:'+919876543210'};
  else if(path.endsWith('/availability')){const gate=stockGate;if(gate)await gate;if(delay)await new Promise(r=>setTimeout(r,1500));json={today:date,maximumDate:date,dates:[{date,available:true,items:products.map(p=>({productId:p.id,available:p.id!==6&&!(p.id===1&&blockRasgulla),code:p.id===6?'NO_INVENTORY':p.id===1&&blockRasgulla?'QUANTITY_TOO_LARGE':null})),slots:[{slot,normalAvailable:true,priorityAvailable:false,issues:[]}]}]};}
  else if(path==='/api/menu/pickup-addons')json=[snacks[0],dairy[1],dairy[0],sweets[1]].map(product=>({product,weightGrams:product.saleMode==='WEIGHT'?250:null,portionPrice:product.saleMode==='WEIGHT'?125:product.price,portionTotal:product.saleMode==='WEIGHT'?125:product.price,reason:'Something extra',slotVerified:true,includesTax:true}));else if(path==='/api/menu/pickup-addons/check')json={orderable:true};
  await route.fulfill({json,headers}).catch(()=>{});
 });
 await context.addInitScript(({branch,date,slot})=>{localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));localStorage.setItem('gokul-pickup-intent',JSON.stringify({branchId:1,date}));localStorage.setItem('gokul-selected-pickup-slot',JSON.stringify({date,slot,pickupType:'NORMAL'}));},{branch,date,slot});
 await page.goto(`${base}/menu`);await page.locator('#gokul-product-1').waitFor();
 assert.match(await page.locator('#gokul-product-1').innerText(),/Rasgulla/);
 assert.equal(await page.locator('#gokul-product-1').getByRole('button',{name:'Add Rasgulla to cart',exact:true}).isDisabled(),true,'display-only catalog cannot enable ordering before live checks');
 releaseInitial();await page.locator('#gokul-product-1').getByRole('button',{name:'Add Rasgulla to cart',exact:true}).waitFor();await page.waitForFunction(()=>!document.querySelector('#gokul-product-1 button[data-ordering-target="add"]')?.disabled);await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});
 if(width>640){assert.equal(await page.locator('.menu-editorial-feature').count(),0);assert.equal(await page.locator('.mobile-portion-card').count(),0);assert.equal(await page.getByRole('heading',{name:'Dahi 400 g',exact:true}).count(),1);assert.equal(await page.locator('.customer-bottom-navigation a').count(),5);assert.equal(await page.locator('.customer-bottom-navigation').getAttribute('data-reference-menu'),null);await context.close();console.log('Premium menu desktop preserved');continue;}
 await page.locator('.menu-editorial-feature').waitFor();assert.equal(await page.locator('.menu-category-tile').count(),2);assert.equal(await page.locator('.customer-notification-bell').isVisible(),true);assert.equal(await page.locator('.customer-account-link').isVisible(),true);
 const brand=await page.locator('.reference-wordmark').boundingBox();

 const account=await page.locator('.customer-account-link').boundingBox();
 const bell=await page.locator('.customer-notification-bell').boundingBox();
 for(const action of [account,bell])assert.ok(brand.y+brand.height<=action.y||brand.x+brand.width<=action.x,'header actions do not overlap the wordmark');
 const language=await page.locator('.language-trigger').boundingBox();
 assert.ok(language.y<bell.y+bell.height&&bell.y<language.y+language.height&&language.x+language.width<=bell.x+1,'language sits beside the notification bell');
 for(const action of [language,bell,account]){assert.ok(Math.abs(brand.y+brand.height/2-action.y-action.height/2)<3,'brand and all controls share one row');assert.ok(brand.x+brand.width<=action.x,'controls do not overlap brand');assert.ok(action.width>=44&&action.height>=44,'44px touch targets retained');}
 assert.ok((await page.locator('.customer-site-header').boundingBox()).height<=54,'header remains one compact row');
 const pickup=await page.locator('.mobile-menu-pickup').boundingBox(),searchBox=await page.locator('.menu-search').boundingBox();
 assert.ok(pickup.y+pickup.height<=searchBox.y,'pickup precedes search');
 await mkdir(screenshotDir,{recursive:true});await page.screenshot({path:`${screenshotDir}/reference-empty-${width}.png`});
 const nav=page.getByRole('navigation',{name:'Primary navigation'});
 await page.waitForFunction(()=>document.querySelector('.customer-bottom-navigation')?.dataset.referenceMenu==='true');
 assert.deepEqual(await nav.locator('a').evaluateAll(nodes=>nodes.map(node=>node.dataset.navIcon)),['home','menu','orders','profile']);
 assert.equal(await nav.locator('[data-nav-icon=menu]').getAttribute('aria-current'),'page');
 const navBox=await nav.boundingBox();assert.equal(navBox.x,0);assert.equal(navBox.width,width);assert.equal(await nav.evaluate(node=>getComputedStyle(node).borderRadius),'0px');
 for(const link of await nav.locator('a').all()){
  assert.equal(await link.evaluate(node=>getComputedStyle(node).backgroundColor),'rgba(0, 0, 0, 0)');
  assert.equal(await link.locator('span').first().evaluate(node=>getComputedStyle(node).backgroundColor),'rgba(0, 0, 0, 0)');
  assert.ok((await link.boundingBox()).height>=44);
 }
 assert.equal(await nav.locator('[data-nav-icon=menu] svg rect').count(),4);
 assert.equal(await nav.locator('[data-nav-icon=orders] svg rect').count(),1);
 assert.equal(await nav.locator('[data-nav-icon=menu] svg').evaluate(node=>getComputedStyle(node).stroke),'rgb(152, 12, 49)');
 assert.equal(await nav.locator('[data-nav-icon=profile] svg').evaluate(node=>getComputedStyle(node).stroke),'rgb(100, 112, 128)');
 await mkdir(screenshotDir,{recursive:true});await nav.screenshot({path:`${screenshotDir}/navigation-${width}.png`});
 const rasgulla=page.locator('#gokul-product-1');await rasgulla.getByRole('button',{name:'Add Rasgulla to cart',exact:true}).click();await rasgulla.getByRole('group',{name:'Rasgulla quantity: 1',exact:true}).waitFor();
 const dock=page.locator('.gokul-floating-cart');
 await dock.locator('.reference-cart-summary').getByText('1 item · ₹15',{exact:true}).waitFor();
 const summaryBox=await dock.locator('.reference-cart-summary').boundingBox(),actionBox=await dock.locator('.reference-cart-action').boundingBox();
 assert.ok(summaryBox.x+summaryBox.width<=actionBox.x,'cart summary and action do not overlap');
 await page.locator('.mobile-menu-suggestions').waitFor();
 await page.screenshot({path:`${screenshotDir}/reference-added-${width}.png`});const paired=page.locator('.mobile-menu-pairings-inline');await paired.getByRole('button',{name:'Add Butter biscuits from pairings',exact:true}).click();await paired.getByRole('group',{name:'Butter biscuits quantity: 1',exact:true}).waitFor();const biscuitCount=paired.getByRole('group',{name:'Butter biscuits quantity: 1',exact:true});const qtyBox=await biscuitCount.locator('span').boundingBox(),subtractBox=await biscuitCount.getByRole('button',{name:'Remove one Butter biscuits'}).boundingBox(),addBox=await biscuitCount.getByRole('button',{name:'Add one more Butter biscuits'}).boundingBox();assert.ok(subtractBox.x+subtractBox.width<=qtyBox.x&&qtyBox.x+qtyBox.width<=addBox.x,'pairing quantity stays between the buttons');assert.equal(await page.locator('#gokul-product-7').getByRole('group',{name:'Butter biscuits quantity: 1',exact:true}).count(),1);
 await paired.getByRole('heading',{name:'Dahi',exact:true}).waitFor();assert.equal(await paired.getByRole('heading',{name:'Dahi',exact:true}).count(),1,'paired variants appear as one product family');const groupButton=paired.getByRole('button',{name:'Choose options for Dahi from pairings',exact:true});const groupBox=await groupButton.boundingBox(),photoBox=await groupButton.locator('..').boundingBox();assert.ok(groupBox.x>=photoBox.x+photoBox.width,'grouped pairing actions stay beside the photo');assert.ok(groupBox.y+groupBox.height<=photoBox.y+photoBox.height,'grouped pairing actions are not clipped');
 await paired.getByRole('button',{name:'Choose options for Dahi from pairings',exact:true}).click();const pairedSizes=page.getByRole('dialog',{name:'Dahi',exact:true});await pairedSizes.getByRole('button',{name:'Add Dahi 200 g pack to cart',exact:true}).click();await pairedSizes.getByRole('group',{name:'Dahi 200 g pack quantity: 1',exact:true}).waitFor();await pairedSizes.getByRole('button',{name:'Remove one Dahi 200 g pack',exact:true}).click();await pairedSizes.getByRole('button',{name:'Done',exact:true}).click();
 await paired.getByRole('button',{name:'Add Gulab Jamun from pairings',exact:true}).click();await paired.getByRole('group',{name:'Gulab Jamun quantity: 250 g',exact:true}).waitFor();const minus=paired.getByRole('button',{name:'Remove one Gulab Jamun',exact:true}),plus=paired.getByRole('button',{name:'Add one more Gulab Jamun',exact:true});const minusBox=await minus.boundingBox(),plusBox=await plus.boundingBox();assert.ok(minusBox.x+minusBox.width<=plusBox.x,'pairing stepper buttons cannot overlap');await minus.click();await paired.getByRole('button',{name:'Add Gulab Jamun from pairings',exact:true}).waitFor();
 await page.getByRole('button',{name:'View optional additions',exact:true}).click();await page.getByRole('dialog',{name:'Optional additions',exact:true}).getByRole('button',{name:'Choose options for Dahi from pairings',exact:true}).click();await pairedSizes.waitFor();assert.equal(await page.getByRole('dialog').count(),1,'choosing paired variants replaces the additions sheet');await pairedSizes.getByRole('button',{name:'Done',exact:true}).click();await page.waitForFunction(()=>document.activeElement?.id==='menu-pairing-group-dahi');
 delay=true;await rasgulla.getByRole('button',{name:'Add one more Rasgulla'}).click();assert.equal(await rasgulla.getByRole('button',{name:'Add one more Rasgulla'}).isEnabled(),true);await rasgulla.getByRole('group',{name:'Rasgulla quantity: 2',exact:true}).waitFor();assert.equal(await rasgulla.locator('.product-card-controls').evaluate(n=>getComputedStyle(n).overflow),'hidden');assert.equal(await page.locator('.mobile-cart-offer-slot').count(),0);
 // Hold the upcoming cart refresh until the stock transition is explicitly released.
 stockGate=new Promise(resolve=>{releaseStock=resolve;});
 const dahi=page.locator('#gokul-product-3');await dahi.getByRole('button',{name:'Choose options for Dahi'}).click();const sheet=page.getByRole('dialog',{name:'Dahi',exact:true});await sheet.waitFor();await sheet.getByRole('button',{name:'Add Dahi 400 g pack to cart',exact:true}).click();await sheet.getByRole('button',{name:'Add one more Dahi 400 g pack'}).click();await sheet.getByRole('button',{name:'Add Dahi 1 kg pack to cart',exact:true}).click();assert.equal(await sheet.getByRole('button',{name:'Add Dahi 5 kg pack to cart',exact:true}).isDisabled(),true);await sheet.getByText(/3 selected · ₹220/).waitFor();
 await mkdir(screenshotDir,{recursive:true});await page.screenshot({path:`${screenshotDir}/variants-${width}.png`});await sheet.getByRole('button',{name:'Done',exact:true}).click();const cart=await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-cart')));assert.equal(cart.items.find(i=>i.product.id===4).quantity,2);assert.equal(cart.items.find(i=>i.product.id===5).quantity,1);assert.equal(cart.items.some(i=>i.product.id===6),false);
 blockRasgulla=true;releaseStock();await page.waitForFunction(()=>document.querySelector('#gokul-product-1 button[aria-label="Add one more Rasgulla"]')?.disabled===true);assert.equal(await rasgulla.getByRole('group',{name:'Rasgulla quantity: 2',exact:true}).count(),1,'stock refresh preserves the existing quantity');assert.equal(await rasgulla.getByRole('button',{name:'Remove one Rasgulla'}).isEnabled(),true);await rasgulla.getByRole('button',{name:'Remove one Rasgulla'}).click();await rasgulla.getByRole('group',{name:'Rasgulla quantity: 1',exact:true}).waitFor();assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);assert.deepEqual(errors,[]);await page.evaluate(()=>window.scrollTo({top:0,behavior:'instant'}));await page.screenshot({path:`${screenshotDir}/menu-${width}.png`});await context.close();console.log(`Premium menu ${width}px quantities, pairings, variants and stock passed`);
}}finally{await browser.close();}
