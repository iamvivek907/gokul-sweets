import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const browser=await chromium.launch({headless:true}),base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
const branch={id:1,name:'Gokul Sweets Test Branch',active:true,operational:true,pickupAvailable:true};
try {for(const reducedMotion of ['no-preference','reduce']) {
 const context=await browser.newContext({viewport:{width:390,height:900},serviceWorkers:'block',reducedMotion}),page=await context.newPage();
 const errors=[];page.on('pageerror',error=>errors.push(error.message));let reads=0,menuReads=0,release;
 const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true'};
 await context.addInitScript(branch=>localStorage.setItem('gokul-selected-branch',JSON.stringify(branch)),branch);
 await context.route('**/api/**',async route=>{const path=new URL(route.request().url()).pathname;let json=[];
  if(path==='/api/storefront/features')json={futuristicStorefrontV2:true,branchExperience:true};
  else if(path==='/api/storefront/customer-identity')json={enabled:true};
  else if(path==='/api/customer/identity/me')json={authenticated:false};
  else if(path==='/api/branches')json=[branch];
  else if(path==='/api/branches/1'){reads++;json=branch;}
  return route.fulfill({headers,json});
 });
 // Hold the route payload before it can be prefetched, so navigation is genuinely pending.
 await context.route('**/menu?*',async route=>{
  if(route.request().headers().rsc==='1'){menuReads++;await new Promise(resolve=>{release=resolve;});}
  await route.continue();
 });
 await page.goto(`${base}/branches/1`);await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});
 const home=page.locator('[data-nav-icon=home]'),menu=page.locator('[data-nav-icon=menu]');
 await home.waitFor();
 const before={header:await page.locator('.customer-site-header').boundingBox(),nav:await page.locator('.customer-bottom-navigation').boundingBox()};
 await page.evaluate(()=>scrollTo({top:100,behavior:'instant'}));const scroll=await page.evaluate(()=>scrollY),count=reads;
 for(let i=0;i<8;i++)await home.click();
 await page.waitForTimeout(500);
 assert.equal(new URL(page.url()).pathname,'/branches/1');assert.equal(reads,count,'current Home does not reload data');
 assert.equal(await page.evaluate(()=>scrollY),scroll,'current Home does not reset scroll');
 assert.equal(await page.locator('.navigation-feedback').count(),0,'current page taps never show loading');
 await page.locator('[data-nav-icon=orders]').evaluate(anchor=>anchor.addEventListener('click',event=>event.preventDefault(),{once:true}));
 await page.locator('[data-nav-icon=orders]').click();await page.waitForTimeout(500);
 assert.equal(await page.locator('.navigation-feedback').count(),0,'cancelled navigation does not show feedback');
 await menu.click();await page.locator('.navigation-feedback').waitFor();
 assert.equal((await page.locator('.navigation-feedback').boundingBox()).height,3);
 assert.deepEqual(await page.locator('.customer-bottom-navigation').boundingBox(),before.nav,'navigation never changes height');
 assert.equal(await menu.getByText('Opening…',{exact:true}).count(),0);
 assert.equal(await home.getByText('Opening…',{exact:true}).count(),0);
 const pendingRequests=menuReads;
 for(let i=0;i<5;i++)await menu.click();
 assert.equal(menuReads,pendingRequests,'repeat taps do not restart pending navigation');
 assert.ok(release,'held route demonstrates actual pending navigation');release();
 await page.waitForURL('**/menu');await page.locator('.navigation-feedback').waitFor({state:'hidden'});
 // Overlapping Link completion events cannot hide a newer navigation.
 await page.evaluate(()=>{window.dispatchEvent(new CustomEvent('gokul-navigation-start',{detail:{token:'old'}}));window.dispatchEvent(new CustomEvent('gokul-navigation-start',{detail:{token:'new'}}));window.dispatchEvent(new CustomEvent('gokul-navigation-end',{detail:{token:'old'}}));});
 await page.locator('.navigation-feedback').waitFor();
 if(reducedMotion==='reduce')assert.equal(await page.locator('.navigation-progress').evaluate(n=>getComputedStyle(n).animationName),'none');
 await page.evaluate(()=>window.dispatchEvent(new CustomEvent('gokul-navigation-end',{detail:{token:'new'}})));
 await page.locator('.navigation-feedback').waitFor({state:'hidden'});
 assert.deepEqual(errors,[]);await context.close();console.log(`Navigation feedback ${reducedMotion} passed`);
}}finally{await browser.close();}
