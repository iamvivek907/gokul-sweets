import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {mkdir} from 'node:fs/promises';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const browser=await chromium.launch({headless:true});
const base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
try {
 for(const width of [390,1440]) {
  const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:'block',reducedMotion:'reduce'});
  const page=await context.newPage();await page.clock.install();
  let unavailable=true,reads=0,writes=0;
  const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true'};
  await context.route('**/api/**',async route=>{
   const req=route.request(),p=new URL(req.url()).pathname;
   if(req.method()==='OPTIONS')return route.fulfill({status:204,headers:{...headers,'Access-Control-Allow-Methods':'GET,POST,PUT,DELETE,OPTIONS','Access-Control-Allow-Headers':'content-type'}});
   if(req.method()!=='GET')writes++;
   if(p==='/api/storefront/features') {reads++;return route.fulfill({status:unavailable?503:200,json:unavailable?{}:{customerHomeV2:false,preHomeIntentGateway:false,futuristicStorefrontV2:false,checkoutExperienceV2:false,today:'2026-10-01',futureOrderingDays:30},headers});}
   return route.fulfill({json:[],headers});
  });
  await page.goto(base);await page.getByRole('heading',{name:'A little sweetness is on its way.'}).waitFor();
  assert.equal(await page.getByText("We couldn't refresh ordering settings. Your cart is saved.",{exact:true}).count(),0);
  await page.evaluate(()=>localStorage.setItem('gokul-reconnect-cart-sentinel','retained'));
  await page.clock.fastForward(2600);await page.getByText('Online ordering is taking longer to connect.',{exact:true}).waitFor();
  assert.equal(await page.locator('a[href="/about#privacy-policy"]').count(),1);
  await page.getByRole('link',{name:'Explore Gokul',exact:true}).click();await page.getByRole('heading',{name:'Good moments start with Gokul.'}).waitFor();
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
  if(process.env.SCREENSHOT_DIR){await mkdir(process.env.SCREENSHOT_DIR,{recursive:true});await page.evaluate(()=>scrollTo(0,0));await page.screenshot({path:`${process.env.SCREENSHOT_DIR}/reconnect-${width}.png`,fullPage:true});}
  const before=reads;await page.clock.fastForward(300000);await page.waitForTimeout(100);assert.ok(reads<=before+2,'a long wait must not burst requests');
  unavailable=false;await page.clock.fastForward(16000);await page.getByRole('heading',{name:/Your favourites, ready when you are/}).waitFor();
  assert.equal(await page.evaluate(()=>localStorage.getItem('gokul-reconnect-cart-sentinel')),'retained');assert.equal(writes,0);
  // A real uncontrolled customer input remains mounted through failed settings and recovery.
  await page.goto(`${base}/careers`);
  if(width<=640){await page.locator('.gokul-mobile-launch').waitFor({state:'visible'});await page.clock.fastForward(2600);await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});}
  await page.getByLabel('Full name').fill('Preserved customer input');
  unavailable=true;await page.clock.fastForward(61000);await page.getByText('Online ordering is taking longer to connect.',{exact:true}).waitFor();assert.equal(await page.getByLabel('Full name').inputValue(),'Preserved customer input');
  unavailable=false;await page.getByRole('button',{name:'Try again',exact:true}).click();await page.getByText('Online ordering is taking longer to connect.',{exact:true}).waitFor({state:'hidden'});assert.equal(await page.getByLabel('Full name').inputValue(),'Preserved customer input');
  await context.close();
 }
 console.log('PASS: mobile/desktop branded cold start, bounded retries, automatic recovery, cart retention, no writes, and mounted customer input retention.');
}finally {await browser.close();}
