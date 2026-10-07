import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {mkdir} from 'node:fs/promises';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const browser=await chromium.launch({headless:true}),base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
const branch={id:1,name:'Gokul Sweets Tamkuhi Road Collection Branch',active:true,operational:true,pickupAvailable:true,address:'Main Road',phone:'9876543210'};
const statuses=['PENDING_PAYMENT','PREPARING','PICKED_UP','CANCELLED'];
const orders=statuses.map((orderStatus,i)=>({orderNumber:`COLOUR-${i}`,customerOrderNumber:i+1,branchId:1,branchName:branch.name,orderStatus,paymentStatus:i===0?'PENDING':i===3?'FAILED':'PAID',fulfillmentType:'PICKUP',totalAmount:100+i*20,createdAt:new Date(Date.now()-i*60000).toISOString(),updatedAt:new Date().toISOString(),pickupDate:'2026-10-06',pickupStartTime:'18:00:00',pickupEndTime:'19:00:00',items:[]}));
try{for(const [width,enabled] of [[320,true],[390,true],[640,true],[1280,true],[390,false]]){
 const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:'block'}),page=await context.newPage();page.setDefaultTimeout(15000);let reads=0;
 const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Methods':'GET,POST,OPTIONS','Access-Control-Allow-Headers':'content-type'};
 const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await context.route('**/api/**',async route=>{const path=new URL(route.request().url()).pathname;let json=[];
  if(route.request().method()==='OPTIONS')return route.fulfill({status:204,headers});
  if(path==='/api/storefront/features')json={futuristicStorefrontV2:enabled,checkoutExperienceV2:enabled,simplifiedCheckout:enabled,acceptedCheckoutQuote:enabled,branchExperience:true,notificationInbox:true,truthfulOrderTracking:true};
  else if(path==='/api/storefront/customer-identity')json={enabled:true};
  else if(path==='/api/customer/identity/me')json={authenticated:true,name:'Vivek Customer',phone:'+919876543210'};
  else if(path==='/api/customer/identity/notifications')json={unreadCount:2,items:[]};
  else if(path==='/api/customer/identity/orders'){reads++;json=orders;}
  else if(path==='/api/branches')json=[branch];else if(path==='/api/branches/1')json=branch;
  else if(path.endsWith('/review'))json={eligible:false,review:null};
  return route.fulfill({headers,json});
 });
 await context.addInitScript(branch=>localStorage.setItem('gokul-selected-branch',JSON.stringify(branch)),branch);
 await page.goto(`${base}/orders`);await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});await page.locator('article').first().waitFor();
 const compact=width<=640&&enabled;
 if(compact){
  assert.equal(await page.getByRole('button',{name:'Refresh',exact:true}).count(),0);
  await page.locator('.customer-notification-bell').waitFor();
  const nav=page.getByRole('navigation',{name:'Primary navigation',exact:true});
  const colours=await nav.locator('a').evaluateAll(nodes=>nodes.map(n=>getComputedStyle(n.querySelector('svg')).stroke));assert.equal(new Set(colours).size,2,'shared navigation uses maroon selection and muted inactive icons');
  assert.equal(await nav.locator('[data-nav-icon=orders]').getAttribute('aria-current'),'page');
  assert.equal(await nav.locator('[aria-current=page]').count(),1);
  const metrics=await nav.locator('a').evaluateAll(nodes=>{
   const luminance=colour=>{const values=colour.match(/\d+(?:\.\d+)?/g).slice(0,3).map(Number).map(v=>{const x=v/255;return x<=.04045?x/12.92:((x+.055)/1.055)**2.4;});return .2126*values[0]+.7152*values[1]+.0722*values[2];};
   const contrast=(a,b)=>{const x=luminance(a),y=luminance(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);};
   return nodes.map(n=>{const label=n.children[1],active=n.getAttribute('aria-current')==='page';return {active,textContrast:contrast(getComputedStyle(label).color,'rgb(255,255,255)'),iconContrast:contrast(getComputedStyle(n.querySelector('svg')).stroke,'rgb(255,255,255)'),weight:Number(getComputedStyle(n).fontWeight),labelClipped:label.scrollWidth>label.clientWidth||getComputedStyle(label).textOverflow==='ellipsis'};});
  });
  for(const m of metrics){assert.ok(m.textContrast>=4.5,'all navigation text retains normal-size contrast');assert.ok(m.iconContrast>=3,'all navigation icons retain contrast');assert.equal(m.labelClipped,false);if(m.active)assert.ok(m.weight>=700,'selection retains bold emphasis as well as aria-current');}

  assert.equal(await page.locator('.gokul-location-pin svg').count(),1);
  assert.equal(await page.locator('.language-trigger svg').count(),1);
  const cards=page.locator('.mobile-order-card');assert.equal(await cards.count(),4);
  assert.equal(new Set(await cards.evaluateAll(nodes=>nodes.map(n=>getComputedStyle(n).borderLeftColor))).size,4);
  const previous=reads;await page.evaluate(()=>document.dispatchEvent(new Event('visibilitychange')));await page.waitForFunction(()=>document.querySelectorAll('.mobile-order-card').length===4);for(let i=0;i<30&&reads===previous;i++)await page.waitForTimeout(50);assert.ok(reads>previous,'visible resume refreshes history without a manual button');
  await page.getByRole('button',{name:'Language / भाषा',exact:true}).click();await page.getByRole('button',{name:/हिन्दी.*अपनी भाषा/}).click();assert.equal(await page.evaluate(()=>document.documentElement.lang),'hi');assert.equal(await nav.locator('a>span:nth-child(2)').evaluateAll(nodes=>nodes.some(n=>n.scrollWidth>n.clientWidth||getComputedStyle(n).textOverflow==='ellipsis')),false,'Hindi navigation labels remain visible');
  await page.getByRole('button',{name:'Language / भाषा',exact:true}).click();await page.getByRole('button',{name:/English.*Order with ease/}).click();
  if(process.env.SCREENSHOT_DIR){await mkdir(process.env.SCREENSHOT_DIR,{recursive:true});await page.screenshot({path:`${process.env.SCREENSHOT_DIR}/colour-orders-${width}.png`,fullPage:true});}
 }else assert.equal(await page.getByRole('button',{name:'Refresh',exact:true}).count(),1,'desktop and flag-OFF refresh retained');
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);assert.deepEqual(errors,[]);
 await page.goto(`${base}/about`);await page.getByRole('contentinfo',{name:'Customer footer'}).waitFor();await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});
 const adminLink=page.getByRole('contentinfo',{name:'Customer footer'}).getByRole('link',{name:'Admin sign in',exact:true});assert.equal(await adminLink.getAttribute('href'),'/admin');assert.equal(await adminLink.evaluate(n=>new URL(n.href).origin),base,'admin entry follows the current deployment domain');
 if(compact){assert.equal(await page.locator('.customer-site-footer').evaluate(n=>getComputedStyle(n).backgroundColor),'rgb(33, 27, 36)','footer follows the compact charcoal palette');assert.equal(await page.locator('.customer-footer-brand-mark').isVisible(),true);if(process.env.SCREENSHOT_DIR)await page.screenshot({path:`${process.env.SCREENSHOT_DIR}/colour-shell-${width}.png`,fullPage:true});}
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);await context.close();console.log(`Colourful shell ${width}px enabled=${enabled} passed`);
}}finally{await browser.close();}
