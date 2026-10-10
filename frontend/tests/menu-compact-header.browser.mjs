import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {mkdir} from 'node:fs/promises';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const base=process.env.BROWSER_BASE??'http://127.0.0.1:3311',screens=process.env.SCREENSHOT_DIR??'/tmp/gokul-compact-header';
const date=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata'}).format(new Date(Date.now()+86400000));
const branch={id:1,name:'Main branch',active:true,operational:true,pickupAvailable:true};
const products=Array.from({length:30},(_,i)=>({id:i+1,name:i===0?'Rasgulla':`Sweet ${i+1}`,categoryId:1,categoryName:'Sweets',price:15,available:true,saleMode:'UNIT',imageUrl:'/arrival-mithai.webp'}));
const slot={id:1,branchId:1,slotDate:date,startTime:'18:00:00',endTime:'19:00:00',active:true,remainingCapacity:20,priorityEnabled:false};
const isPickerRequest=request=>request.method()!=='OPTIONS'&&(new URL(request.url()).pathname.endsWith('/pickup-discovery')||(new URL(request.url()).pathname.endsWith('/availability')&&!request.url().includes('menuPreview=true')));
const browser=await chromium.launch({headless:true});
try{for(const [width,enabled,expired=false,reduced=true,cold=false] of [[320,true],[390,true],[640,true],[1280,true],[390,false],[390,true,true],[390,true,false,false],[390,true,false,false,true]]){
 const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:'block',reducedMotion:reduced?'reduce':'no-preference'}),page=await context.newPage();page.setDefaultTimeout(15000);let discoveryFails=false,previewFails=false,previewReads=0,pickerGate=null;const errors=[];page.on('pageerror',e=>errors.push(e.message));
 let releaseChunk,notifyChunk;const chunkGate=new Promise(resolve=>{releaseChunk=resolve;}),chunkRequested=new Promise(resolve=>{notifyChunk=resolve;});
 if(cold)await context.route('**/_next/static/chunks/*.js',async route=>{const response=await route.fetch();const body=await response.text();if(body.includes('mobile-pickup-title')){notifyChunk();await chunkGate;}await route.fulfill({response,body});});
 const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Methods':'GET,POST,OPTIONS','Access-Control-Allow-Headers':'content-type'};
 await context.route('**/api/**',async route=>{
  const path=new URL(route.request().url()).pathname;let json=[];if(path.endsWith('/availability')&&route.request().url().includes('menuPreview=true')){previewReads++;if(previewFails)return route.fulfill({status:503,json:{message:'Temporary outage'},headers});}
  if(route.request().method()==='OPTIONS')return route.fulfill({status:204,headers});
  if(isPickerRequest(route.request())&&pickerGate)await pickerGate;
  if(path.endsWith('/pickup-discovery')&&discoveryFails)return route.fulfill({status:503,json:{message:'Temporary outage'},headers});
  if(path==='/api/storefront/features')json={futuristicStorefrontV2:enabled,checkoutExperienceV2:enabled,contextualStorefrontV2:true,smartAvailability:true,today:date,futureOrderingDays:30};
  else if(path==='/api/branches')json=[branch];else if(path==='/api/branches/1')json=branch;
  else if(path==='/api/menu')json=[{id:1,name:'Sweets',products}];
  else if(path==='/api/menu/portion-groups')json={groups:[]};
  else if(path==='/api/storefront/customer-identity')json={enabled:false};
  else if(path.endsWith('/availability')||path.endsWith('/pickup-discovery'))json={today:date,maximumDate:date,dates:[{date,available:true,items:products.map(p=>({productId:p.id,available:true})),slots:[{slot,normalAvailable:true,priorityAvailable:false,issues:[]}]}]};
  await route.fulfill({json,headers}).catch(()=>{});
 });
 await context.addInitScript(({branch,date,slot,expired,product})=>{localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));localStorage.setItem('gokul-social-follow-popup-seen','true');localStorage.setItem('gokul-pickup-intent',JSON.stringify({branchId:1,date}));localStorage.setItem('gokul-selected-pickup-slot',JSON.stringify({date:expired?'2026-01-01':date,slot:expired?{...slot,slotDate:'2026-01-01'}:slot,pickupType:'NORMAL'}));if(expired)localStorage.setItem('gokul-cart',JSON.stringify({branchId:1,items:[{product,quantity:1}]}));},{branch,date,slot,expired,product:products[0]});
 await page.goto(`${base}/menu`);await page.locator('#gokul-product-1').waitFor();await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});
 const header=page.locator('.customer-site-header'),original=await header.boundingBox();
 await page.evaluate(()=>window.scrollTo({top:900,behavior:'instant'}));
 if(width>640||!enabled){await page.waitForTimeout(100);assert.equal(await page.locator('.menu-compact-header:visible').count(),0);assert.equal(await header.locator('[data-header-content]').getAttribute('aria-hidden'),null);await context.close();console.log(`Compact header scope preserved ${width}px enabled=${enabled}`);continue;}
 await page.locator('.menu-compact-header[aria-hidden=false]').waitFor();
 if(expired)assert.match(await page.locator('.menu-compact-pickup').innerText(),/Choose pickup date & time/);
 assert.equal(await header.locator('[data-header-content]').getAttribute('aria-hidden'),'true');
 assert.equal(await header.locator('[data-header-content]').evaluate(n=>n.inert),true,'hidden header controls cannot receive keyboard focus');
 assert.ok((await page.locator('.mobile-menu-sticky-tools').boundingBox()).y<0,'search and filters scroll away');
 for(const top of [1000,1100,1099,1101,1200,1400]){await page.evaluate(top=>window.scrollTo({top,behavior:'instant'}),top);await page.evaluate(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))));const box=await header.boundingBox();assert.ok(Math.abs(box.y)<1&&Math.abs(box.height-original.height)<1,'header does not move or resize during scroll');assert.equal(await page.locator('.menu-compact-header').count(),1);}
 await mkdir(screens,{recursive:true});await page.screenshot({path:`${screens}/compact-pickup-${width}.png`});
 if(width===320){discoveryFails=true;await page.locator('.menu-compact-pickup').click();await page.locator('.menu-compact-pickup [role=alert]').waitFor();assert.match(await page.locator('.menu-compact-pickup').innerText(),/Tap to retry/);discoveryFails=false;}
 if(!reduced){
  assert.notEqual(await page.locator('.menu-compact-header').evaluate(n=>getComputedStyle(n).transitionDuration),'0s','normal motion has a transition');
  const boundary=await page.locator('.mobile-menu-pickup').evaluate(n=>n.getBoundingClientRect().bottom+scrollY-document.querySelector('.customer-site-header').getBoundingClientRect().bottom+12);
  for(const top of [boundary+2,boundary-5,boundary+3,boundary-6]){await page.evaluate(top=>scrollTo({top,behavior:'instant'}),top);await page.evaluate(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))));assert.equal(await header.getAttribute('data-menu-compact'),'','threshold jitter cannot toggle the header');assert.ok(Math.abs((await header.boundingBox()).height-original.height)<1);}
  await page.evaluate(()=>scrollTo({top:1400,behavior:'instant'}));await page.waitForTimeout(250);
  previewFails=true;await page.evaluate(()=>window.dispatchEvent(new Event('online')));await page.locator('.menu-compact-pickup [role=alert]').waitFor();
  const before=previewReads;previewFails=false;const retryResponse=page.waitForResponse(response=>response.url().includes('/availability?menuPreview=true'));await page.locator('.menu-compact-pickup').click();await retryResponse;await page.waitForFunction(()=>!document.querySelector('.menu-compact-pickup [role=alert]'));assert.ok(previewReads>before,'retry repeats authoritative menu availability');assert.equal(await page.locator('.mobile-pickup-dialog').count(),0,'availability retry does not open an unrelated picker');
 }
 let scroll=await page.evaluate(()=>scrollY);
 const saved=await page.evaluate(()=>localStorage.getItem('gokul-selected-pickup-slot'));
 // Measure after retry text removal and browser scroll anchoring, while the request is held.
 let releaseInitial;pickerGate=new Promise(resolve=>{releaseInitial=resolve;});const initialRequest=page.waitForRequest(isPickerRequest);
 await page.locator('.menu-compact-pickup').click();await initialRequest;await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));scroll=await page.evaluate(()=>scrollY);releaseInitial();pickerGate=null;const picker=page.getByRole('dialog',{name:'Choose pickup date & time',exact:true});
 if(cold){await chunkRequested;assert.ok((await page.locator('#gokul-product-1').boundingBox()).width>0,'cold dialog download never hides the menu');assert.equal(await page.locator('.menu-compact-pickup').isDisabled(),true,'pending dialog cannot be opened twice');await page.evaluate(()=>scrollTo({top:2500,behavior:'instant'}));scroll=await page.evaluate(()=>scrollY);releaseChunk();}
 await picker.waitFor();if(cold)assert.ok(Math.abs((await page.evaluate(()=>scrollY))-scroll)<2,'cold dialog opening keeps the latest scroll');await picker.getByRole('button',{name:'Close pickup selector',exact:true}).click();assert.equal(await page.evaluate(()=>localStorage.getItem('gokul-selected-pickup-slot')),saved,'cancelling compact pickup preserves selection');
 await picker.waitFor({state:'hidden'});assert.equal(await page.locator('.menu-compact-pickup').evaluate(n=>document.activeElement===n),true,'closing pickup restores the opener without scrolling');await page.waitForFunction(top=>Math.abs(scrollY-top)<24,scroll);
 assert.ok(Math.abs((await page.evaluate(()=>scrollY))-scroll)<24,'pickup cancellation preserves menu scroll');
 if(width===390&&!expired&&reduced){
  let releaseDiscovery;pickerGate=new Promise(resolve=>{releaseDiscovery=resolve;});
  const requested=page.waitForRequest(isPickerRequest);
  await page.locator('.menu-compact-pickup').click();await requested;
  await page.evaluate(()=>scrollTo({top:2500,behavior:'instant'}));const latestScroll=await page.evaluate(()=>scrollY);
  assert.ok(latestScroll>scroll+500,'customer continues browsing while pickup times load');
  releaseDiscovery();pickerGate=null;await picker.waitFor();
  assert.ok(Math.abs((await page.evaluate(()=>scrollY))-latestScroll)<2,'opening a delayed picker keeps the latest browsing position');
  await picker.getByRole('button',{name:'Close pickup selector',exact:true}).click();await picker.waitFor({state:'hidden'});
  assert.ok(Math.abs((await page.evaluate(()=>scrollY))-latestScroll)<2,'closing a delayed picker keeps the latest browsing position');
  assert.equal(await page.evaluate(()=>localStorage.getItem('gokul-selected-pickup-slot')),saved);
  assert.equal(await page.locator('.menu-compact-pickup').evaluate(n=>document.activeElement===n),true);
 }
 await page.locator('.menu-compact-search').click();await page.waitForFunction(()=>document.activeElement?.id==='gokul-menu-search');await page.locator('#gokul-menu-search').fill('Rasgulla');await page.locator('#gokul-product-1').waitFor();assert.equal(await page.locator('#gokul-product-2').count(),0,'compact search uses the existing menu filter');await page.locator('#gokul-menu-search').fill('');
 await page.evaluate(()=>window.scrollTo({top:0,behavior:'instant'}));await page.waitForFunction(()=>!document.querySelector('.customer-site-header[data-menu-compact]'));await page.locator('.menu-compact-header').waitFor({state:'hidden'});assert.equal(await page.locator('.menu-compact-header').evaluate(n=>n.inert),true);assert.equal(await header.locator('[data-header-content]').evaluate(n=>getComputedStyle(n).opacity),'1');assert.equal(await header.locator('[data-header-content]').evaluate(n=>n.inert),false);assert.equal(await header.locator('[data-header-content]').getAttribute('aria-hidden'),null);
 await page.screenshot({path:`${screens}/expanded-pickup-${width}.png`});
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);assert.deepEqual(errors,[]);await context.close();console.log(`Compact pickup transition, stable scroll, original picker/search and restoration passed ${width}px`);
}}finally{await browser.close();}
