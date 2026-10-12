import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
const today=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata'}).format(new Date());
const tomorrow=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata'}).format(new Date(Date.parse(`${today}T12:00:00+05:30`)+86400000));
const branch={id:1,name:'Test',active:true,operational:true,pickupAvailable:true};
const products=[1,2].map(id=>({id,name:`Meal ${id}`,categoryId:1,categoryName:'Food',price:100,available:true,saleMode:'UNIT',imageUrl:null}));
const slot=(date,id,time)=>({id,branchId:1,slotDate:date,startTime:time,endTime:'23:59:00',active:true,remainingCapacity:20,priorityEnabled:false});
const original={date:today,slot:slot(today,1,'23:55:00'),pickupType:'NORMAL'};
const cart={branchId:1,items:[{product:products[0],quantity:2,weightGrams:null}]};
const browser=await chromium.launch({headless:true});
try{for(const width of [320,390,1280]){
 const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:'block'}),page=await context.newPage();page.setDefaultTimeout(15000);
 let fail=false,previewFail=false,cartChecks=0,holdConfirm=false,releaseConfirm,holdLoading=false;const loadGates=[];const errors=[];page.on('pageerror',e=>errors.push(e.message));
 const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Methods':'GET,POST,OPTIONS','Access-Control-Allow-Headers':'content-type,idempotency-key'};
 await context.route('**/api/**',async route=>{
  const request=route.request(),url=new URL(request.url()),path=url.pathname;let json=[];
  if(request.method()==='OPTIONS')return route.fulfill({status:204,headers});
  if(path==='/api/storefront/features')json={futuristicStorefrontV2:true,checkoutExperienceV2:true,contextualStorefrontV2:true,smartAvailability:true,today,futureOrderingDays:7};
  else if(path==='/api/branches')json=[branch];else if(path==='/api/branches/1')json=branch;
  else if(path==='/api/menu')json=[{id:1,name:'Food',products}];
  else if(path==='/api/menu/portion-groups')json={groups:[]};
  else if(path==='/api/storefront/customer-identity')json={enabled:false};
  else if(path.endsWith('/pickup-discovery'))json={today,maximumDate:tomorrow,dates:[{date:tomorrow,slots:[{slot:slot(tomorrow,2,'12:00:00'),normalAvailable:true,issues:[]}]}]};
  else if(path.endsWith('/availability')){
   const body=request.postDataJSON(),strict=!url.searchParams.has('menuPreview');
   if(!strict&&previewFail)return route.fulfill({status:503,headers,json:{message:'Offline'}});
   if(strict){if(holdLoading&&body.days>1)await new Promise(resolve=>loadGates.push(resolve));if(holdConfirm&&body.days===1){holdConfirm=false;await new Promise(resolve=>releaseConfirm=resolve);}cartChecks++;assert.deepEqual(body.items,[{productId:1,quantity:2,weightGrams:null}]);if(fail&&body.days===1)return route.fulfill({status:503,headers,json:{message:'Offline'}});}
   const issue={productId:1,productName:'Meal 1',available:false,code:'NOT_READY',reason:'Ready tomorrow at noon.'};
   const day=(date,valid)=>({date,available:valid,reason:valid?null:issue.reason,items:products.map(p=>({productId:p.id,available:valid,reason:valid?null:issue.reason,code:valid?null:'NOT_READY'})),slots:[{slot:slot(date,date===today?1:2,date===today?'23:55:00':'12:00:00'),normalAvailable:valid,priorityAvailable:false,issues:valid?[]:[issue]}]});
   json={today,maximumDate:tomorrow,dates:strict&&body.days>1?[day(today,false),day(tomorrow,true)]:[day(body.startDate,body.startDate===tomorrow)]};
  }
  await route.fulfill({headers,json});
 });
 await context.addInitScript(({branch,cart,original})=>{
  localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));localStorage.setItem('gokul-cart',JSON.stringify(cart));
  if(!sessionStorage.getItem('review-seeded')){localStorage.setItem('gokul-selected-pickup-slot',JSON.stringify(original));localStorage.setItem('gokul-pickup-intent',JSON.stringify({branchId:1,date:original.date}));sessionStorage.setItem('review-seeded','true');}
  localStorage.setItem('gokul-ordering-tour:v1','seen');localStorage.setItem('gokul-social-follow-popup-seen','true');
 },{branch,cart,original});
 await page.goto(`${base}/menu`);await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});
 const pickup=page.getByRole('region',{name:'Menu pickup time'}),dialog=page.getByRole('dialog',{name:'Choose pickup date & time'});
 await pickup.getByRole('button',{name:'Change time',exact:true}).click();
 await dialog.getByRole('button',{name:tomorrow,exact:true}).waitFor();
 assert.equal(await dialog.getByRole('button',{name:tomorrow,exact:true}).getAttribute('aria-pressed'),'true');
 assert.equal(await dialog.locator('.gokul-pickup-time').count(),1);
 const box=await dialog.boundingBox(),actions=await dialog.locator('.mobile-pickup-dialog-actions').boundingBox();
 assert.ok(box.width<=680&&box.height<=720,'picker stays bounded on desktop and phones');
 assert.ok(actions.y+actions.height<=900,'confirmation remains in view');
 if(width===1280)await page.screenshot({path:'/tmp/gokul-pickup-dialog-desktop.png'});
 await dialog.getByRole('button',{name:'Cancel',exact:true}).click();
 assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-selected-pickup-slot'))),original);
 // Cancel during initial loading, reopen, then release the obsolete request.
 holdLoading=true;await pickup.getByRole('button',{name:'Change time',exact:true}).click();
 await dialog.getByRole('status').filter({hasText:'Checking times…'}).waitFor();
 for(let i=0;i<100&&loadGates.length<1;i++)await page.waitForTimeout(10);assert.equal(loadGates.length,1);
 await dialog.getByRole('button',{name:'Close pickup selector'}).click();await dialog.waitFor({state:'hidden'});
 await pickup.getByRole('button',{name:'Change time',exact:true}).click();
 await dialog.getByRole('status').filter({hasText:'Checking times…'}).waitFor();
 for(let i=0;i<100&&loadGates.length<2;i++)await page.waitForTimeout(10);
 assert.equal(loadGates.length,2);loadGates[0]();await page.waitForTimeout(100);
 assert.equal(await dialog.isVisible(),true);await dialog.getByRole('status').filter({hasText:'Checking times…'}).waitFor();
 holdLoading=false;loadGates[1]();await dialog.locator('.gokul-pickup-time').waitFor();
 await dialog.getByRole('button',{name:'Cancel',exact:true}).click();
 await pickup.getByRole('button',{name:'Change time',exact:true}).click();await dialog.locator('.gokul-pickup-time').click();fail=true;
 await dialog.getByRole('button',{name:'Use this pickup',exact:true}).click();await dialog.getByRole('alert').waitFor();
 assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-selected-pickup-slot'))),original);
 fail=false;holdConfirm=true;await dialog.getByRole('button',{name:'Use this pickup',exact:true}).click();
 await dialog.getByRole('button',{name:'Checking pickup…',exact:true}).waitFor();
 assert.equal(await dialog.getByRole('button',{name:'Close pickup selector'}).isDisabled(),true);
 assert.equal(await dialog.getByRole('button',{name:'Cancel',exact:true}).isDisabled(),true);
 await page.keyboard.press('Escape');await page.mouse.click(2,2);assert.equal(await dialog.isVisible(),true);
 while(!releaseConfirm)await page.waitForTimeout(10);releaseConfirm();await dialog.waitFor({state:'hidden'});
 assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-selected-pickup-slot')).date),tomorrow);
 assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-cart'))),cart);
 assert.ok(cartChecks>=4);assert.equal(await page.getByRole('region',{name:'Other menu items'}).count(),0);
 previewFail=true;await page.reload();
 await page.locator('#gokul-product-2 .menu-availability-note').getByText('Pickup check failed. Please retry.',{exact:true}).waitFor();
 previewFail=false;
 await page.evaluate(()=>{localStorage.removeItem('gokul-selected-pickup-slot');localStorage.setItem('gokul-language','en');});
 await page.reload();
 await pickup.getByText(/Tomorrow/).waitFor();await page.waitForFunction(()=>document.querySelector('button[aria-label="Add Meal 2 to cart"]')?.disabled===false);
 assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-cart'))),cart);
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);assert.deepEqual(errors,[]);
 await context.close();console.log(`Cart pickup ${width}px passed`);
}}finally{await browser.close();}
