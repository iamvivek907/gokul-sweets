import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const base=process.env.BROWSER_BASE??'http://127.0.0.1:3311',date=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata'}).format(new Date(Date.now()+86400000));
const branch={id:1,name:'Main branch',active:true,operational:true,pickupAvailable:true};
const product=(id,name,categoryId,categoryName,price)=>({id,name,categoryId,categoryName,price,available:true,saleMode:'UNIT',description:'Prepared for your pickup.',imageUrl:'/arrival-mithai.webp'});
const sweets=[product(1,'Rasgulla',1,'Sweets',15),{...product(2,'Gulab Jamun',1,'Sweets',500),saleMode:'WEIGHT',minimumWeightGrams:250,weightStepGrams:50}],dairy=[product(3,'Dahi 200 g',2,'Dairy',25),product(4,'Dahi 400 g',2,'Dairy',50),product(5,'Dahi 1 kg',2,'Dairy',120),product(6,'Dahi 5 kg',2,'Dairy',550)],snacks=[product(7,'Butter biscuits',3,'Snacks',40),product(8,'Namkeen',3,'Snacks',60)],products=[...sweets,...dairy,...snacks];
const groups=[{key:'dahi',title:'Dahi',choices:dairy.map(p=>({productId:p.id,label:p.name.replace('Dahi ','')+' pack'}))}],slot={id:1,branchId:1,slotDate:date,startTime:'18:00:00',endTime:'19:00:00',active:true,remainingCapacity:20,priorityEnabled:false};
const browser=await chromium.launch({headless:true});
try{for(const width of [320,390,640]){
 const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:'block',reducedMotion:width===640?'reduce':'no-preference'}),page=await context.newPage();page.setDefaultTimeout(15000);let blockRasgulla=false,delay=false,releaseInitial,stockGate=null,denyAddition=false,checkedItems=[];const initialGate=new Promise(resolve=>{releaseInitial=resolve;});const errors=[];page.on('pageerror',e=>errors.push(e.message));
 const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Methods':'GET,POST,OPTIONS','Access-Control-Allow-Headers':'content-type'};
 await context.route('**/api/**',async route=>{const path=new URL(route.request().url()).pathname;let json=[];
  if(route.request().method()==='OPTIONS')return route.fulfill({status:204,headers});
  if(path==='/api/storefront/features')json={futuristicStorefrontV2:true,checkoutExperienceV2:true,contextualStorefrontV2:true,simplifiedCheckout:true,acceptedCheckoutQuote:true,smartAvailability:true,pickupAddOns:true,reviews:true,notificationInbox:true,today:date,futureOrderingDays:30};
  else if(path==='/api/branches')json=[branch];else if(path==='/api/branches/1')json=branch;
  else if(path==='/api/menu/catalog')json={revision:'1',categories:[{id:1,name:'Sweets',products:sweets},{id:2,name:'Dairy',products:dairy},{id:3,name:'Snacks',products:snacks}]};
  else if(path==='/api/menu'){await initialGate;json=[{id:1,name:'Sweets',products:sweets},{id:2,name:'Dairy',products:dairy},{id:3,name:'Snacks',products:snacks}];}
  else if(path==='/api/menu/appearance')json={pairings:[7,4,3].map(suggestedProductId=>({seedProductId:1,suggestedProductId})),banners:[{key:'admin-feature',titleEn:'Festival favourites',titleHi:'त्योहार की मिठास',subtitleEn:'Sweets for pickup.',subtitleHi:'पिकअप के लिए मिठाइयाँ।',buttonLabel:'Explore sweets',categoryId:1,mediaUrl:'/arrival-mithai.webp',mediaType:'image/webp',posterUrl:null,frame:{x:50,y:50,zoom:100,fit:'COVER'}}],categories:[]};
  else if(path==='/api/menu/portion-groups')json={version:1,groups};
  else if(path==='/api/storefront/customer-identity')json={enabled:true};else if(path==='/api/customer/identity/me')json={authenticated:true,name:'Test customer',phone:null};
  else if(path.endsWith('/availability')){const preview=new URL(route.request().url()).searchParams.has('menuPreview');if(!preview){checkedItems=route.request().postDataJSON().items;if(denyAddition){json={today:date,maximumDate:date,dates:[{date,slots:[{slot,normalAvailable:false}]}]};return route.fulfill({json,headers});}}const gate=stockGate;if(gate)await gate;if(delay)await new Promise(r=>setTimeout(r,1500));json={today:date,maximumDate:date,dates:[{date,available:true,items:products.map(p=>({productId:p.id,available:p.id!==6&&!(p.id===1&&blockRasgulla),code:p.id===6?'NO_INVENTORY':p.id===1&&blockRasgulla?'QUANTITY_TOO_LARGE':null})),slots:[{slot,normalAvailable:true,priorityAvailable:false,issues:[]}]}]};}
  else if(path==='/api/menu/pickup-addons')json=[snacks[0],dairy[1],dairy[0],sweets[1]].map(product=>({product,weightGrams:product.saleMode==='WEIGHT'?250:null,portionPrice:product.saleMode==='WEIGHT'?125:product.price,portionTotal:product.saleMode==='WEIGHT'?125:product.price,reason:'Something extra',slotVerified:true,includesTax:true}));else if(path==='/api/menu/pickup-addons/check')assert.fail('menu additions with a pickup must not request a second stock check');
  await route.fulfill({json,headers}).catch(()=>{});
 });
 await context.addInitScript(({branch,date,slot})=>{localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));localStorage.setItem('gokul-pickup-intent',JSON.stringify({branchId:1,date}));localStorage.setItem('gokul-selected-pickup-slot',JSON.stringify({date,slot,pickupType:'NORMAL'}));},{branch,date,slot});
 await page.goto(`${base}/menu`);await page.locator('#gokul-product-1').waitFor();
 assert.match(await page.locator('#gokul-product-1').innerText(),/Rasgulla/);
 assert.equal(await page.locator('#gokul-product-1').getByRole('button',{name:'Add Rasgulla to cart',exact:true}).isDisabled(),true,'display-only catalog cannot enable ordering before live checks');
 releaseInitial();await page.locator('#gokul-product-1').getByRole('button',{name:'Add Rasgulla to cart',exact:true}).waitFor();await page.waitForFunction(()=>!document.querySelector('#gokul-product-1 button[data-ordering-target="add"]')?.disabled);await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});
 await page.locator('#gokul-product-1').getByRole('button',{name:'Add Rasgulla to cart',exact:true}).click();
 const rail=page.locator('.mobile-menu-pairings-inline');await rail.getByRole('button',{name:'Add Gulab Jamun from pairings',exact:true}).waitFor();
 // A recommendation can remain in the rail when a newly built meal includes it.
 await page.evaluate(([sweet,side])=>{const cart=JSON.parse(localStorage.getItem('gokul-cart'));cart.items.push({product:sweet,quantity:1,weightGrams:500},{product:side,quantity:1,weightGrams:null});cart.meals=[{instanceId:'reviewmeal',mealId:1,version:1,title:'Sweet meal',quantity:1,optionIds:['sweet','side'],regularTotal:290,total:261,components:[{productId:sweet.id,quantity:1,weightGrams:500},{productId:side.id,quantity:1,weightGrams:null}]}];localStorage.setItem('gokul-cart',JSON.stringify(cart));window.dispatchEvent(new Event('gokul-cart-change'));},[sweets[1],snacks[0]]);
 const weight=()=>page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-cart')).items.find(i=>i.product.id===2).weightGrams);
 const saved=()=>page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-cart')));
 await rail.getByRole('button',{name:'Add Gulab Jamun from pairings',exact:true}).waitFor();
 await rail.getByRole('button',{name:'Add Butter biscuits from pairings',exact:true}).waitFor();
 await rail.getByRole('button',{name:'Add Gulab Jamun from pairings',exact:true}).click();
 await rail.getByRole('group',{name:'Gulab Jamun quantity: 250 g',exact:true}).waitFor();
 assert.equal(await weight(),750);assert.equal(checkedItems.find(i=>i.productId===2).weightGrams,750,'checked and committed total weights match');
 await rail.getByRole('button',{name:'Add one more Gulab Jamun',exact:true}).click();await rail.getByRole('group',{name:'Gulab Jamun quantity: 500 g',exact:true}).waitFor();
 assert.equal(await weight(),1000);assert.equal(checkedItems.find(i=>i.productId===2).weightGrams,1000);
 for(const expected of [950,900,850,800,750,500]){await rail.getByRole('button',{name:'Remove one Gulab Jamun',exact:true}).click();await page.waitForFunction(value=>JSON.parse(localStorage.getItem('gokul-cart')).items.find(i=>i.product.id===2).weightGrams===value,expected);}
 await rail.getByRole('button',{name:'Add Gulab Jamun from pairings',exact:true}).waitFor();
 await rail.getByRole('button',{name:'Add Butter biscuits from pairings',exact:true}).click();await rail.getByRole('group',{name:'Butter biscuits quantity: 1',exact:true}).waitFor();
 assert.equal((await saved()).items.find(i=>i.product.id===7).quantity,2);assert.equal(checkedItems.find(i=>i.productId===7).quantity,2);
 await rail.getByRole('button',{name:'Remove one Butter biscuits',exact:true}).click();await rail.getByRole('button',{name:'Add Butter biscuits from pairings',exact:true}).waitFor();
 assert.equal((await saved()).items.find(i=>i.product.id===7).quantity,1);
 denyAddition=true;const beforeFailure=await page.evaluate(()=>localStorage.getItem('gokul-cart'));
 await rail.getByRole('button',{name:'Add Gulab Jamun from pairings',exact:true}).click();await page.getByText('This addition no longer fits your pickup. Choose another time.',{exact:true}).waitFor();
 assert.equal(await page.evaluate(()=>localStorage.getItem('gokul-cart')),beforeFailure,'failed checks cannot mutate meal or extras');
 const cart=await saved();assert.equal(cart.meals.length,1);assert.equal(cart.meals[0].total,261);assert.equal(cart.mealNotice,undefined);assert.equal(await weight(),500);assert.deepEqual(errors,[]);
 await context.close();console.log(`PASS meal pairing totals, extra-only controls and failed-check recovery ${width}px`);
}}finally{await browser.close();}
