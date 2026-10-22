import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
const today=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata'}).format(new Date());
const date=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata'}).format(new Date(Date.parse(`${today}T12:00:00+05:30`)+86400000));
const branch={id:1,name:'Gokul Test',active:true,operational:true,pickupAvailable:true};
const product={id:1,name:'Version Laddu',categoryId:1,categoryName:'Sweets',price:40,available:true,saleMode:'UNIT',imageUrl:'/arrival-mithai.webp'};
const other={...product,id:2,name:'Cart Barfi'};
const slot={id:2,branchId:1,slotDate:date,startTime:'12:00:00',endTime:'12:30:00',active:true,remainingCapacity:10,priorityEnabled:false,priorityRemainingCapacity:0};
const browser=await chromium.launch({headless:true});
try {for(const width of [0,120000]) {
 const context=await browser.newContext({viewport:{width:390,height:900},serviceWorkers:'block'}),page=await context.newPage();page.setDefaultTimeout(15000);
 let version='v1',available=true,checks=0; const cutoff=Date.now()+10000; const recheckAt=new Date(cutoff).toISOString();
 await page.clock.install({time:new Date(Date.now()+width)});
 const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Methods':'GET,POST,OPTIONS','Access-Control-Allow-Headers':'content-type,idempotency-key'};
 await context.route('**/api/**',async route=>{
  const request=route.request(),path=new URL(request.url()).pathname;let json=[];
  if(request.method()==='OPTIONS')return route.fulfill({status:204,headers});
  if(path==='/api/storefront/features')json={futuristicStorefrontV2:true,checkoutExperienceV2:true,contextualStorefrontV2:true,smartAvailability:true,today,futureOrderingDays:7};
  else if(path==='/api/branches')json=[branch];else if(path==='/api/branches/1')json=branch;
  else if(path==='/api/menu')json={revision:'catalog',serviceWindowsEnabled:false,observedAt:new Date().toISOString(),items:[{productId:1,available:true},{productId:2,available:true}]};
  else if(path==='/api/menu/catalog/1/catalog')json={revision:'catalog',categories:[{id:1,name:'Sweets',products:[product,other]}]};
  else if(path==='/api/menu/portion-groups')json={groups:[]};
  else if(path==='/api/storefront/customer-identity')json={enabled:false};
  else if(path.endsWith('/menu-state')){json={version};}
  else if(path.endsWith('/availability')){
   checks++; available=Date.now()<cutoff;
   const items=[{productId:1,productName:product.name,available,code:available?null:'QUANTITY_TOO_LARGE',reason:available?null:'No online quantity remains.'},{productId:2,productName:other.name,available:true}];
   json={today,maximumDate:date,recheckAt,observedAt:new Date().toISOString(),dates:[{date,available,items,slots:[{slot,normalAvailable:available,priorityAvailable:false,issues:available?[]:[items[0]]}]}]};
  }
  await route.fulfill({headers,json});
 });
 await context.addInitScript(({branch,date,slot,other})=>{
  localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));
  const pickup=JSON.stringify({date,slot,pickupType:'NORMAL'});
  localStorage.setItem('gokul-selected-pickup-slot',pickup);
  localStorage.setItem('gokul-pickup-intent',JSON.stringify({branchId:1,date}));
  localStorage.setItem('gokul-menu-pickup-mode:v1',JSON.stringify({branchId:1,mode:'fixed',pickup}));
  localStorage.setItem('gokul-cart',JSON.stringify({branchId:1,items:[{product:other,quantity:2,weightGrams:null}]}));
  localStorage.setItem('gokul-social-follow-popup-seen','true');localStorage.setItem('gokul-ordering-tour:v1','seen');
 },{branch,date,slot,other});
 await page.goto(`${base}/menu`);
 const add=page.getByRole('button',{name:'Add Version Laddu to cart',exact:true});
 await add.waitFor(); await page.waitForFunction(()=>document.querySelector('button[aria-label="Add Version Laddu to cart"]')?.disabled===false);
 await page.waitForTimeout(Math.max(0,cutoff-Date.now()+1200));
 console.log(JSON.stringify({clockAheadMs:width,checks,enabledAfterServerCutoff:await add.isEnabled()}));
 assert.equal(await add.isEnabled(),false);
 await context.close();
}}finally{await browser.close();}
