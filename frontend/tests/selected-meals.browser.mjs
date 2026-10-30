import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {mkdir} from 'node:fs/promises';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const base=process.env.BROWSER_BASE??'http://127.0.0.1:3311',browser=await chromium.launch({headless:true});
const branch={id:1,name:'Tamkuhi Road',active:true,operational:true,pickupAvailable:true};
const products=[{id:1,name:'Veg Burger',price:100},{id:2,name:'Paneer Burger',price:120},{id:3,name:'Fries',price:60},{id:4,name:'Drink',price:40}].map(p=>({...p,categoryId:1,categoryName:'Food',available:true,saleMode:'UNIT',imageUrl:'/arrival-mithai.webp'}));
const option=(key,productId)=>({key,productId,productName:products.find(p=>p.id===productId).name,quantity:1,weightGrams:null,upgrade:0});
const initial={id:1,branchId:1,version:1,title:'Burger Combo',basePrice:0,pricingMode:'SELECTED_ITEMS',maximumDiscount:null,discountType:'PERCENT',discountValue:2,active:true,groups:[{key:'main',title:'Choose your burger',minimum:1,maximum:1,options:[option('paneer',2),option('veg',1)]},{key:'side',title:'Choose your side',minimum:1,maximum:1,options:[option('fries',3)]},{key:'drink',title:'Choose your drink',minimum:1,maximum:1,options:[option('drink',4)]}]};
try{for(const width of [320,390,1280]){
 const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:'block',reducedMotion:'reduce'}),page=await context.newPage();page.setDefaultTimeout(15000);let saved=structuredClone(initial),writes=0,serverVegPrice=100,unavailableIds=[],hideAllProducts=false;const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await context.addInitScript(branch=>{localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));localStorage.setItem('gokul-social-follow-popup-seen','true');localStorage.setItem('gokul-ordering-tour:v1','seen');},branch);
 const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Headers':'content-type,x-staff-csrf','Access-Control-Allow-Methods':'GET,POST,PUT,OPTIONS','Access-Control-Expose-Headers':'X-Staff-CSRF','X-Staff-CSRF':'test-csrf'};
 await context.route('**/api/**',async route=>{
  const request=route.request(),path=new URL(request.url()).pathname;let json=[];
  if(request.method()==='OPTIONS')return route.fulfill({status:204,headers});
  if(path==='/api/storefront/features')json={futuristicStorefrontV2:true,checkoutExperienceV2:true,contextualStorefrontV2:true,simplifiedCheckout:true,today:'2026-10-30',smartAvailability:false};
  else if(path==='/api/branches')json=[branch];else if(path==='/api/branches/1')json=branch;
  else if(path==='/api/storefront/customer-identity')json={enabled:false,guestCheckoutEnabled:true};else if(path==='/api/customer/identity/me')json={authenticated:false};
  else if(path==='/api/menu')json=hideAllProducts?[]:[{id:1,name:'Food',products:products.map(p=>({...p,available:!unavailableIds.includes(p.id),catalogAvailable:!unavailableIds.includes(p.id)}))}];else if(path==='/api/menu/portion-groups')json={groups:[]};
  else if(path==='/api/branches/1/meals')json=[saved];
  else if(path==='/api/branches/1/meals/quote'){
   const claim=request.postDataJSON();assert.equal(claim.total,undefined);const selected=saved.groups.flatMap(g=>g.options).filter(o=>claim.optionIds.includes(o.key));const priceFor=id=>id===1?serverVegPrice:products.find(p=>p.id===id).price;const regular=selected.reduce((n,o)=>n+priceFor(o.productId),0);const discount=Math.min(Math.round(regular*2)/100,saved.maximumDiscount??Infinity);
   json={mealId:1,version:saved.version,title:saved.title,quantity:claim.quantity,regularTotal:regular*claim.quantity,baseTotal:regular*claim.quantity,discount:discount*claim.quantity,upgrades:0,total:(regular-discount)*claim.quantity,components:selected.map(o=>({productId:o.productId,quantity:claim.quantity,weightGrams:null,regularTotal:priceFor(o.productId)*claim.quantity,unitPrice:priceFor(o.productId)}))};
  }
  else if(path==='/api/admin/auth/me')json={staffId:77,username:'owner',fullName:'Test owner',roleName:'OWNER_ADMIN',branchIds:[1],permissions:['MENU_MANAGE','INVENTORY_VIEW','INVENTORY_MANAGE']};
  else if(path==='/api/admin/branches/1/menu/workspace')json={content:[],totalElements:0,page:0,totalPages:0,categories:[],branchCategories:[],taxes:[]};
  else if(path.endsWith('/workspace/groups'))json={version:0,groups:[],total:0,page:0,totalPages:0};
  else if(path==='/api/admin/branches/1/meals/products')json={products:products.map(p=>({...p,minimumGrams:250,stepGrams:50,available:!unavailableIds.includes(p.id)})),nextAfter:null};
  else if(path==='/api/admin/branches/1/meals')json=[saved];
  else if(path==='/api/admin/branches/1/meals/1'){assert.equal(request.headers()['x-staff-csrf'],'test-csrf');const input=request.postDataJSON();assert.equal(input.basePrice,0);assert.equal(input.pricingMode,'SELECTED_ITEMS');assert.ok(input.groups.every(g=>g.options.every(o=>o.upgrade===0)));saved={...input,version:2};writes++;json=saved;}
  return route.fulfill({headers,json});
 });
 await page.goto(base+'/menu');await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});const card=page.getByRole('button',{name:'Build meal Burger Combo'});await card.waitFor();assert.equal(await card.locator('.menu-comparison-price').innerText(),'₹200');assert.equal(await card.locator('.menu-sale-price').innerText(),'₹196');
 await card.click();const dialog=page.getByRole('dialog',{name:'Burger Combo',exact:true});await dialog.getByLabel('Veg Burger',{exact:false}).check();await dialog.getByLabel('Fries',{exact:false}).check();await dialog.getByLabel('Drink',{exact:false}).check();
 assert.equal(await dialog.locator('.menu-sale-price').first().innerText(),'₹196');assert.equal(await dialog.locator('.menu-comparison-price').first().innerText(),'₹200');const color=await dialog.locator('.menu-sale-price').first().evaluate(e=>getComputedStyle(e).backgroundColor);assert.equal(color,'rgb(255, 220, 49)');
 await dialog.getByLabel('Paneer Burger',{exact:false}).check();assert.match(await dialog.locator('.menu-sale-price').first().innerText(),/215\.6(?:0)?$/);await dialog.getByRole('button',{name:'Increase meal quantity'}).click();assert.match(await dialog.locator('.menu-sale-price').first().innerText(),/431\.2(?:0)?$/);
 await mkdir('/tmp/selected-meal-shots',{recursive:true});await page.screenshot({path:`/tmp/selected-meal-shots/builder-${width}.png`});await dialog.getByRole('button',{name:/Add meal/}).click();await dialog.waitFor({state:'hidden'});const cart=await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-cart')));assert.equal(cart.meals[0].total,431.2);assert.equal(cart.meals[0].regularTotal,440);

 // Changing server prices cannot silently add a meal at a price the customer never saw.
 await card.click();await dialog.getByLabel('Veg Burger',{exact:false}).check();await dialog.getByLabel('Fries',{exact:false}).check();await dialog.getByLabel('Drink',{exact:false}).check();
 const cartBeforePriceReview=await page.evaluate(()=>localStorage.getItem('gokul-cart'));serverVegPrice=120;
 await dialog.getByRole('button',{name:/^Add meal/}).click();await dialog.getByRole('alert').filter({hasText:'Meal price changed'}).waitFor();
 assert.equal(await page.evaluate(()=>localStorage.getItem('gokul-cart')),cartBeforePriceReview);assert.match(await dialog.locator('.menu-sale-price').first().innerText(),/215\.6/);
 serverVegPrice=130;await dialog.getByRole('button',{name:/Confirm price & add meal/}).click();await dialog.locator('.menu-sale-price').filter({hasText:'₹225.4'}).first().waitFor();
 assert.equal(await page.evaluate(()=>localStorage.getItem('gokul-cart')),cartBeforePriceReview);
 assert.match(await dialog.locator('label').filter({hasText:'Veg Burger'}).locator('small').innerText(),/₹130/);await page.screenshot({path:`/tmp/selected-meal-shots/price-review-${width}.png`});
 await dialog.getByRole('button',{name:/Confirm price & add meal/}).click();await dialog.waitFor({state:'hidden'});
 const confirmed=await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-cart')));assert.equal(confirmed.meals.at(-1).total,225.4);
 // A selection change discards the old review; only the new displayed choices can be submitted.
 await card.click();await dialog.getByLabel('Veg Burger',{exact:false}).check();await dialog.getByLabel('Fries',{exact:false}).check();await dialog.getByLabel('Drink',{exact:false}).check();await dialog.getByRole('button',{name:/^Add meal/}).click();await dialog.getByRole('alert').filter({hasText:'Meal price changed'}).waitFor();
 await dialog.getByLabel('Paneer Burger',{exact:false}).check();assert.equal(await dialog.getByRole('alert').filter({hasText:'Meal price changed'}).count(),0);assert.equal(await dialog.getByRole('button',{name:/Confirm price/}).count(),0);await dialog.getByRole('button',{name:'Close',exact:true}).click();await dialog.waitFor({state:'hidden'});serverVegPrice=100;
 await page.goto(base+'/admin/menu/workspace');await page.getByText('App-exclusive meals · prices, discounts & choices',{exact:true}).click();const admin=page.getByRole('region',{name:'Meal management'});await admin.getByRole('button',{name:/Burger Combo/}).click();assert.equal(await admin.getByLabel('Meal pricing',{exact:true}).inputValue(),'SELECTED_ITEMS');assert.equal(await admin.getByLabel('Meal base price ₹',{exact:true}).count(),0);assert.equal(await admin.getByLabel('Upgrade charge ₹',{exact:true}).count(),0);
 await admin.getByLabel('Maximum discount per meal ₹ (optional)',{exact:true}).fill('3');await admin.getByLabel('Meal pricing',{exact:true}).scrollIntoViewIfNeeded();await page.screenshot({path:`/tmp/selected-meal-shots/admin-${width}.png`});await admin.getByRole('button',{name:'Save meal',exact:true}).click();await admin.getByRole('status').filter({hasText:'Meal saved'}).waitFor();assert.equal(writes,1);assert.equal(saved.maximumDiscount,3);
 await page.goto(base+'/menu');await card.waitFor();assert.equal(await card.locator('.menu-sale-price').innerText(),'₹197');assert.equal(await card.locator('.menu-comparison-price').innerText(),'₹200');assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);assert.deepEqual(errors,[]);
 // Disabled alternatives and an unavailable optional group do not block the remaining recipe.
 saved={...saved,groups:saved.groups.map(g=>g.key==='drink'?{...g,minimum:0}:g)};
 unavailableIds=[1,4];await page.reload();await card.waitFor();await card.click();
 await dialog.getByLabel('Veg Burger',{exact:false}).waitFor();assert.equal(await dialog.getByLabel('Veg Burger',{exact:false}).isDisabled(),true);
 assert.equal(await dialog.getByLabel('Drink',{exact:false}).isDisabled(),true);
 await dialog.getByLabel('Paneer Burger',{exact:false}).check();await dialog.getByLabel('Fries',{exact:false}).check();
 assert.equal(await dialog.getByRole('button',{name:/^Add meal/}).isEnabled(),true);
 await dialog.getByRole('button',{name:'Close',exact:true}).click();await dialog.waitFor({state:'hidden'});
 // The only required side is unavailable: retain the inspectable card and disable Add.
 unavailableIds=[1,3,4];await page.reload();await card.waitFor();assert.match(await card.innerText(),/Currently unavailable/);assert.equal(await card.locator(".menu-sale-price").count(),0);await card.click();
 assert.equal(await dialog.getByLabel('Fries',{exact:false}).isDisabled(),true);assert.equal(await dialog.getByRole('button',{name:/^Add meal/}).isDisabled(),true);
 await dialog.getByText('A required group has too few available dishes. Choose another pickup time or try later.',{exact:true}).waitFor();
 await dialog.getByRole('button',{name:'Close',exact:true}).click();await dialog.waitFor({state:'hidden'});
 hideAllProducts=true;await page.reload();await card.waitFor();assert.match(await card.innerText(),/Currently unavailable/);assert.equal(await card.locator(".menu-sale-price").count(),0);await card.click();
 assert.equal(await dialog.getByLabel('Paneer Burger',{exact:false}).isDisabled(),true);await dialog.getByRole('button',{name:'Close',exact:true}).click();await dialog.waitFor({state:'hidden'});hideAllProducts=false;
 // Admin picker must retain disabled dishes even if public discovery has no dishes.
 hideAllProducts=true;await page.goto(base+'/admin/menu/workspace');await page.getByText('App-exclusive meals · prices, discounts & choices',{exact:true}).click();
 await admin.getByRole('button',{name:/Burger Combo/}).click();const firstDish=admin.getByLabel('Dish 1.1',{exact:true});
 assert.match(await firstDish.locator('option[value="1"]').innerText(),/Veg Burger.*Unavailable/);
 assert.match(await firstDish.locator('option[value="2"]').innerText(),/Paneer Burger/);
 await admin.getByRole('button',{name:'Cancel edit',exact:true}).click();hideAllProducts=false;
 // A parked workspace follows IST midnight and also clamps yesterday on restoration.
 await page.clock.install({time:new Date('2026-10-30T18:29:59Z')});
 await page.goto(base+'/admin/menu/workspace');const inventoryDate=page.getByLabel('Inventory date · IST',{exact:false});
 await inventoryDate.waitFor();assert.equal(await inventoryDate.inputValue(),'2026-10-30');await page.clock.fastForward(2000);
 await page.waitForFunction(()=>document.querySelector('input[type="date"]')?.value==='2026-10-31');
 await page.reload();await inventoryDate.waitFor();assert.equal(await inventoryDate.inputValue(),'2026-10-31');
 await inventoryDate.fill('2026-11-02');await page.clock.fastForward(86400000);assert.equal(await inventoryDate.inputValue(),'2026-11-02');
 await context.close();console.log(`PASS selected meal pricing, cap and yellow comparison ${width}px`);
}}finally{await browser.close();}
