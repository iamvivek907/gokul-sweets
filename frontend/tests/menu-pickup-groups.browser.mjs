import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
const date=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata'}).format(new Date(Date.now()+86400000));
const branch={id:1,name:'Gokul Test',active:true,operational:true,pickupAvailable:true};
const products=['Half Paratha','Full Paratha','Breakfast'].map((name,index)=>({id:index+1,name,categoryId:1,categoryName:'Food',price:50+index*40,available:true,saleMode:'UNIT',imageUrl:null}));
const group={key:'paratha',title:'Paratha',choices:[{productId:1,label:'Half'},{productId:2,label:'Full'}]};
const slot={id:1,branchId:1,slotDate:date,startTime:'08:00:00',endTime:'08:30:00',active:true,remainingCapacity:20,priorityEnabled:false};
const browser=await chromium.launch({headless:true});
try{for(const width of [320,390,640]){
 const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:'block'}),page=await context.newPage();page.setDefaultTimeout(15000);
 let mixed=false;const errors=[];page.on('pageerror',e=>errors.push(e.message));
 const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Methods':'GET,POST,OPTIONS','Access-Control-Allow-Headers':'content-type,idempotency-key'};
 await context.route('**/api/**',async route=>{
  const request=route.request(),path=new URL(request.url()).pathname;let json=[];
  if(request.method()==='OPTIONS')return route.fulfill({status:204,headers});
  if(path==='/api/storefront/features')json={futuristicStorefrontV2:true,checkoutExperienceV2:true,contextualStorefrontV2:true,smartAvailability:true,today:date,futureOrderingDays:7};
  else if(path==='/api/branches')json=[branch];else if(path==='/api/branches/1')json=branch;
  else if(path==='/api/menu')json=[{id:1,name:'Food',products}];
  else if(path==='/api/menu/portion-groups')json={groups:[group]};
  else if(path==='/api/storefront/customer-identity')json={enabled:false};
  else if(path.endsWith('/availability')){
   const issues=products.filter(p=>mixed?p.id!==2:p.id!==3).map(p=>({productId:p.id,productName:p.name,available:false,code:'OUTSIDE_SERVICE',reason:p.id===1?'Half service starts at 11 AM.':p.id===2?'Full service starts at noon.':'Breakfast service ends at 9:30 AM.'}));
   json={today:date,maximumDate:date,dates:[{date,available:true,items:products.map(p=>({productId:p.id,available:true})),slots:[{slot,normalAvailable:false,issues}]}]};
  }
  await route.fulfill({headers,json});
 });
 await context.addInitScript(({branch,date,slot})=>{
  localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));localStorage.setItem('gokul-social-follow-popup-seen','true');
  localStorage.setItem('gokul-pickup-intent',JSON.stringify({branchId:branch.id,date}));localStorage.setItem('gokul-selected-pickup-slot',JSON.stringify({date,slot,pickupType:'NORMAL'}));
 },{branch,date,slot});
 await page.goto(`${base}/menu`);await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});
 const card=page.locator('.mobile-portion-card').filter({has:page.getByRole('heading',{name:'Paratha',exact:true})});
 await card.getByText('Half service starts at 11 AM.',{exact:false}).waitFor();
 await card.getByText('Full service starts at noon.',{exact:false}).waitFor();
 assert.equal(await card.getByRole('button',{name:'Choose options for Paratha',exact:true}).isDisabled(),true);
 assert.equal(await page.getByRole('region',{name:'Other menu items'}).locator('.mobile-portion-card').count(),1);
 mixed=true;await page.reload();await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});
 await page.waitForFunction(()=>document.querySelector('button[aria-label="Choose options for Paratha"]')?.disabled===false);
 assert.equal(await page.getByRole('region',{name:'Available for selected pickup'}).locator('.mobile-portion-card').count(),1);
 await page.getByLabel('Find a favourite',{exact:true}).fill('Half');
 await page.waitForFunction(()=>!document.querySelector('section[aria-label="Other menu items"]'));
 assert.equal(await card.count(),1,'filtered group is kept once, with its full catalogue of sizes');
 await card.getByRole('button',{name:'Choose options for Paratha',exact:true}).click();
 const dialog=page.getByRole('dialog',{name:'Paratha',exact:true});
 assert.equal(await dialog.getByRole('button',{name:'Add Paratha Half to cart',exact:true}).isDisabled(),true);
 assert.equal(await dialog.getByRole('button',{name:'Add Paratha Full to cart',exact:true}).isEnabled(),true);
 await dialog.getByRole('button',{name:'Done',exact:true}).click();
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);assert.deepEqual(errors,[]);
 await context.close();console.log(`Grouped service reasons and search eligibility ${width}px passed`);
}}finally{await browser.close();}
