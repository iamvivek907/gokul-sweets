import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {mkdir} from 'node:fs/promises';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const base=process.env.BROWSER_BASE??'http://127.0.0.1:3311',browser=await chromium.launch({headless:true});
const branch={id:1,name:'Meal test branch',active:true,operational:true,pickupAvailable:true};
const products=[{id:1,name:'Main dish',price:100},{id:2,name:'Side dish',price:50},{id:3,name:'Drink',price:40},{id:4,name:'Regular extra',price:20},{id:5,name:'Another dish',price:40}].map(p=>({...p,categoryId:1,categoryName:'Food',available:true,saleMode:'UNIT',description:'Freshly prepared',imageUrl:'/arrival-mithai.webp'}));
const initial={id:1,branchId:1,version:2,title:'Your lunch meal',titleHi:'आपका लंच मील',description:'Pick the combination you want.',basePrice:130,discountType:'FIXED',discountValue:10,estimatedCost:75,active:true,groups:[{key:'main',title:'Choose your main',minimum:1,maximum:1,options:[{key:'main1',productId:1,quantity:1,weightGrams:null,upgrade:0}]},{key:'side',title:'Choose your side',minimum:1,maximum:1,options:[{key:'side1',productId:2,quantity:1,weightGrams:null,upgrade:0}]},{key:'drink',title:'Add a drink',minimum:0,maximum:1,options:[{key:'drink1',productId:3,quantity:1,weightGrams:null,upgrade:20}]}]};
try{for(const width of [320,390,1280]){
 const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:'block',reducedMotion:'reduce'}),page=await context.newPage();page.setDefaultTimeout(15000);const errors=[];page.on('pageerror',e=>errors.push(e.message));let saved=structuredClone(initial),fail=false,writes=0;
 await context.addInitScript(branch=>{localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));localStorage.setItem('gokul-social-follow-popup-seen','true');localStorage.setItem('gokul-ordering-tour:v1','seen');},{...branch});
 const checkoutDate=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata'}).format(new Date(Date.now()+86400000));
 const checkoutSlot={id:9,branchId:1,slotDate:checkoutDate,startTime:'18:00:00',endTime:'19:00:00',active:true,remainingCapacity:50};
 const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Headers':'content-type,x-staff-csrf','Access-Control-Allow-Methods':'GET,POST,PUT,OPTIONS','X-Staff-CSRF':'test-csrf'};
 await context.route('**/api/**',async route=>{
  const request=route.request(),path=new URL(request.url()).pathname;let json=[];
  if(request.method()==='OPTIONS')return route.fulfill({status:204,headers});
  if(path==='/api/storefront/features')json={futuristicStorefrontV2:true,checkoutExperienceV2:true,contextualStorefrontV2:true,simplifiedCheckout:true,today:'2026-10-29',smartAvailability:false};
  else if(path==='/api/branches')json=[branch];else if(path==='/api/branches/1')json=branch;
  else if(path==='/api/storefront/customer-identity')json={enabled:false,guestCheckoutEnabled:true};else if(path==='/api/customer/identity/me')json={authenticated:false};
  else if(path==='/api/menu')json=[{id:1,name:'Food',products}];else if(path==='/api/menu/portion-groups')json={groups:[]};
  else if(path==='/api/branches/1/availability')json={today:checkoutDate,maximumDate:checkoutDate,dates:[{date:checkoutDate,slots:[{slot:checkoutSlot,normalAvailable:true,priorityAvailable:false}]}]};
  else if(path==='/api/branches/1/meals')json=[{...saved,estimatedCost:null}];
  else if(path==='/api/branches/1/meals/quote'){
   const claim=request.postDataJSON();assert.equal(claim.mealId,1);assert.equal(claim.version,2);assert.equal(claim.total,undefined,'client never submits a trusted price');
   if(fail)return route.fulfill({status:409,headers,json:{message:'This meal changed. Rebuild it from the menu.'}});
   const options=saved.groups.flatMap(g=>g.options).filter(o=>claim.optionIds.includes(o.key));const components=options.map(o=>({productId:o.productId,quantity:o.quantity*claim.quantity,weightGrams:null,regularTotal:products.find(p=>p.id===o.productId).price*o.quantity*claim.quantity}));json={mealId:1,version:2,title:saved.title,quantity:claim.quantity,regularTotal:components.reduce((n,p)=>n+p.regularTotal,0),baseTotal:130*claim.quantity,discount:10*claim.quantity,upgrades:options.reduce((n,o)=>n+o.upgrade,0)*claim.quantity,total:(120+options.reduce((n,o)=>n+o.upgrade,0))*claim.quantity,components};
  }
  else if(path==='/api/admin/auth/me')json={staffId:77,username:'owner',fullName:'Test owner',roleName:'OWNER_ADMIN',branchIds:[1],permissions:['MENU_MANAGE','INVENTORY_VIEW','INVENTORY_MANAGE']};
  else if(path==='/api/admin/branches/1/menu/workspace')json={content:[],totalElements:0,page:0,totalPages:0,categories:[],branchCategories:[],taxes:[]};
  else if(path.endsWith('/workspace/groups'))json={version:0,groups:[],total:0,page:0,totalPages:0};
  else if(path==='/api/admin/branches/1/meals')json=[saved];
  else if(path==='/api/admin/branches/1/meals/1'){
   const input=request.postDataJSON();assert.equal(request.method(),'PUT');assert.equal(input.version,2);writes++;
   if(fail)return route.fulfill({status:409,headers,json:{message:'This meal was edited elsewhere. Reload before saving.'}});
   saved={...input,version:3,id:1,branchId:1};json=saved;
  }
  return route.fulfill({headers,json});
 });
 await page.goto(base+'/menu');await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});await page.getByRole('button',{name:'Build meal Your lunch meal'}).click();
 const dialog=page.getByRole('dialog',{name:'Your lunch meal',exact:true});await dialog.waitFor();const add=dialog.getByRole('button',{name:/Add meal/});assert.equal(await add.isDisabled(),true);
 await dialog.getByLabel('Main dish',{exact:false}).check();assert.equal(await add.isDisabled(),true);await dialog.getByLabel('Side dish',{exact:false}).check();await dialog.getByLabel('Drink',{exact:false}).check();assert.match(await add.innerText(),/140/);
 await dialog.getByRole('button',{name:'Increase meal quantity'}).click();assert.match(await add.innerText(),/280/);
 await mkdir('/tmp/gokul-meal-shots',{recursive:true});await page.screenshot({path:`/tmp/gokul-meal-shots/builder-${width}.png`});await add.click();await dialog.waitFor({state:'hidden'});
 let cart=await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-cart')));assert.equal(cart.meals.length,1);assert.equal(cart.meals[0].total,280);assert.equal(cart.items.find(p=>p.product.id===1).quantity,2);
 await page.getByRole('button',{name:'Build meal Your lunch meal'}).click();await dialog.waitFor();await dialog.getByLabel('Main dish',{exact:false}).check();await dialog.getByLabel('Side dish',{exact:false}).check();fail=true;const before=await page.evaluate(()=>localStorage.getItem('gokul-cart'));await dialog.getByRole('button',{name:/Add meal/}).click();await dialog.getByRole('alert').waitFor();assert.equal(await page.evaluate(()=>localStorage.getItem('gokul-cart')),before,'failed quotes do not partially add components');await dialog.getByRole('button',{name:'Close',exact:true}).click();await dialog.waitFor({state:'hidden'});fail=false;
 await page.goto(base+'/cart');await page.getByRole('complementary',{name:'Your meals'}).waitFor();await page.getByRole('link',{name:'Edit meal',exact:true}).click();await dialog.waitFor();assert.equal(await dialog.getByLabel('Drink',{exact:false}).isChecked(),true);await dialog.getByRole('button',{name:'Decrease meal quantity'}).click();await dialog.getByRole('button',{name:/Update meal/}).click();await dialog.waitFor({state:'hidden'});cart=await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-cart')));assert.equal(cart.meals.length,1);assert.equal(cart.meals[0].quantity,1);assert.equal(cart.items.find(p=>p.product.id===1).quantity,1);
 if(width===1280){
  await page.evaluate(({date,slot})=>{localStorage.setItem('gokul-customer-details',JSON.stringify({name:'Meal customer',phone:'9876543210'}));localStorage.setItem('gokul-selected-pickup-slot',JSON.stringify({date,slot,pickupType:'NORMAL'}));},{date:checkoutDate,slot:checkoutSlot});
  await page.goto(base+'/checkout/review');await page.getByRole('complementary',{name:'Your meals'}).waitFor();
  const adjustment=page.getByRole('dialog',{name:'Adjust your pickup here'});
  for(const change of ['pickup-only','extra-main','remove-main']){
   await page.getByRole('button',{name:'Change time',exact:true}).click();await adjustment.waitFor();
   if(change==='extra-main')await adjustment.getByLabel('Main dish quantity',{exact:true}).fill('2');
   if(change==='remove-main')await adjustment.getByRole('button',{name:'Remove Main dish',exact:true}).click();
   await adjustment.getByRole('button',{name:'Check & apply changes',exact:true}).click();await adjustment.waitFor({state:'hidden'});
   const savedCart=await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-cart')));
   if(change==='remove-main'){assert.equal(savedCart.meals,undefined);assert.match(savedCart.mealNotice,/Review your total/);await page.getByRole('status').filter({hasText:'A meal deal was removed'}).waitFor();}
   else{assert.equal(savedCart.meals.length,1,change+' retains the deal');assert.equal(savedCart.meals[0].total,140);assert.equal(savedCart.meals[0].regularTotal,190);}
  }
 }
 await page.goto(base+'/admin/menu/workspace');await page.getByRole('combobox',{name:'Selected branch'}).waitFor();await page.getByText('App-exclusive meals · prices, discounts & choices',{exact:true}).click();const admin=page.getByRole('region',{name:'Meal management'});await admin.getByRole('button',{name:/Your lunch meal/}).click();await admin.getByRole('combobox',{name:/^Discount/}).selectOption('PERCENT');await admin.getByLabel('Discount %',{exact:true}).fill('10');await admin.getByLabel('Estimated base-meal cost',{exact:false}).fill('70');await admin.getByText(/Estimated base margin/).waitFor();await page.screenshot({path:`/tmp/gokul-meal-shots/admin-${width}.png`});fail=true;await admin.getByRole('button',{name:'Save meal',exact:true}).click();await admin.getByRole('alert').waitFor();assert.equal(await admin.getByLabel('Discount %',{exact:true}).inputValue(),'10','conflict retains the admin draft');fail=false;await admin.getByRole('button',{name:'Save meal',exact:true}).click();await admin.getByRole('status').filter({hasText:'Meal saved'}).waitFor();assert.equal(writes,2);assert.equal(saved.discountType,'PERCENT');
 assert.deepEqual(errors,[]);await context.close();console.log(`PASS meals customer/admin ${width}px`);
}}finally{await browser.close();}
