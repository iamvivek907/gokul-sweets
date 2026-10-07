import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const browser=await chromium.launch({headless:true}),base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
const date=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata'}).format(new Date(Date.now()+86400000));
const branch={id:1,name:'Gokul-Tamkuhi Road',active:true,operational:true,pickupAvailable:true,address:'Main Road',phone:'9876543210'};
const products=Array.from({length:12},(_,i)=>({id:i+1,name:`Sweet ${i+1}`,categoryId:1,categoryName:'Sweets',available:true,saleMode:'UNIT',price:100,imageUrl:null,description:'Freshly prepared.'}));
try{for(const [width,motion] of [[320,'no-preference'],[390,'no-preference'],[390,'reduce']]){
 const context=await browser.newContext({viewport:{width,height:900},reducedMotion:motion,serviceWorkers:'block'}),page=await context.newPage();page.setDefaultTimeout(15000);const errors=[];page.on('pageerror',e=>errors.push(e.message));
 const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Methods':'GET,POST,OPTIONS','Access-Control-Allow-Headers':'content-type'};
 await context.addInitScript(({branch,date})=>{localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));localStorage.setItem('gokul-pickup-intent',JSON.stringify({branchId:1,date}));},{branch,date});
 await context.route('**/api/**',async route=>{
  const path=new URL(route.request().url()).pathname;let json=[];
  if(route.request().method()==='OPTIONS')return route.fulfill({status:204,headers});
  if(path==='/api/storefront/features')json={futuristicStorefrontV2:true,checkoutExperienceV2:true,contextualStorefrontV2:true,simplifiedCheckout:true,acceptedCheckoutQuote:true,branchExperience:true,notificationInbox:true,occasionEnquiries:true,smartAvailability:true,futureOrderingDays:30,today:date};
  else if(path==='/api/storefront/customer-identity')json={enabled:true};
  else if(path==='/api/customer/identity/me')json={authenticated:true,name:'Test customer',phone:'+919876543210'};
  else if(path==='/api/customer/identity/notifications')json={unreadCount:1,messages:[]};
  else if(path==='/api/branches')json=[branch];else if(path==='/api/branches/1')json=branch;
  else if(path==='/api/branches/1/discovery')json={offerings:[],overallExperience:{average:4.6,count:27},topRatedItems:[]};
  else if(path==='/api/menu')json=[{id:1,name:'Sweets',products}];
  else if(path==='/api/menu/portion-groups')json={groups:[]};
  else if(path==='/api/branches/1/availability')json={today:date,maximumDate:date,dates:[{date,available:false,slots:[],items:products.map(p=>({productId:p.id,available:false,code:'NO_INVENTORY'}))}]};
  return route.fulfill({headers,json});
 });
 await page.goto(`${base}/branches/1`);await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});await page.locator('.branch-home-hero').waitFor();await page.locator('.customer-notification-bell').waitFor();
 const header=page.locator('.customer-site-header');
 assert.ok((await header.boundingBox()).height<=80,'shared phone header is compact');
 assert.equal(await page.locator('.branch-home-back svg').evaluate(n=>getComputedStyle(n).padding),'0px','back arrow retains its visible drawing area');
 for(const node of await header.locator('.customer-header-actions button,.customer-header-actions a,.gokul-location-control').all()){
  if(!await node.isVisible())continue;const box=await node.boundingBox();assert.ok(box.width>=44&&box.height>=44,'header actions retain 44px targets');
 }
 assert.equal(await page.getByRole('link',{name:'Browse menu',exact:false}).filter({hasText:'Browse menu'}).count(),1,'branch home has one primary menu action');
 const tabs=page.getByRole('navigation',{name:'Branch pages'});assert.equal(await tabs.getByRole('button',{name:'Home',exact:true}).isVisible(),false);assert.equal(await tabs.getByRole('link',{name:'Menu',exact:true}).isVisible(),false);
 await tabs.getByRole('button',{name:'Branch details',exact:true}).click();await tabs.getByRole('button',{name:'Home',exact:true}).waitFor();await tabs.getByRole('button',{name:'Home',exact:true}).click();
 await page.getByRole('heading',{name:'How was the Gokul experience?',exact:true}).waitFor();
 await page.screenshot({path:`/tmp/clean-branch-home-${width}-${motion}.png`});
 await page.getByRole('link',{name:'Browse menu',exact:false}).filter({hasText:'Browse menu'}).click();await page.waitForURL('**/menu');await page.locator('#gokul-product-1').waitFor();
 const regular=page.locator('#gokul-product-1');await regular.getByText('Unavailable for selected pickup',{exact:true}).waitFor();assert.equal(await regular.locator('.menu-availability-note').count(),0);assert.equal(await regular.locator('.menu-availability-chip').count(),0);assert.equal(await regular.getByText('Unavailable for selected pickup',{exact:true}).count(),1);
 const nav=page.locator('.customer-bottom-navigation');
 await page.evaluate(()=>{document.activeElement?.blur();document.documentElement.style.scrollBehavior='auto';window.scrollTo({top:450,behavior:'instant'});});
 if(motion==='reduce'){await page.waitForTimeout(100);assert.notEqual(await nav.getAttribute('data-scroll-hidden'),'true');}
 else{
  await page.waitForFunction(()=>document.querySelector('.customer-bottom-navigation')?.dataset.scrollHidden==='true');
  await page.waitForFunction(()=>document.querySelector('.customer-bottom-navigation')?.dataset.scrollHidden==='false');
  await nav.locator('a').first().focus();await page.keyboard.press('Tab');await page.evaluate(()=>window.scrollTo({top:650,behavior:'instant'}));await page.waitForTimeout(50);assert.equal(await nav.getAttribute('data-scroll-hidden'),'false','keyboard navigation keeps the nav accessible');
 }
 await page.evaluate(()=>{document.activeElement?.blur();window.scrollTo({top:0,behavior:'instant'});});await page.waitForTimeout(500);await page.screenshot({path:`/tmp/clean-menu-${width}-${motion}.png`});
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);assert.deepEqual(errors,[]);
 await context.close();console.log(`Clean mobile shell ${width}px ${motion} passed`);
}}finally{await browser.close();}
