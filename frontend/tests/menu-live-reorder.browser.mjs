import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
const browser=await chromium.launch({headless:true});
const date=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata'}).format(new Date(Date.now()+86400000));
const branch={id:1,name:'Test branch',active:true,operational:true,pickupAvailable:true};
const products=Array.from({length:7},(_,i)=>({id:i+1,name:`Sweet ${i+1}`,categoryId:1,categoryName:'Sweets',price:15,available:true,saleMode:'UNIT'}));
const slot={id:1,branchId:1,slotDate:date,startTime:'18:00:00',endTime:'19:00:00',active:true,remainingCapacity:20,priorityEnabled:false};
try{
 const context=await browser.newContext({viewport:{width:390,height:900},serviceWorkers:'block',reducedMotion:'reduce'});
 const page=await context.newPage();page.setDefaultTimeout(15000);
 let blocked=false,addChecks=0;
 const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Methods':'GET,POST,OPTIONS','Access-Control-Allow-Headers':'content-type'};
 await context.route('**/api/**',async route=>{
  const request=route.request(),path=new URL(request.url()).pathname;
  if(request.method()==='OPTIONS')return route.fulfill({status:204,headers});
  let json=[];
  if(path==='/api/storefront/features')json={futuristicStorefrontV2:true,checkoutExperienceV2:true,contextualStorefrontV2:true,smartAvailability:true,pickupAddOns:true,today:date,futureOrderingDays:30};
  else if(path==='/api/branches')json=[branch];
  else if(path==='/api/branches/1')json=branch;
  else if(path==='/api/menu')json=[{id:1,name:'Sweets',products}];
  else if(path==='/api/menu/portion-groups')json={groups:[]};
  else if(path==='/api/menu/appearance')json={banners:[],categories:[],pairings:[{seedProductId:1,suggestedProductId:2}]};
  else if(path==='/api/menu/pickup-addons/check'){addChecks++;json={orderable:!blocked};}
  else if(path.endsWith('/availability')){
   const issues=blocked?[{productId:1,available:false,code:'NO_INVENTORY'}]:[];
   json={today:date,maximumDate:date,dates:[{date,available:!blocked,items:products.map(p=>({productId:p.id,available:true})),slots:[{slot,normalAvailable:!blocked,priorityAvailable:false,issues}]}]};
  }
  await route.fulfill({json,headers}).catch(()=>{});
 });
 await context.addInitScript(({branch,slot,date,product})=>{
  localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));
  localStorage.setItem('gokul-selected-pickup-slot',JSON.stringify({date,slot,pickupType:'NORMAL'}));
  localStorage.setItem('gokul-pickup-intent',JSON.stringify({branchId:branch.id,date}));
  localStorage.setItem('gokul-cart',JSON.stringify({branchId:branch.id,items:[{product,quantity:1}]}));
  localStorage.setItem('gokul-ordering-tour:v1','seen');
 },{branch,slot,date,product:products[0]});
 await page.goto(base+'/menu');await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});
 const addPairing=page.getByRole('button',{name:'Add Sweet 2 from pairings',exact:true});
 await addPairing.waitFor();assert.equal(await addPairing.isEnabled(),true);
 const card=page.locator('#gokul-product-5'),node=await card.elementHandle();
 await card.getByRole('button',{name:'View details · Sweet 5',exact:true}).click();
 const details=page.getByRole('dialog',{name:'Sweet 5',exact:true});await details.waitFor();
 blocked=true;await page.evaluate(()=>window.dispatchEvent(new Event('online')));
 await page.waitForFunction(()=>document.querySelector('#gokul-product-5').compareDocumentPosition(document.querySelector('#gokul-product-1'))&Node.DOCUMENT_POSITION_FOLLOWING);
 assert.equal(await details.isVisible(),true,'reordering across the fourth row keeps details open');
 assert.equal(await node.evaluate(card=>card.isConnected),true,'reordering keeps the original card');
 await details.getByRole('button',{name:'Close',exact:true}).click();await details.waitFor({state:'hidden'});
 assert.equal(await card.getByRole('button',{name:'Add Sweet 5 to cart',exact:true}).isEnabled(),true);
 await addPairing.waitFor({state:'hidden'});
 assert.equal(addChecks,0,'the known basket conflict is handled before Add');
 blocked=false;await page.evaluate(()=>window.dispatchEvent(new Event('online')));
 await addPairing.waitFor();assert.equal(await addPairing.isEnabled(),true,'pairing returns after the basket fits again');
 const first=page.locator('#gokul-product-1');
 await first.getByRole('button',{name:'View details · Sweet 1',exact:true}).click();
 const firstDetails=page.getByRole('dialog',{name:'Sweet 1',exact:true});await firstDetails.waitFor();
 blocked=true;await page.evaluate(()=>window.dispatchEvent(new Event('online')));
 await page.waitForFunction(()=>document.querySelector('#gokul-product-5').compareDocumentPosition(document.querySelector('#gokul-product-1'))&Node.DOCUMENT_POSITION_FOLLOWING);
 assert.equal(await firstDetails.evaluate(dialog=>dialog.matches(':modal')),true,'moving the open card itself preserves its native modal');
 await firstDetails.getByRole('button',{name:'Close',exact:true}).click();await firstDetails.waitFor({state:'hidden'});
 await context.close();
 console.log('Stock reordering preserves the open dish, and pairings respect the selected pickup for the basket.');
}finally{await browser.close();}
