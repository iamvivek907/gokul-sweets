import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const browser=await chromium.launch({headless:true}),base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
const branch={id:1,name:'Test branch',active:true,operational:true,pickupAvailable:true};
const product={id:1,name:'Fresh sweet',categoryId:1,categoryName:'Sweets',price:100,saleMode:'UNIT',available:true};
try{for(const mode of ['shared','branch-timeout','menu-timeout','inbox-timeout']){
 const context=await browser.newContext({viewport:{width:390,height:900},serviceWorkers:'block'}),page=await context.newPage();page.setDefaultTimeout(15000);
 let holdBranch=mode!=='menu-timeout',holdMenu=mode==='menu-timeout',holdInbox=mode==='inbox-timeout',releaseBranch,releaseMenu,releaseInbox,branchReads=0;
 const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await context.addInitScript(branch=>localStorage.setItem('gokul-selected-branch',JSON.stringify(branch)),branch);
 const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Methods':'GET,OPTIONS','Access-Control-Allow-Headers':'content-type'};
 await context.route('**/api/**',async route=>{
  const path=new URL(route.request().url()).pathname;let json=[];
  if(route.request().method()==='OPTIONS')return route.fulfill({status:204,headers});
  if(path==='/api/storefront/features')json={futuristicStorefrontV2:true,checkoutExperienceV2:true,simplifiedCheckout:true,acceptedCheckoutQuote:true,notificationInbox:true};
  else if(path==='/api/storefront/customer-identity')json={enabled:true};
  else if(path==='/api/customer/identity/me')json={authenticated:true,name:'Customer',phone:'+919876543210'};
  else if(path==='/api/customer/identity/notification-preferences')json={offerInboxEnabled:false,marketingConsentGranted:false};
  else if(path==='/api/customer/identity/notifications'){
   if(holdInbox)await new Promise(resolve=>releaseInbox=resolve);
   json={messages:[{id:1,eventKey:'paid',kind:'PAYMENT_PAID',targetType:'ORDER',targetId:'ORDER-1',title:'Payment received',message:'Your order is confirmed.',deliveryState:'AVAILABLE',createdAt:new Date().toISOString(),readAt:null}],unreadCount:1,nextBefore:null};
  }
  else if(path==='/api/branches')json=[branch];
  else if(path==='/api/branches/1'){branchReads++;if(holdBranch)await new Promise(resolve=>releaseBranch=resolve);json=branch;}
  else if(path==='/api/menu'){if(holdMenu)await new Promise(resolve=>releaseMenu=resolve);json=[{id:1,name:'Sweets',products:[product]}];}
  else if(path==='/api/menu/portion-groups')json={groups:[]};
  try{return await route.fulfill({headers,json});}catch{/* Timed-out reads are cancelled. */}
 });
 if(mode==='inbox-timeout'){
  await page.goto(`${base}/notifications`);await page.getByText('Loading your inbox…',{exact:true}).waitFor();
  await page.getByRole('alert').filter({hasText:'Your inbox could not load'}).waitFor();assert.equal(await page.getByText('Loading your inbox…',{exact:true}).count(),0);
  holdInbox=false;releaseInbox();await page.getByRole('button',{name:'Try again',exact:true}).click();
  await page.getByRole('heading',{name:'Payment received',exact:true}).waitFor();
  const heading=await page.locator('.notification-page-heading').boundingBox(),card=await page.locator('.notification-group').first().boundingBox();
  for(const width of [320,390]){await page.setViewportSize({width,height:900});const title=await page.locator('.notification-page-heading h1').boundingBox();assert.ok(title.x+title.width<=width,'notification title fits beside icon-only Back');assert.equal(await page.getByRole('link',{name:'Back to previous page',exact:true}).count(),1);}
  console.log('Notification controls height',Math.round(card.y-heading.y));
  await page.screenshot({path:'/tmp/compact-notification-loading-fix.png'});
  assert.ok(card.y-heading.y<300,'notification controls no longer consume most of the first screen');
 }else{
  await page.goto(`${base}/menu`);await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});
  await page.getByText('Loading your page…',{exact:true}).waitFor();
  assert.equal(await page.locator('.customer-site-footer').isVisible().catch(()=>false),false,'footer is not shown below partial loading content');
  assert.equal(await page.getByText('Checking branch availability…',{exact:true}).count(),0);
  if(mode==='shared'){
   await page.locator('.customer-bottom-navigation a[href="/"]').click();await page.waitForURL(base+'/');
   assert.equal(branchReads,1,'navigation shares an in-flight branch check');
   holdBranch=false;releaseBranch();await page.locator('.customer-page-state').waitFor({state:'hidden'});
   await page.locator('.customer-bottom-navigation a[href="/menu"]').click();await page.waitForURL('**/menu');
   await page.getByText('Fresh sweet',{exact:true}).first().waitFor();assert.equal(branchReads,1,'recent branch result and footer reuse avoid repeated route checks');
  }else if(mode==='branch-timeout'){
   await page.getByRole('heading',{name:'Branch availability could not be checked',exact:true}).waitFor();
   holdBranch=false;releaseBranch();await page.getByRole('button',{name:'Try again',exact:true}).click();
   await page.getByText('Fresh sweet',{exact:true}).first().waitFor();
  }else{
   await page.getByRole('heading',{name:'Unable to load menu',exact:true}).waitFor();
   holdMenu=false;releaseMenu();await page.getByRole('button',{name:'Try again',exact:true}).click();
   await page.getByText('Fresh sweet',{exact:true}).first().waitFor();
  }
 }
 assert.deepEqual(errors,[]);assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
 await context.close();console.log(`Bounded page loading ${mode} passed`);
}}finally{await browser.close();}
