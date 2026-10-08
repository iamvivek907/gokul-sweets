import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {mkdir} from 'node:fs/promises';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
const screenshots=process.env.SCREENSHOT_DIR??'/tmp/gokul-pickup-default';
const today=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata'}).format(new Date());
const yesterday=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata'}).format(new Date(Date.parse(`${today}T12:00:00+05:30`)-86400000));
const branch={id:1,name:'Gokul Tamkuhi Road',active:true,operational:true,pickupAvailable:true};
const product=(id,name,categoryId,categoryName)=>({id,name,categoryId,categoryName,price:80,available:true,saleMode:'UNIT',imageUrl:'/arrival-mithai.webp'});
const products=[product(1,'Veg Chowmein',1,'Food'),product(2,'Aloo Paratha',2,'Breakfast'),product(3,'Gulab Jamun',3,'Sweets')];
const slot=(id,time,date=today)=>({id,branchId:1,slotDate:date,startTime:time,endTime:time==='08:00:00'?'08:30:00':'11:30:00',active:true,capacity:10,bookedCount:0,remainingCapacity:10,priorityEnabled:false,priorityRemainingCapacity:0,priorityCharge:20});
const breakfast=slot(8,'08:00:00'),lunch=slot(11,'11:00:00');
const browser=await chromium.launch({headless:true});
try {for(const width of [320,390,640,1280]) {
 const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:'block'}),page=await context.newPage();page.setDefaultTimeout(15000);
 await page.clock.install({time:new Date(`${today}T00:01:00+05:30`)});
 let mode='normal',revision='1',checks=0;const errors=[];page.on('pageerror',e=>errors.push(e.message));
 const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Methods':'GET,POST,OPTIONS','Access-Control-Allow-Headers':'content-type,idempotency-key'};
 await context.route('**/api/**',async route=>{
  const request=route.request(),path=new URL(request.url()).pathname;let json=[];
  if(request.method()==='OPTIONS')return route.fulfill({status:204,headers});
  if(path==='/api/storefront/features')json={futuristicStorefrontV2:true,checkoutExperienceV2:true,contextualStorefrontV2:true,smartAvailability:true,today,futureOrderingDays:7};
  else if(path==='/api/branches')json=[branch];else if(path==='/api/branches/1')json=branch;
  else if(path==='/api/menu')json=[{id:1,name:'Food',products:[products[0]]},{id:2,name:'Breakfast',products:[products[1]]},{id:3,name:'Sweets',products:[products[2]]}].map(c=>({...c,products:c.products.map(p=>({...p,availabilityRevision:revision}))}));
  else if(path==='/api/menu/portion-groups')json={groups:[]};
  else if(path==='/api/storefront/customer-identity')json={enabled:false};
  else if(path.endsWith('/pickup-discovery'))json={today,maximumDate:today,dates:[{date:today,available:true,slots:[{slot:breakfast,normalAvailable:true,issues:[]},{slot:lunch,normalAvailable:true,issues:[]}]}]};
  else if(path.endsWith('/availability')){
   checks++;if(mode==='outage')return route.fulfill({status:503,headers,json:{message:'Unavailable'}});
   const date=request.postDataJSON().startDate;
   const slots=[breakfast,lunch].map(original=>{
    const checked={...original,slotDate:date};
    if(mode==='full'||mode==='priority'){checked.remainingCapacity=0;checked.priorityEnabled=mode==='priority';checked.priorityRemainingCapacity=mode==='priority'?3:0;}
    const issues=mode==='all'?[]:products.filter(p=>original.id===8?p.id===1:p.id!==1).map(p=>({productId:p.id,productName:p.name,available:false,code:'OUTSIDE_SERVICE',reason:original.id===8?'Available for pickup from 11 AM.':'Breakfast service ends at 9:30 AM.'}));
    return {slot:checked,normalAvailable:!issues.length,priorityAvailable:false,code:mode==='closed'?'PICKUP_WINDOW':null,reason:mode==='closed'?'Booking cutoff passed.':null,issues};
   });
   json={today,maximumDate:today,dates:[{date,available:true,items:products.map(p=>({productId:p.id,productName:p.name,available:true})),slots}]};
  }
  await route.fulfill({headers,json});
 });
 // Seed once: reload must exercise actual persistence rather than re-seeding the fixture.
 await page.addInitScript(branch=>{if(!localStorage.getItem('gokul-selected-branch'))localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));localStorage.setItem('gokul-social-follow-popup-seen','true');},branch);
 const load=async()=>{await page.goto(`${base}/menu`);await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});};
 const pickup=page.getByRole('region',{name:'Menu pickup time'});
 await load();await pickup.getByText(/Today, .* · 8:00/).waitFor();
 await page.getByRole('heading',{name:'Available for your 8:00 am pickup',exact:false}).waitFor();
 const breakfastSection=page.getByRole('region',{name:'Available for selected pickup'});
 assert.equal(await breakfastSection.getByRole('heading',{name:'Aloo Paratha',exact:true}).count(),1,'All is the default, even though Food is first in the catalog');
 assert.equal(await breakfastSection.getByRole('button',{name:'Add Aloo Paratha to cart'}).isEnabled(),true);
 assert.equal(await page.getByRole('button',{name:'Add Veg Chowmein to cart'}).isDisabled(),true);
 await page.getByText('Available for pickup from 11 AM.',{exact:true}).waitFor();
 const first=await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-selected-pickup-slot')));assert.equal(first.slot.id,8);assert.equal(first.date,today);assert.equal(first.pickupType,'NORMAL');
 await mkdir(screenshots,{recursive:true});await page.screenshot({path:`${screenshots}/pickup-breakfast-${width}.png`,fullPage:true});
 await page.reload();await pickup.getByText(/Today, .* · 8:00/).waitFor();assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-selected-pickup-slot'))),first);
 // A mixed breakfast/lunch cart cannot silently move its pickup or discard its items.
 await breakfastSection.getByRole('button',{name:'Add Aloo Paratha to cart'}).click();
 await pickup.getByRole('button',{name:'Change time',exact:true}).click();const dialog=page.getByRole('dialog',{name:'Choose pickup date & time'});
 await dialog.getByRole('button',{name:/11:00–11:30/}).click();await dialog.getByRole('button',{name:'Use this pickup',exact:true}).click();
 await dialog.getByRole('alert').filter({hasText:'Aloo Paratha'}).waitFor();
 assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-selected-pickup-slot')).slot.id),8);
 assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-cart')).items[0].quantity),1);
 await dialog.getByRole('button',{name:'Cancel',exact:true}).click();
 await breakfastSection.getByRole('button',{name:'Remove one Aloo Paratha',exact:true}).click();
 // Explicitly choose lunch after resolving the incompatible cart.
 await pickup.getByRole('button',{name:'Change time',exact:true}).click();
 await dialog.getByRole('button',{name:/11:00–11:30/}).click();await dialog.getByRole('button',{name:'Use this pickup',exact:true}).click();await dialog.waitFor({state:'hidden'});
 await pickup.getByText(/Today, .* · 11:00/).waitFor();await page.waitForFunction(()=>!document.querySelector('button[aria-label="Add Veg Chowmein to cart"]')?.disabled);
 assert.equal(await page.getByRole('button',{name:'Add Aloo Paratha to cart'}).isDisabled(),true);
 // All eligible restores the current category presentation without extra availability sections.
 mode='all';revision='2';await page.clock.fastForward(31000);await breakfastSection.waitFor({state:'hidden'});await page.waitForFunction(()=>document.querySelector('button[aria-label="Add Aloo Paratha to cart"]')?.disabled===false);
 assert.equal(await page.getByRole('button',{name:'Add Aloo Paratha to cart'}).isEnabled(),true);
 // Capacity refresh blocks Add but never moves the retained pickup.
 mode='full';revision='3';await page.clock.fastForward(61000);await page.waitForFunction(()=>document.querySelector('button[aria-label="Add Veg Chowmein to cart"]')?.disabled===true);
 assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-selected-pickup-slot')).slot.id),11);
 // Closed day: no automatic tomorrow. Priority-only times must not incur an automatic fee.
 for(const state of ['closed','priority','outage']){
  mode=state;await page.evaluate(()=>{localStorage.removeItem('gokul-menu-pickup-mode:v1');localStorage.removeItem('gokul-pickup-intent');localStorage.removeItem('gokul-selected-pickup-slot');localStorage.removeItem('gokul-cart');});await load();
  if(state==='closed')await pickup.getByText(/No verified normal pickup/).waitFor();
  if(state==='priority')await pickup.getByText(/No verified normal pickup/).waitFor();
  if(state==='outage')await pickup.getByRole('button',{name:'Retry availability'}).waitFor();
  assert.equal(await page.evaluate(()=>localStorage.getItem('gokul-selected-pickup-slot')),null);
  assert.equal(await page.getByRole('button',{name:'Add Aloo Paratha to cart'}).isDisabled(),true);
 }
 mode='normal';await pickup.getByRole('button',{name:'Retry availability'}).click();await pickup.getByText(/Today, .* · 8:00/).waitFor();
 // Empty old-day visits may default today; an old cart and unfinished order must be preserved.
 for(const retain of [false,true]){
  await page.evaluate(({yesterday,breakfast,product,retain})=>{
   localStorage.setItem('gokul-pickup-intent',JSON.stringify({branchId:1,date:yesterday}));localStorage.setItem('gokul-selected-pickup-slot',JSON.stringify({date:yesterday,slot:{...breakfast,slotDate:yesterday},pickupType:'NORMAL'}));
   localStorage.setItem('gokul-menu-pickup-mode:v1',JSON.stringify({branchId:1,mode:'soonest',pickup:localStorage.getItem('gokul-selected-pickup-slot')}));
   if(retain)localStorage.setItem('gokul-cart',JSON.stringify({branchId:1,items:[{product,quantity:1,weightGrams:null}]}));else localStorage.removeItem('gokul-cart');
  },{yesterday,breakfast,product:products[1],retain});await load();
  if(!retain)await pickup.getByText(/Today, .* · 8:00/).waitFor();else {await pickup.getByText(/Your previous pickup has passed/).waitFor();assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-selected-pickup-slot')).date),yesterday);assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-cart')).items[0].quantity),1);}
 }
 // An uncertain checkout attempt prevents automatic pickup replacement even with an empty cart.
 await page.evaluate(()=>{localStorage.removeItem('gokul-cart');localStorage.removeItem('gokul-menu-pickup-mode:v1');localStorage.removeItem('gokul-pickup-intent');localStorage.removeItem('gokul-selected-pickup-slot');sessionStorage.setItem('gokul-mobile-order-attempt','pending');});
 await load();await page.waitForFunction(()=>document.querySelector('button[aria-label="Add Aloo Paratha to cart"]')?.disabled===true);
 await page.waitForTimeout(800);assert.equal(await page.evaluate(()=>localStorage.getItem('gokul-selected-pickup-slot')),null);
 assert.equal(await page.evaluate(()=>sessionStorage.getItem('gokul-mobile-order-attempt')),'pending');
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);assert.deepEqual(errors,[]);
 await context.close();console.log(`Pickup-aware default, All view, service grouping, refresh, closing, priority, outage and midnight recovery ${width}px passed (${checks} checks)`);
}}finally{await browser.close();}
