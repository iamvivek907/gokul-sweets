import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {mkdir} from 'node:fs/promises';
const {chromium,webkit}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const browser=await (process.env.BROWSER_ENGINE==='webkit'?webkit:chromium).launch({headless:true});
const base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
const today=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata'}).format(new Date());
const date=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata'}).format(new Date(Date.now()+86400000));
const branch={id:1,name:'Gokul Test branch',code:'TEST',address:'Main Road',active:true,operational:true,pickupAvailable:true};
const products=Array.from({length:16},(_,i)=>({id:i+1,name:`Test meal ${i+1}`,categoryId:1,categoryName:'Meals',price:100+i*10,available:true,saleMode:'UNIT',description:'Prepared fresh for pickup.',imageUrl:'/logo.png'}));
const slot={id:1,branchId:1,slotDate:date,startTime:'18:00:00',endTime:'19:00:00',remainingCapacity:20,active:true};
const offer={rebateId:1,name:'Spend saving',code:'SAVE',scope:'GENERAL',rebateType:'SLAB',rebateAmount:0,minimumOrderAmount:400,maximumDiscountAmount:40,nextSlabMinimumOrderAmount:400,nextSlabRebateAmount:40,amountNeededForNextSlab:150};
try{for(const width of [320,390,640]){
 const context=await browser.newContext({viewport:{width,height:900},hasTouch:true,isMobile:true,serviceWorkers:'block'});
 const page=await context.newPage();page.setDefaultTimeout(15000);const errors=[];page.on('pageerror',e=>errors.push(e.message));
 let menuReads=0,inventoryReleased=false,failAdd=true;let releaseInventory;const inventoryGate=new Promise(r=>releaseInventory=r);
 const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Methods':'GET,POST,PUT,OPTIONS','Access-Control-Allow-Headers':'content-type,idempotency-key'};
 await context.route('**/api/**',async route=>{const path=new URL(route.request().url()).pathname;let json=[];
  if(route.request().method()==='OPTIONS')return route.fulfill({status:204,headers});
  if(path==='/api/storefront/features')json={futuristicStorefrontV2:true,checkoutExperienceV2:true,contextualStorefrontV2:true,simplifiedCheckout:true,acceptedCheckoutQuote:true,pickupAddOns:true,smartAvailability:false,today,futureOrderingDays:30};
  else if(path==='/api/branches')json=[branch];else if(path==='/api/branches/1')json=branch;
  else if(path==='/api/menu'){menuReads++;json=[{id:1,name:'Meals',products}];}
  else if(path==='/api/menu/portion-groups')json={groups:[]};
  else if(path==='/api/storefront/customer-identity')json={enabled:true,guestCheckoutEnabled:false};
  else if(path==='/api/customer/identity/me')json={authenticated:true,name:'Test customer',phone:'+919876543210'};
  else if(path==='/api/customer/identity/orders')json=[{orderNumber:'OLD',branchId:1,orderStatus:'PICKED_UP'}];
  else if(path==='/api/customer/identity/orders/OLD')json={branchId:1,orderStatus:'PICKED_UP',paymentStatus:'PAID',items:[{productId:2}]};
  else if(path==='/api/branches/1/inventory/check'){await inventoryGate;json={enforcementEnabled:true,items:[{productId:2,orderable:true}]};}
  else if(path==='/api/menu/pickup-addons'){await new Promise(r=>setTimeout(r,200));json=[{product:products[0],weightGrams:null,portionPrice:100,portionTotal:100,reason:'Often ordered together'}];}
  else if(path==='/api/menu/pickup-addons/check')json={orderable:!failAdd};
  else if(path==='/api/orders/mobile-preview')json={offers:[],spendTargets:[offer]};
  await route.fulfill({json,headers}).catch(()=>{});
 });
 await context.addInitScript(({branch,slot,date})=>{localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));localStorage.setItem('gokul-selected-pickup-slot',JSON.stringify({date,slot,pickupType:'NORMAL'}));Object.defineProperty(navigator,'standalone',{value:true,configurable:true});},{branch,slot,date});
 await page.goto(`${base}/menu`);await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});const row=page.locator('#gokul-product-12');await row.waitFor();
 await page.evaluate(()=>document.documentElement.style.scrollBehavior="auto");await row.scrollIntoViewIfNeeded();await page.evaluate(()=>scrollBy({top:-100,behavior:"instant"}));await page.waitForTimeout(300);
 const before=await row.evaluate(n=>({top:n.getBoundingClientRect().top,scroll:scrollY,document:window.__testDocument=crypto.randomUUID()}));const node=await row.elementHandle();const reads=menuReads;
 await row.getByRole('button',{name:'Add Test meal 12 to cart',exact:true}).click();
 await page.waitForFunction(()=>JSON.parse(localStorage.getItem('gokul-cart')).items.length===1);
 await page.locator('.mobile-menu-pairings-inline').getByRole('button',{name:'Add Test meal 1 from pairings',exact:true}).waitFor({timeout:1200});
 assert.equal(inventoryReleased,false,'verified branch pairings appear while historical stock is still pending');
 assert.equal(await page.evaluate(()=>window.__testDocument),before.document);assert.equal(await node.evaluate(n=>n.isConnected),true);
 assert.ok(Math.abs(await row.evaluate(n=>n.getBoundingClientRect().top)-before.top)<3,'cold first Add retains the tapped row position');assert.equal(menuReads,reads,'first Add does not reload the menu');
 const inline=page.locator('.mobile-menu-pairings-inline');await inline.getByRole('button',{name:'Add Test meal 1 from pairings',exact:true}).click();
 await page.getByRole('status').filter({hasText:'This addition no longer fits'}).waitFor();assert.equal(await inline.getByRole('button',{name:'Add Test meal 1 from pairings',exact:true}).isVisible(),true,'failure does not hide retryable inline cards');
 failAdd=false;await inline.getByRole('button',{name:'Add Test meal 1 from pairings',exact:true}).click();await page.waitForFunction(()=>JSON.parse(localStorage.getItem('gokul-cart')).items.length===2);
 releaseInventory();inventoryReleased=true;await inline.getByRole('button',{name:'Add Test meal 2 from pairings',exact:true}).waitFor();assert.equal(await page.getByText('This addition no longer fits your pickup. Choose another time.',{exact:true}).count(),0,'old-context error clears');
 await page.locator('.mobile-cart-offer-target').getByText(/Unlock/).waitFor();await page.locator('.reference-cart-summary').getByText(/^2 items ·/).waitFor();
 await page.emulateMedia({reducedMotion:'reduce'});assert.equal(await page.locator('.gokul-floating-cart').evaluate(n=>getComputedStyle(n).animationName),'none');assert.ok(await page.locator('.mobile-cart-offer-icon circle').last().evaluate(n=>parseFloat(getComputedStyle(n).transitionDuration)<.001),'reduced motion makes progress updates effectively immediate');await page.emulateMedia({reducedMotion:'no-preference'});
 await page.waitForTimeout(300);const cartBounds=await page.locator('.gokul-floating-cart').boundingBox();assert.ok(cartBounds.x>=0&&cartBounds.x+cartBounds.width<=width,'animated cart remains centred within the viewport');
 if(process.env.SCREENSHOT_DIR){await mkdir(process.env.SCREENSHOT_DIR,{recursive:true});await page.screenshot({path:`${process.env.SCREENSHOT_DIR}/menu-polish-${width}.png`,fullPage:false});}
 // Use a genuine page Back link; interior drags and open dialogs must not navigate.
 await page.goto(`${base}/profile/rewards`);await page.locator('.mobile-page-back').waitFor();await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});
 const swipe=async(x,y=450,dy=0)=>page.evaluate(({x,y,dy})=>{const target=document.querySelector('.page-content');const dispatch=(type,px,py)=>{const touch=new Touch({identifier:1,target,clientX:px,clientY:py});document.dispatchEvent(new TouchEvent(type,{bubbles:true,cancelable:true,touches:type==='touchend'?[]:[touch],changedTouches:[touch]}));};dispatch('touchstart',x,y);dispatch('touchmove',x+130,y+dy);dispatch('touchend',x+130,y+dy);},{x,y,dy});
 // The gesture never translates the large scrolling surface; scroll abandons an incomplete gesture.
 await page.evaluate(()=>{const target=document.querySelector('.page-content'),touch=x=>new Touch({identifier:1,target,clientX:x,clientY:450});document.dispatchEvent(new TouchEvent('touchstart',{bubbles:true,touches:[touch(10)]}));document.dispatchEvent(new TouchEvent('touchmove',{bubbles:true,cancelable:true,touches:[touch(100)]}));});
 assert.equal(await page.locator('.page-content').evaluate(n=>getComputedStyle(n).translate),'none');
 await page.evaluate(()=>document.dispatchEvent(new Event('scroll')));assert.equal(await page.locator('.future-storefront').getAttribute('data-edge-back'),null);
 await swipe(100);assert.ok(page.url().includes('/profile/rewards'),'interior drag is not Back');await swipe(10,450,80);assert.ok(page.url().includes('/profile/rewards'),'vertical scrolling is not Back');
 await page.evaluate(()=>{const d=document.createElement('dialog');d.id='gesture-test';document.body.append(d);d.showModal();});await swipe(10);assert.ok(page.url().includes('/profile/rewards'),'open modal blocks Back gesture');await page.evaluate(()=>document.getElementById('gesture-test').remove());
 await swipe(10);await page.waitForURL('**/profile');assert.deepEqual(errors,[]);await context.close();console.log(`Ordering polish ${width}px passed: cold Add, delayed history, retry, feedback, cart and edge gesture`);
}}finally{await browser.close();}
