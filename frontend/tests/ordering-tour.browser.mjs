import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {mkdir} from 'node:fs/promises';
const {chromium} = createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE ?? 'playwright');
const browser = await chromium.launch({headless: true}), base = process.env.BROWSER_BASE ?? 'http://127.0.0.1:3311';
const today = new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata'}).format(new Date());
const branch = {id:1,code:'TOUR',name:'Tour branch',active:true,operational:true,pickupAvailable:true};
const slot = {id:1,branchId:1,slotDate:today,startTime:'18:00:00',endTime:'18:30:00',active:true,capacity:10,remainingCapacity:10,bookedCount:0,priorityEnabled:false,priorityRemainingCapacity:0,priorityCharge:0};
const product = {id:1,name:'Samosa',categoryId:1,categoryName:'Snacks',price:20,available:true,saleMode:'UNIT',imageUrl:null};
try {
 for (const width of [320,390,1280]) {
  const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:'block'}),page=await context.newPage();
  await page.clock.install({time:new Date(`${today}T07:00:00+05:30`)});
  const hindi=width===390; const errors=[];page.on('pageerror',e=>errors.push(e.message));const writes=[];
  await context.addInitScript(hindi=>{if(hindi)localStorage.setItem('gokul-language','hi');localStorage.setItem('gokul-social-follow-popup-seen','true');},hindi);
  await context.route('**/api/**',async route=>{
   const req=route.request(),path=new URL(req.url()).pathname;
   const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Headers':'content-type,idempotency-key','Access-Control-Allow-Methods':'GET,POST,OPTIONS'};
   if(req.method()==='OPTIONS')return route.fulfill({status:204,headers});
   // Existing cart previews and recommendations use POST for read-only queries.
   const readOnly=path.endsWith('/availability')||['/api/storefront/vitals','/api/orders/mobile-preview','/api/menu/pickup-addons','/api/menu/pickup-addons/check','/api/reviews/product-summaries'].includes(path);
   if(req.method()!=='GET'&&!readOnly)writes.push(`${req.method()} ${path}`);
   let json=[];
   if(path==='/api/storefront/features')json={futuristicStorefrontV2:true,checkoutExperienceV2:true,contextualStorefrontV2:true,branchExperience:true,smartAvailability:true,today,futureOrderingDays:7};
   else if(path==='/api/storefront/customer-identity')json={enabled:false,guestCheckoutEnabled:true};
   else if(path==='/api/customer/identity/me')json={authenticated:false};
   else if(path==='/api/branches')json=[branch];
   else if(path==='/api/branches/1')json=branch;
   else if(path==='/api/menu')json=[{id:1,name:'Snacks',products:[product]}];
   else if(path==='/api/menu/portion-groups')json={groups:[]};
   else if(path==='/api/branches/1/discovery')json={offerings:[],topRatedItems:[],overallExperience:{average:0,count:0}};
   else if(path.endsWith('/pickup-discovery'))json={today,maximumDate:today,dates:[{date:today,available:true,slots:[{slot,normalAvailable:true,issues:[]}]}]};
   else if(path.endsWith('/availability')) {const date=req.postDataJSON().startDate;json={today,maximumDate:today,dates:[{date,available:true,items:[{productId:1,productName:'Samosa',available:true}],slots:[{slot:{...slot,slotDate:date},normalAvailable:true,issues:[]}]}]};}
   return route.fulfill({json,headers});
  });
  const coach=page.locator('.ordering-tour-coach');
  const stage=async step=>{await page.waitForFunction(step=>JSON.parse(localStorage.getItem('gokul-ordering-walkthrough:v1')??'null')?.step===step,step);await coach.waitFor({state:'visible'});};
  const leaveMenuAndResume=async step=>{
   await page.goto(`${base}/branches/1`);
   const open=coach.getByRole('button',{name:hindi?'मेन्यू खोलें':'Open menu',exact:true});await open.waitFor();
   assert.equal(await open.isEnabled(),true,'guide offers a real route back to Menu');
   await page.reload();await open.waitFor();
   assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-ordering-walkthrough:v1')).step),step,'navigation and refresh retain progress');
   await open.click();await page.waitForURL('**/menu');await stage(step);
  };
  await page.goto(`${base}/branches`);await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});
  await page.getByRole('button',{name:hindi?'मुझे दिखाएँ':'Show me how',exact:true}).click();await stage('branch');
  assert.notEqual(await page.evaluate(()=>document.body.style.overflow),'hidden','walkthrough does not lock browsing');
  assert.equal(await page.locator('.ordering-tour-dialog').count(),0);
  assert.equal(await page.evaluate(()=>localStorage.getItem('gokul-selected-branch')),null,'help does not select a branch');
  await page.locator('.gokul-branch-card-action[data-ordering-target="branch"]').first().click();
  await page.waitForURL('**/branches/1');await stage('menu');
  if(width<=640)await page.locator('.customer-bottom-navigation [data-ordering-target="menu"]').click();
  else await page.locator('.branch-home-actions [data-ordering-target="menu"]').click();
  await page.waitForURL('**/menu');await stage('pickup');
  await page.reload();await stage('pickup');
  await leaveMenuAndResume('pickup');
  assert.equal(await page.locator('.ordering-tour-invite').count(),0,'reload resumes progress, not the invitation');
  await page.locator('[data-ordering-target="pickup"]').click();
  const dialog=page.getByRole('dialog');await dialog.waitFor();await coach.waitFor({state:'hidden'});
  await dialog.getByRole('button',{name:hindi?'रहने दें':'Cancel',exact:true}).click();await stage('pickup');
  await page.locator('[data-ordering-target="pickup"]').click();await dialog.waitFor();
  await dialog.getByRole('button',{name:/18:00–18:30/}).click();
  await dialog.getByRole('button',{name:hindi?'इस पिकअप का उपयोग करें':'Use this pickup',exact:true}).click();
  await dialog.waitFor({state:'hidden'});await stage('add');
  await leaveMenuAndResume('add');
  assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-cart')??'null')?.items?.length??0),0,'picker and guide do not add food');
  await coach.locator('.ordering-tour-primary').click();
  assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-cart')??'null')?.items?.length??0),0,'Show item focuses Add without adding');
  const add=page.locator('[data-ordering-target="add"]:not(:disabled):not([aria-hidden="true"])').first();
  await add.click();await stage('cart');
  assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-cart')).items[0].quantity),1);
  await page.waitForFunction(()=>document.querySelector('[data-ordering-target="cart"]')?.classList.contains('ordering-tour-target'));
  if(process.env.SCREENSHOT_DIR){await mkdir(process.env.SCREENSHOT_DIR,{recursive:true});await page.screenshot({path:`${process.env.SCREENSHOT_DIR}/ordering-tour-interactive-${width}.png`});}
  const bounds=await coach.boundingBox();assert.ok(bounds.x>=0&&bounds.x+bounds.width<=width&&bounds.y>=0&&bounds.y+bounds.height<=900);
  await page.locator('[data-ordering-target="cart"]').click();await page.waitForURL('**/cart');
  await page.waitForFunction(()=>localStorage.getItem('gokul-ordering-walkthrough:v1')===null);
  await page.goto(`${base}/menu`);assert.equal(await coach.count(),0,'completed guide does not restart tomorrow or on navigation');
  await page.getByRole('button',{name:hindi?'ऑर्डर कैसे करें':'How to order',exact:true}).click();await stage('cart');
  await coach.getByRole('button',{name:hindi?'बंद करें':'Close',exact:false}).click();await coach.waitFor({state:'hidden'});
  await page.reload();assert.equal(await coach.count(),0);assert.equal(await page.locator('.ordering-tour-invite').count(),0);
  await page.getByRole('button',{name:hindi?'ऑर्डर कैसे करें':'How to order',exact:true}).click();await stage('cart');
  await page.keyboard.press('Escape');await coach.waitFor({state:'hidden'});
  assert.equal(await page.evaluate(()=>localStorage.getItem('gokul-ordering-walkthrough:v1')),null);
  assert.deepEqual(writes,[],'guide never creates an order, payment or branch write');assert.deepEqual(errors,[]);
  await context.close();console.log(`PASS: interactive ordering walkthrough ${width}px ${hindi?'Hindi':'English'}`);
 }
} finally {await browser.close();}
