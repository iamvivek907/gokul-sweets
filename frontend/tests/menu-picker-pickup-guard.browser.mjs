import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
const date=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata'}).format(new Date(Date.now()+86400000));
const branch={id:1,name:'Gokul Test',active:true,operational:true,pickupAvailable:true};
const products=['Half Paratha','Full Paratha','Breakfast'].map((name,index)=>({id:index+1,name,categoryId:1,categoryName:'Food',price:50+index*40,available:true,saleMode:'UNIT',imageUrl:null}));
const group={key:'paratha',title:'Paratha',choices:[{productId:1,label:'Half'},{productId:2,label:'Full'}]};
const later=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata'}).format(new Date(Date.parse(`${date}T12:00:00+05:30`)+86400000));
const slot={id:1,branchId:1,slotDate:date,startTime:'08:00:00',endTime:'08:30:00',active:true,remainingCapacity:20,priorityEnabled:false};
const browser=await chromium.launch({headless:true});
try{for(const width of [320,390,640]){
 const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:'block'}),page=await context.newPage();page.setDefaultTimeout(15000);
 await page.clock.install({time:new Date(`${date}T07:59:30+05:30`)});
 const errors=[];page.on('pageerror',e=>errors.push(e.message));
 const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Methods':'GET,POST,OPTIONS','Access-Control-Allow-Headers':'content-type,idempotency-key'};
 await context.route('**/api/**',async route=>{
  const request=route.request(),path=new URL(request.url()).pathname;let json=[];
  if(request.method()==='OPTIONS')return route.fulfill({status:204,headers});
  if(path==='/api/storefront/features')json={futuristicStorefrontV2:true,checkoutExperienceV2:true,contextualStorefrontV2:true,smartAvailability:true,today:date,futureOrderingDays:7};
  else if(path==='/api/branches')json=[branch];else if(path==='/api/branches/1')json=branch;
  else if(path==='/api/menu')json=[{id:1,name:'Food',products}];
  else if(path==='/api/menu/portion-groups')json={groups:[group]};
  else if(path==='/api/storefront/customer-identity')json={enabled:false};
  else if(path.endsWith('/pickup-discovery'))json={today:date,maximumDate:later,dates:[date,later].map(value=>({date:value,slots:[{slot:{...slot,id:value===date?1:2,slotDate:value},normalAvailable:true,issues:[]}]}))};
  else if(path.endsWith('/availability')){
   const value=request.postDataJSON().startDate;
   json={today:date,maximumDate:later,dates:[{date:value,available:true,items:products.map(p=>({productId:p.id,available:true})),slots:[{slot:{...slot,id:value===date?1:2,slotDate:value},normalAvailable:true,issues:[]}]}]};
  }
  await route.fulfill({headers,json});
 });
 await context.addInitScript(({branch,date,slot})=>{
  localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));localStorage.setItem('gokul-social-follow-popup-seen','true');
  localStorage.setItem('gokul-pickup-intent',JSON.stringify({branchId:branch.id,date}));const raw=JSON.stringify({date,slot,pickupType:'NORMAL'});localStorage.setItem('gokul-selected-pickup-slot',raw);localStorage.setItem('gokul-menu-pickup-mode:v1',JSON.stringify({branchId:branch.id,mode:'soonest',pickup:raw}));localStorage.setItem('gokul-ordering-tour:v1','seen');
 },{branch,date,slot});
 await page.goto(`${base}/menu`);await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});
 const card=page.locator('.mobile-portion-card').filter({has:page.getByRole('heading',{name:'Paratha',exact:true})});
 await page.waitForFunction(()=>document.querySelector('button[aria-label="Choose options for Paratha"]')?.disabled===false);
 await card.getByRole('button',{name:'Choose options for Paratha',exact:true}).click();
 const dialog=page.getByRole('dialog',{name:'Paratha',exact:true});
 assert.equal(await dialog.getByRole('button',{name:'Add Paratha Full to cart',exact:true}).isEnabled(),true);
 await page.clock.fastForward(31000);
 await page.waitForFunction(date=>JSON.parse(localStorage.getItem('gokul-selected-pickup-slot'))?.date===date,later);
 await dialog.getByRole('alert').filter({hasText:'Pickup changed or expired'}).waitFor();
 assert.equal(await dialog.getByRole('button',{name:'Add Paratha Full to cart',exact:true}).isDisabled(),true);
 assert.equal(await page.evaluate(()=>localStorage.getItem('gokul-cart')),null,'an open size popup cannot silently adopt tomorrow');
 await dialog.getByRole('button',{name:'Done',exact:true}).click();
 await page.getByRole('region',{name:'Menu pickup time'}).getByText(/Tomorrow, /).waitFor();
 await page.waitForFunction(()=>document.querySelector('button[aria-label="Choose options for Paratha"]')?.disabled===false);
 await card.getByRole('button',{name:'Choose options for Paratha',exact:true}).click();
 await dialog.getByRole('button',{name:'Add Paratha Full to cart',exact:true}).click();
 await page.waitForFunction(()=>JSON.parse(localStorage.getItem('gokul-cart'))?.items[0]?.product.id===2);
 assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-selected-pickup-slot')).date),later);
 await dialog.getByRole('button',{name:'Done',exact:true}).click();
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);assert.deepEqual(errors,[]);
 await context.close();console.log(`Grouped-size popup rejects unseen automatic pickup rollover ${width}px passed`);
}}finally{await browser.close();}
