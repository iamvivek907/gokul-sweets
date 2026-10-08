import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {mkdir} from 'node:fs/promises';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
const screenshots=process.env.SCREENSHOT_DIR??'/tmp/gokul-soonest-pickup';
const today=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata'}).format(new Date());
const nextDate=date=>new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata'}).format(new Date(Date.parse(`${date}T12:00:00+05:30`)+86400000));
const tomorrow=nextDate(today),later=nextDate(tomorrow);
const branch={id:1,name:'Gokul Test',active:true,operational:true,pickupAvailable:true};
const product={id:1,name:'Aloo Paratha',categoryId:1,categoryName:'Breakfast',price:80,available:true,saleMode:'UNIT',imageUrl:'/arrival-mithai.webp'};
const slot=(date,id,time='08:00:00')=>({id,branchId:1,slotDate:date,startTime:time,endTime:'09:30:00',active:true,remainingCapacity:10,priorityEnabled:false,priorityRemainingCapacity:0});
const browser=await chromium.launch({headless:true});
try{for(const width of [320,390,1280]){
 const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:'block'}),page=await context.newPage();page.setDefaultTimeout(15000);
 await page.clock.install({time:new Date(`${today}T22:00:00+05:30`)});
 let outage=false,hold=false,release=null;const checks=[],errors=[];page.on('pageerror',e=>errors.push(e.message));
 const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Methods':'GET,POST,OPTIONS','Access-Control-Allow-Headers':'content-type,idempotency-key'};
 await context.route('**/api/**',async route=>{
  const request=route.request(),path=new URL(request.url()).pathname;let json=[];
  if(request.method()==='OPTIONS')return route.fulfill({status:204,headers});
  if(path==='/api/storefront/features')json={futuristicStorefrontV2:true,checkoutExperienceV2:true,contextualStorefrontV2:true,smartAvailability:true,today,futureOrderingDays:7};
  else if(path==='/api/branches')json=[branch];else if(path==='/api/branches/1')json=branch;
  else if(path==='/api/menu')json=[{id:1,name:'Breakfast',products:[product]}];
  else if(path==='/api/menu/portion-groups')json={groups:[]};
  else if(path==='/api/storefront/customer-identity')json={enabled:false};
  else if(path.endsWith('/pickup-discovery')){
   json={today,maximumDate:later,dates:[today,tomorrow,later].map((date,index)=>({date,slots:[{slot:slot(date,index+1),normalAvailable:true,issues:[]}]}))};
  }else if(path.endsWith('/availability')){
   const date=request.postDataJSON().startDate;checks.push(date);
   if(hold){hold=false;await new Promise(resolve=>{release=resolve;});}
   if(outage)return route.fulfill({status:503,headers,json:{message:'Offline'}});
   json={today,maximumDate:later,dates:[{date,available:true,items:[{productId:1,productName:product.name,available:true}],slots:[{slot:slot(date,[today,tomorrow,later].indexOf(date)+1),normalAvailable:true,issues:[]}]}]};
  }
  await route.fulfill({headers,json});
 });
 await context.addInitScript(branch=>{if(!localStorage.getItem('gokul-selected-branch'))localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));localStorage.setItem('gokul-social-follow-popup-seen','true');localStorage.setItem('gokul-ordering-tour:v1','dismissed');},branch);
 const pickup=page.getByRole('region',{name:'Menu pickup time'}),add=page.getByRole('button',{name:'Add Aloo Paratha to cart'});
 const saved=()=>page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-selected-pickup-slot')));
 await page.goto(`${base}/menu`);await pickup.getByText(/Tomorrow, .*8:00/).waitFor();await add.waitFor();await page.waitForFunction(()=>!document.querySelector('button[aria-label="Add Aloo Paratha to cart"]')?.disabled);
 assert.equal((await saved()).date,tomorrow);assert.equal((await saved()).pickupType,'NORMAL');assert.equal(checks.includes(today),false,'closed slots are skipped before item queries');
 await mkdir(screenshots,{recursive:true});await page.screenshot({path:`${screenshots}/soonest-tomorrow-${width}.png`,fullPage:true});
 await page.reload();await pickup.getByText(/Tomorrow, .*8:00/).waitFor();assert.equal((await saved()).date,tomorrow);
 await pickup.getByRole('button',{name:'Change time',exact:true}).click();
 const dialog=page.getByRole('dialog',{name:'Choose pickup date & time'});
 await dialog.getByRole('button',{name:'Cancel',exact:true}).click();
 assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-menu-pickup-mode:v1')).mode),'soonest','cancelling the picker preserves automatic mode');
 // At midnight tomorrow's valid first slot stays put, now labelled Today with the actual date.
 await page.clock.fastForward(2*60*60*1000+1000);await pickup.getByText(/Today, .*8:00/).waitFor();assert.equal((await saved()).date,tomorrow);
 // An expired automatic slot advances while empty, including to a future date.
 await page.clock.fastForward(8*60*60*1000);await page.waitForFunction(date=>JSON.parse(localStorage.getItem('gokul-selected-pickup-slot'))?.date===date,later);
 await pickup.getByText(/Tomorrow, .*8:00/).waitFor();await page.waitForFunction(()=>document.querySelector('button[aria-label="Add Aloo Paratha to cart"]')?.disabled===false);
 // First Add fixes pickup; clearing it and refresh retain fixed mode.
 await add.click();assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-menu-pickup-mode:v1')).mode),'fixed');
 await page.getByRole('button',{name:'Remove one Aloo Paratha',exact:true}).click();await page.reload();await pickup.getByText(/Tomorrow, .*8:00/).waitFor();
 assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-menu-pickup-mode:v1')).mode),'fixed');
 await page.clock.fastForward(24*60*60*1000);await pickup.getByText(/Your previous pickup has passed/).waitFor();assert.equal((await saved()).date,later);
 // A failed future-date verification never gets presented as no slots.
 await page.clock.setSystemTime(new Date(`${today}T22:00:00+05:30`));
 await page.evaluate(()=>{for(const key of ['gokul-menu-pickup-mode:v1','gokul-selected-pickup-slot','gokul-pickup-intent','gokul-cart'])localStorage.removeItem(key);});outage=true;
 await page.reload();await pickup.getByRole('button',{name:'Retry availability'}).waitFor();assert.equal(await saved(),null);
 assert.equal(await pickup.getByText(/No verified normal pickup/).count(),0);
 outage=false;await pickup.getByRole('button',{name:'Retry availability'}).click();await pickup.getByText(/Tomorrow, .*8:00/).waitFor();
 await pickup.getByRole('button',{name:'Change time',exact:true}).click();
 await dialog.getByRole('button',{name:'Use this pickup',exact:true}).click();await dialog.waitFor({state:'hidden'});
 assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-menu-pickup-mode:v1')).mode),'fixed','explicit confirmation fixes the same pickup too');
 // Late automatic responses cannot overwrite a manual/date-only choice from another tab.
 await page.evaluate(()=>{for(const key of ['gokul-menu-pickup-mode:v1','gokul-selected-pickup-slot','gokul-pickup-intent'])localStorage.removeItem(key);});hold=true;
 await page.reload();await page.waitForFunction(()=>document.querySelector('.mobile-menu-pickup')?.textContent?.includes('Finding'));
 const deadline=Date.now()+15000;while(!release&&Date.now()<deadline)await page.waitForTimeout(50);assert.ok(release,"automatic verification started");
 await page.evaluate(date=>{localStorage.setItem('gokul-pickup-intent',JSON.stringify({branchId:1,date}));localStorage.setItem('gokul-menu-pickup-mode:v1',JSON.stringify({branchId:1,mode:'fixed',pickup:''}));window.dispatchEvent(new Event('storage'));},later);
 release();await pickup.getByText(/Time not selected/).waitFor();await page.waitForTimeout(600);assert.equal(await saved(),null);
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);assert.deepEqual(errors,[]);
 await context.close();console.log(`Soonest pickup future dates, midnight, expiry, cart lock, failure and stale response ${width}px passed`);
}}finally{await browser.close();}
