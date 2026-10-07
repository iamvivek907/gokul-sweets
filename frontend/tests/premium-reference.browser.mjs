import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {mkdir,readFile} from 'node:fs/promises';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const screenshotDir=process.env.SCREENSHOT_DIR??'/tmp/gokul-reference-rebuild';
const base=process.env.BROWSER_BASE??'http://127.0.0.1:3311',date=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata'}).format(new Date(Date.now()+86400000));
const branch={id:1,name:'Main branch',active:true,operational:true,pickupAvailable:true};
const product=(id,name,categoryId,categoryName,price)=>({id,name,categoryId,categoryName,price,available:true,saleMode:'UNIT',description:id===1?'Soft, spongy and juicy':id===2?'Premium cashew sweetness':id===7?'100 g':id===8?'200 g':'Prepared for your pickup.',imageUrl:`/visual-fixture/${id===1?'rasgulla':id===2?'katli':id<7?'dahi':id===7?'biscuits':'namkeen'}.webp`});
const sweets=[product(1,'Rasgulla',1,'Sweets',15),{...product(2,'Kaju Katli',1,'Sweets',800),saleMode:'WEIGHT',minimumWeightGrams:250,weightStepGrams:50}],dairy=[product(3,'Dahi 200 g',2,'Dairy',25),product(4,'Dahi 400 g',2,'Dairy',50),product(5,'Dahi 1 kg',2,'Dairy',120),product(6,'Dahi 5 kg',2,'Dairy',550)],snacks=[product(7,'Butter biscuits',3,'Snacks',40),product(8,'Namkeen',3,'Snacks',60)],food=[{...product(11,'Special Thali',4,'Lunch & dinner',230),description:'A complete meal, made fresh',imageUrl:'/visual-fixture/thali.webp'},{...product(12,'Chole Bhature',4,'Lunch & dinner',70),description:'Classic North Indian favourite',imageUrl:'/visual-fixture/bhature.webp'}],drinks=[{...product(9,'Cola 200 ml',5,'Drinks',40),imageUrl:'/visual-fixture/cola.webp'},{...product(10,'Cola 500 ml',5,'Drinks',60),imageUrl:'/visual-fixture/cola.webp'}],bakery=[{...product(13,'Chocolate Truffle Cake',6,'Bakery',320),imageUrl:'/visual-fixture/cake.webp'}],products=[...food,...sweets,...dairy,...snacks,...drinks,...bakery];
const groups=[{key:"cola",title:"Cola",choices:drinks.map(p=>({productId:p.id,label:p.name.replace("Cola ","")}))},{key:'dahi',title:'Dahi',choices:dairy.map(p=>({productId:p.id,label:p.name.replace('Dahi ','')+' pack'}))}],slot={id:1,branchId:1,slotDate:date,startTime:'18:00:00',endTime:'18:30:00',active:true,remainingCapacity:20,priorityEnabled:false};
const browser=await chromium.launch({headless:true});
try{for(const width of [320,390,640]){
 const context=await browser.newContext({viewport:{width,height:Math.round(width*886/420)},serviceWorkers:'block',reducedMotion:width===640?'reduce':'no-preference'}),page=await context.newPage();page.setDefaultTimeout(15000);let blockRasgulla=false,delay=false;const errors=[];page.on('pageerror',e=>errors.push(e.message));
 const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Methods':'GET,POST,OPTIONS','Access-Control-Allow-Headers':'content-type'};
 await context.route('**/_next/image**',async route=>{const src=new URL(route.request().url()).searchParams.get('url');if(!src?.startsWith('/visual-fixture/'))return route.continue();await route.fulfill({contentType:'image/webp',body:await readFile(new URL(`./fixtures/premium-menu/${src.split('/').at(-1)}`,import.meta.url))});});
 await context.route('**/api/**' ,async route=>{const path=new URL(route.request().url()).pathname;let json=[];
  if(route.request().method()==='OPTIONS')return route.fulfill({status:204,headers});
  if(path==='/api/storefront/features')json={futuristicStorefrontV2:true,checkoutExperienceV2:true,contextualStorefrontV2:true,simplifiedCheckout:true,acceptedCheckoutQuote:true,smartAvailability:true,pickupAddOns:true,reviews:true,notificationInbox:true,today:date,futureOrderingDays:30};
  else if(path==='/api/branches')json=[branch];else if(path==='/api/branches/1')json=branch;
  else if(path==='/api/menu')json=[{id:4,name:'Lunch & dinner',products:food},{id:5,name:'Drinks',products:drinks},{id:6,name:'Bakery',products:bakery},{id:1,name:'Sweets',products:sweets},{id:2,name:'Dairy',products:dairy},{id:3,name:'Snacks',products:snacks}];
  else if(path==='/api/menu/portion-groups')json={version:1,groups};
  else if(path==='/api/storefront/customer-identity')json={enabled:true};else if(path==='/api/customer/identity/me')json={authenticated:true,name:'Vivek',phone:'+919876543210'};
  else if(path.endsWith('/availability')){if(delay)await new Promise(r=>setTimeout(r,1500));json={today:date,maximumDate:date,dates:[{date,available:true,items:products.map(p=>({productId:p.id,available:p.id!==6&&!(p.id===1&&blockRasgulla),code:p.id===6?'NO_INVENTORY':p.id===1&&blockRasgulla?'QUANTITY_TOO_LARGE':null})),slots:[{slot,normalAvailable:true,priorityAvailable:false,issues:[]}]}]};}
  else if(path==='/api/menu/pickup-addons')json=[snacks[0],snacks[1]].map(product=>({product,weightGrams:product.saleMode==='WEIGHT'?250:null,portionPrice:product.saleMode==='WEIGHT'?125:product.price,portionTotal:product.saleMode==='WEIGHT'?125:product.price,reason:'Something extra',slotVerified:true,includesTax:true}));else if(path==='/api/menu/pickup-addons/check')json={orderable:true};
  await route.fulfill({json,headers}).catch(()=>{});
 });

 await context.addInitScript(branch=>localStorage.setItem('gokul-selected-branch',JSON.stringify(branch)),branch);
 await page.goto(`${base}/menu`);await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});await page.locator('#gokul-product-11').waitFor();
 assert.deepEqual(await page.locator('.menu-category-tile strong').allTextContents(),['Food','Sweets','Bakery','Snacks']);
 const pickup=await page.locator('.mobile-menu-pickup').boundingBox(),search=await page.locator('.menu-search').boundingBox();assert.ok(pickup.y+pickup.height<=search.y);
 const lang=await page.locator('.language-trigger').boundingBox(),bell=await page.locator('.customer-notification-bell').boundingBox();assert.ok(lang.x+lang.width<=bell.x+1&&Math.abs(lang.y-bell.y)<8);
 const brandBox=await page.locator('.reference-wordmark').boundingBox();
 for(const control of ['.language-trigger','.customer-notification-bell','.customer-account-link']){const box=await page.locator(control).boundingBox();assert.ok(Math.abs(brandBox.y+brandBox.height/2-box.y-box.height/2)<3,'brand and customer controls share a row');}
 await mkdir(screenshotDir,{recursive:true});
 const capture=async name=>{await page.evaluate(async()=>{await document.fonts.ready;await Promise.all([...document.images].filter(img=>img.complete).map(img=>img.decode().catch(()=>{})));window.scrollTo({top:0,behavior:'instant'});});await page.waitForTimeout(600);assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);await page.screenshot({path:`${screenshotDir}/${name}-${width}.png`});};
 assert.equal(await page.getByRole('button',{name:'Browse Food',exact:true}).getAttribute('aria-current'),'true','initial Food collection reflects the reference');
 assert.equal(await page.locator('#gokul-product-1').count(),0,'initial Food collection excludes other families');
 await capture('food');
 await page.getByRole('button',{name:'Browse Food',exact:true}).click();
 await page.locator('#gokul-product-1').waitFor({state:'detached'});
 await page.getByRole('button',{name:'Explore sweets',exact:true}).click();
 await page.locator('#gokul-product-1').waitFor();
 assert.equal(await page.getByRole('button',{name:'Browse Sweets',exact:true}).getAttribute('aria-current'),'true','banner selects the actual sweets family');
 await page.getByRole('button',{name:'Browse Food',exact:true}).click();
 await page.getByRole('button',{name:'Browse all item categories',exact:true}).click();
 const filters=page.getByRole('dialog',{name:'Filter menu',exact:true});
 await filters.getByRole('checkbox',{name:/Sweets/}).check();
 await filters.getByRole('button',{name:'Show items',exact:true}).click();
 await page.locator('#gokul-product-1').waitFor();
 assert.equal(await page.locator('#gokul-product-11').count(),0,'explicit category filter replaces the Food constraint');
 await page.getByRole('button',{name:'All',exact:true}).click();
 assert.equal(await page.locator('.menu-category-tile[aria-current=true]').count(),0,'All does not highlight a single family');

 await page.evaluate(({date,slot})=>{localStorage.setItem('gokul-pickup-intent',JSON.stringify({branchId:1,date}));localStorage.setItem('gokul-selected-pickup-slot',JSON.stringify({date,slot,pickupType:'NORMAL'}));window.dispatchEvent(new Event('gokul-pickup-slot-change'));window.dispatchEvent(new Event('gokul-pickup-intent-change'));},{date,slot});
 await page.getByRole('button',{name:'Browse Sweets',exact:true}).click();await page.locator('#gokul-product-11').waitFor({state:'detached'});await page.locator('#gokul-product-1').getByRole('button',{name:'Add Rasgulla to cart',exact:true}).click();await page.locator('.mobile-menu-pairings-inline').getByRole('heading',{name:'Namkeen',exact:true}).waitFor();await page.locator('.reference-cart-summary').getByText('1 item · ₹15',{exact:true}).waitFor();assert.equal(await page.locator('.menu-editorial-feature').count(),0);await capture('pairings');
 await page.getByRole('button',{name:'Browse Snacks',exact:true}).click();await page.locator('.menu-editorial-feature--mint').waitFor();await page.locator('#gokul-product-3').getByRole('button',{name:'Choose options for Dahi',exact:true}).click();const sizes=page.getByRole('dialog',{name:'Dahi',exact:true});await sizes.getByRole('button',{name:'Add Dahi 200 g pack to cart',exact:true}).click();await sizes.getByRole('button',{name:'Done',exact:true}).click();await page.locator('#gokul-product-7').getByRole('button',{name:'Add Butter biscuits to cart',exact:true}).click();await page.locator('.reference-cart-summary').getByText('3 items · ₹80',{exact:true}).waitFor();await page.getByRole('heading',{name:'Cola',exact:true}).waitFor();await page.locator('.menu-retail-collection-title').waitFor();await capture('retail');
 await page.locator('.menu-sweet-rail').getByRole('button',{name:'Browse Rasgulla',exact:true}).click();
 await page.locator('#gokul-product-1').waitFor();
 assert.equal(await page.getByRole('button',{name:'Browse Sweets',exact:true}).getAttribute('aria-current'),'true','discovery rail shares shortcut selection');
 assert.equal(await page.getByRole('button',{name:'Browse Snacks',exact:true}).getAttribute('aria-current'),null);
 await page.goto(`${base}/menu?category=1`);await page.locator('#gokul-product-1').waitFor();
 assert.equal(await page.locator('#gokul-product-11').count(),0,'explicit category links override the initial Food collection');
 assert.equal(await page.getByRole('button',{name:'Browse Sweets',exact:true}).getAttribute('aria-current'),'true','category link and shortcut selection agree');
 assert.deepEqual(errors,[]);await context.close();console.log(`Reference states ${width}px: passed`);
}}finally{await browser.close();}
