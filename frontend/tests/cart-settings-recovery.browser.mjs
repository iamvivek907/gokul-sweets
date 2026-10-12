import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const browser=await chromium.launch({headless:true}),base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
try{for(const scenario of ['outage','offline']){
 const context=await browser.newContext({viewport:{width:390,height:900},serviceWorkers:'block'}),page=await context.newPage();
 const branch={id:1,code:'TEST',name:'Test branch',active:true},cart={branchId:1,items:[{product:{id:1,name:'Milk sweet',price:100,saleMode:'UNIT',available:true},quantity:1,weightGrams:null}]};
 let failing=scenario==='outage',checks=0,creates=0;
 const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Methods':'GET,POST,OPTIONS','Access-Control-Allow-Headers':'content-type'};
 await context.route('**/api/**',async route=>{
  const req=route.request(),path=new URL(req.url()).pathname;if(req.method()==='OPTIONS')return route.fulfill({status:204,headers});
  let json=[];
  if(path==='/api/storefront/features'){checks++;if(failing)return route.fulfill({status:503,json:{message:'Service unavailable'},headers});json={checkoutExperienceV2:true,futuristicStorefrontV2:true,simplifiedCheckout:true,acceptedCheckoutQuote:true};}
  else if(path==='/api/branches')json=[branch];else if(path==='/api/storefront/customer-identity')json={enabled:true,guestCheckoutEnabled:false};
  else if(path==='/api/customer/identity/me')json={authenticated:false};else if(path.endsWith('/availability'))json={dates:[]};
  if(req.method()==='POST'&&['/api/orders','/api/payments'].includes(path))creates++;
  return route.fulfill({json,headers});
 });
 await context.addInitScript(({branch,cart})=>{localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));localStorage.setItem('gokul-cart',JSON.stringify(cart));},{branch,cart});
 if(scenario==='offline')await context.addInitScript(()=>{window.testOnline=false;Object.defineProperty(navigator,'onLine',{get:()=>window.testOnline});});
 await page.goto(`${base}/cart`);await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});
 await page.getByRole('heading',{name:'Online ordering is taking longer to connect',exact:true}).waitFor();
 assert.equal(await page.getByRole('link',{name:'Back to menu',exact:true}).isVisible(),true);assert.equal(await page.evaluate(()=>localStorage.getItem('gokul-cart')),JSON.stringify(cart));
 if(scenario==='offline'){await page.getByText('You’re offline. Your cart is saved. Reconnect to continue.',{exact:true}).waitFor();assert.equal(checks,0);assert.equal(await page.getByRole('button',{name:'Try again',exact:true}).isDisabled(),true);await page.evaluate(()=>{window.testOnline=true;window.dispatchEvent(new Event('online'));});}
 else {failing=false;await page.getByRole('button',{name:'Try again',exact:true}).click();}await page.waitForURL('**/checkout/mobile');await page.getByRole('heading',{name:'Your order',exact:true}).waitFor();assert.ok(checks>=(scenario==='offline'?1:2));assert.equal(creates,0);
 assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-cart')).items.length),1);
 await context.close();console.log(`PASS: ${scenario} phone cart settings preserve items and recover compact routing.`);
}}finally{await browser.close();}
