import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const browser=await chromium.launch({headless:true}),base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
try {
 for(const width of [320,390,1280]) {
  const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:'block',reducedMotion:'reduce'}),page=await context.newPage();
  const errors=[];page.on('pageerror',error=>errors.push(error.message));
  await context.addInitScript(()=>{localStorage.setItem('gokul-ordering-tour:v1','seen');localStorage.setItem('gokul-social-follow-popup-seen','true');});
  await context.route('**/api/**',async route=>{
   const path=new URL(route.request().url()).pathname;
   const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Headers':'content-type','Access-Control-Allow-Methods':'GET,POST,OPTIONS'};
   if(route.request().method()==='OPTIONS')return route.fulfill({status:204,headers});
   let json=[];
   if(path==='/api/storefront/features')json={preHomeIntentGateway:true,futuristicStorefrontV2:true,checkoutExperienceV2:true,contextualStorefrontV2:true,branchExperience:true};
   else if(path==='/api/storefront/customer-identity')json={enabled:false,guestCheckoutEnabled:true};
   else if(path==='/api/customer/identity/me')json={authenticated:false};
   else if(path==='/api/branches')json=[1,2,3].map(id=>({id,name:`Test branch ${id}`,code:`TEST${id}`,active:true,operational:true,pickupAvailable:true,address:'Test pickup address'}));
   return route.fulfill({json,headers});
  });
  const links=page.getByRole('link',{name:'Order food',exact:true});
  const atBranches=()=>page.waitForFunction(()=>{const rect=document.getElementById('gokul-branches')?.getBoundingClientRect();return rect&&scrollY>100&&rect.top<innerHeight*.6&&rect.bottom>0;});
  await page.goto(base);await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});await links.last().click();
  await page.waitForURL('**/branches');await atBranches();
  const historyLength=await page.evaluate(()=>history.length);
  let checked=0;
  for(const link of await links.all()) {
   if(!await link.isVisible())continue;
   for(let attempt=0;attempt<2;attempt++) {
    await page.evaluate(()=>window.scrollTo({top:0,behavior:'instant'}));await page.waitForFunction(()=>scrollY===0);
    await link.click();await atBranches();
    assert.equal(new URL(page.url()).pathname,'/branches');
    assert.equal(await page.evaluate(()=>history.length),historyLength,'repeat clicks scroll without extra history entries');
   }
   checked++;
  }
  assert.ok(checked>0);if(width===1280)assert.equal(checked,2,'both navigation and hero actions work');
  assert.equal(await page.evaluate(()=>localStorage.getItem('gokul-selected-branch')),null,'scrolling never chooses a branch');
  assert.deepEqual(errors,[]);await context.close();console.log(`PASS: repeat Order food navigation at ${width}px`);
 }
} finally {await browser.close();}
