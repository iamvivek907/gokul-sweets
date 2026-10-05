import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {mkdir} from 'node:fs/promises';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const browser=await chromium.launch({headless:true}),base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
const branch={id:1,name:'Gokul branch',code:'TEST',active:true,pickupAvailable:true};
const key='gokul-pwa-install-preferences-v1';
async function setup({width=390,ios=false,standalone=false,themed=true,stored=false,authenticated=true}={}){
 const context=await browser.newContext({viewport:{width,height:844},serviceWorkers:'block',reducedMotion:'reduce'});
 await context.addInitScript(({ios,standalone,stored,branch,key})=>{
  localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));
  if(stored&&!sessionStorage.getItem('old-installed-initialized')){localStorage.setItem('gokul-pwa-installed','true');localStorage.setItem(key,JSON.stringify({installed:true}));sessionStorage.setItem('old-installed-initialized','true');}
  if(ios)Object.defineProperty(navigator,'userAgent',{value:'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 Version/18.0 Mobile Safari/604.1'});
  if(standalone&&ios)Object.defineProperty(navigator,'standalone',{value:true});
  if(standalone&&!ios){const original=window.matchMedia.bind(window);window.matchMedia=query=>{const media=original(query);if(query==='(display-mode: standalone)')Object.defineProperty(media,'matches',{value:true});return media;};}
  window.__nativeCalls=0;window.__activeGesture=[];window.__widgetConfig=null;
  window.initSendOTP=config=>{window.__widgetConfig=config;};
 },{ios,standalone,stored,branch,key});
 await context.route('**/api/**',async route=>{
  const path=new URL(route.request().url()).pathname;let json=[];
  if(path==='/api/storefront/features')json={futuristicStorefrontV2:themed,checkoutExperienceV2:themed,simplifiedCheckout:themed,acceptedCheckoutQuote:themed,customerAccountHub:true,notificationInbox:true,branchExperience:true,preHomeIntentGateway:true};
  else if(path==='/api/storefront/customer-identity')json={enabled:true,guestCheckoutEnabled:false};
  else if(path==='/api/customer/identity/me')json={authenticated,name:'Install customer',phone:authenticated?'+919876543210':undefined};
  else if(path==='/api/customer/identity/start')return route.fulfill({status:204});
  else if(path==='/api/customer/identity/orders/page')json={orders:[],nextBefore:null};
  else if(path==='/api/customer/identity/account')json={paidOrders:0,favouriteProductIds:[],addresses:[],preferences:{dietaryNotes:null,preferredBranchId:null}};
  else if(path==='/api/customer/identity/notifications')json={messages:[],unreadCount:0,nextBefore:null};
  else if(path==='/api/branches')json=[branch];else if(path==='/api/branches/1')json=branch;
  else if(path==='/api/branches/1/discovery')json={overallExperience:{average:4.8,count:2},offerings:[],topRatedItems:[]};
  else if(path==='/api/admin/auth/me')return route.fulfill({status:401,json:{message:'Not signed in'}});
  return route.fulfill({json});
 });
 const page=await context.newPage(),errors=[];page.on('pageerror',error=>errors.push(error.message));
 await page.goto(`${base}/profile`);await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});await page.locator('.customer-site-header').waitFor();
 return {context,page,errors};
}
async function emit(page,{result='dismissed',pending=false,fails=false}={}){
 await page.evaluate(({result,pending,fails})=>{
  const event=new Event('beforeinstallprompt',{cancelable:true});
  const choice=new Promise(resolve=>{window.__resolveChoice=outcome=>resolve({outcome});});
  event.userChoice=pending?choice:Promise.resolve({outcome:result});
  event.prompt=async()=>{window.__nativeCalls++;window.__activeGesture.push(navigator.userActivation.isActive);if(fails)throw Error('Browser unavailable');};
  window.dispatchEvent(event);
 },{result,pending,fails});
}
const card=page=>page.locator('.pwa-install-card');
async function assertActionContrast(button){
 const ratio=await button.evaluate(element=>{
  const style=getComputedStyle(element);
  const luminance=color=>{
   const values=color.match(/[\d.]+/g).slice(0,3).map(value=>{
    const channel=Number(value)/255;return channel<=.04045?channel/12.92:((channel+.055)/1.055)**2.4;
   });return values[0]*.2126+values[1]*.7152+values[2]*.0722;
  };
  const text=luminance(style.color),background=luminance(style.backgroundColor);
  return (Math.max(text,background)+.05)/(Math.min(text,background)+.05);
 });
 assert.ok(ratio>=4.5,`Primary action text contrast ${ratio.toFixed(2)}:1 must meet 4.5:1`);
}
try{
 // Declared image assets decode at their actual manifest sizes, including maskable.
 const assets=await setup();const manifest=await (await assets.page.request.get(`${base}/manifest.webmanifest`)).json();
 assert.equal(manifest.id,'/');assert.equal(manifest.start_url,'/');assert.equal(manifest.scope,'/');
 for(const icon of manifest.icons){const size=Number(icon.sizes.split('x')[0]);const dimensions=await assets.page.evaluate(async src=>{const image=new Image();image.src=src;await image.decode();return [image.naturalWidth,image.naturalHeight];},icon.src);assert.deepEqual(dimensions,[size,size]);}
 await assets.context.close();
 // Native signal, rapid taps, consumed event, preference persistence and unavailable state.
 const {context,page,errors}=await setup();await page.locator('.account-hub').waitFor();assert.equal(await card(page).count(),0);
 await emit(page,{pending:true});await card(page).getByRole('button',{name:'Install App',exact:true}).waitFor();assert.equal(await page.evaluate(()=>window.__nativeCalls),0);
 await assertActionContrast(card(page).getByRole('button',{name:'Install App',exact:true}));
 await card(page).getByRole('button',{name:'Install App',exact:true}).click();await page.waitForFunction(()=>window.__nativeCalls===1);await page.evaluate(()=>document.querySelector('.pwa-install-copy button')?.click());
 assert.equal(await page.evaluate(()=>window.__nativeCalls),1);assert.deepEqual(await page.evaluate(()=>window.__activeGesture),[true]);
 await page.evaluate(()=>window.__resolveChoice('dismissed'));await card(page).waitFor({state:'hidden'});await page.reload();await page.locator('.account-hub').waitFor();await emit(page);assert.equal(await card(page).count(),0);
 assert.equal(await page.evaluate(key=>JSON.parse(localStorage.getItem(key)).installPromptResult,key),'dismissed');
 assert.deepEqual(errors,[]);await context.close();console.log('PWA Android choice, gesture and duplicate prompt protection passed');
 // Multiple tabs use one native prompt and synchronize rejection cooldown.
 const multi=await setup();await emit(multi.page,{pending:true});const other=await multi.context.newPage();await other.goto(`${base}/profile`);await other.locator('.account-hub').waitFor();await emit(other,{pending:true});
 await card(multi.page).getByRole('button',{name:'Install App',exact:true}).click();await multi.page.waitForFunction(()=>window.__nativeCalls===1);
 await other.evaluate(()=>document.querySelector('.pwa-install-card .pwa-install-copy button')?.click());
 assert.equal(await multi.page.evaluate(()=>window.__nativeCalls)+await other.evaluate(()=>window.__nativeCalls),1);
 await multi.page.evaluate(()=>window.__resolveChoice('dismissed'));await card(other).waitFor({state:'hidden'});await multi.context.close();console.log('PWA cross-tab prompt/cooldown passed');
 // Accepted choice and actual appinstalled both suppress further promotion.
 for(const actualEvent of [false,true]){const h=await setup();await emit(h.page,{result:'accepted',pending:actualEvent});await card(h.page).getByRole('button',{name:'Install App',exact:true}).click();if(actualEvent)await h.page.evaluate(()=>{window.dispatchEvent(new Event('appinstalled'));window.__resolveChoice('dismissed');});await card(h.page).waitFor({state:'hidden'});await emit(h.page);assert.equal(await card(h.page).count(),0);await h.context.close();}
 // Native failure reports a friendly error; continuing to browse is possible.
 const failure=await setup();await emit(failure.page,{fails:true});await card(failure.page).getByRole('button',{name:'Install App',exact:true}).click();await card(failure.page).getByRole('alert').waitFor();assert.match(await card(failure.page).innerText(),/keep ordering/);assert.equal(await card(failure.page).getByRole('button',{name:'Install App',exact:true}).count(),0);await failure.context.close();
 // iOS compact sheet: close, Escape, outside click, focus return, Hindi, no fake installed state.
 for(const width of [320,390,640]){
  const h=await setup({width,ios:true,stored:true});await card(h.page).getByRole('button',{name:'Install App',exact:true}).waitFor();assert.equal(await h.page.getByRole('dialog').count(),0);
  const install=card(h.page).getByRole('button',{name:'Install App',exact:true});await install.click();const guide=h.page.getByRole('dialog',{name:'Add Gokul Sweets to your Home Screen'});await guide.waitFor();assert.equal(await guide.locator('li').count(),3);assert.equal(await guide.getByRole('button',{name:'Close install instructions'}).evaluate(e=>e===document.activeElement),true);
  await assertActionContrast(install);await assertActionContrast(guide.getByRole('button',{name:'Got it',exact:true}));
  await h.page.keyboard.press('Escape');await guide.waitFor({state:'hidden'});assert.equal(await install.evaluate(e=>e===document.activeElement),true);
  await install.click();await guide.getByRole('button',{name:'Got it',exact:true}).click();await guide.waitFor({state:'hidden'});assert.equal(await install.isVisible(),true);
  await install.click();await h.page.mouse.click(5,5);await guide.waitFor({state:'hidden'});assert.equal(await h.page.evaluate(()=>window.__nativeCalls),0);
  assert.equal(await h.page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
  if(process.env.SCREENSHOT_DIR){await mkdir(process.env.SCREENSHOT_DIR,{recursive:true});await h.page.screenshot({path:`${process.env.SCREENSHOT_DIR}/pwa-profile-${width}.png`});await install.click();await h.page.screenshot({path:`${process.env.SCREENSHOT_DIR}/pwa-ios-guide-${width}.png`});await guide.getByRole('button',{name:'Got it',exact:true}).click();}
  if(width===320){
   await h.page.locator('html').evaluate(e=>e.style.fontSize='20px');await install.click();assert.equal(await h.page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);await guide.getByRole('button',{name:'Got it',exact:true}).click();
   await h.page.evaluate(()=>{localStorage.setItem('gokul-language','hi');window.dispatchEvent(new StorageEvent('storage',{key:'gokul-language',newValue:'hi'}));});await card(h.page).getByRole('button',{name:'ऐप इंस्टॉल करें',exact:true}).waitFor();await card(h.page).getByRole('button',{name:'ऐप इंस्टॉल करें',exact:true}).click();await h.page.getByRole('dialog',{name:'गोकुल स्वीट्स को अपनी होम स्क्रीन पर जोड़ें'}).waitFor();await h.page.keyboard.press('Escape');
   await h.page.evaluate(()=>{localStorage.setItem('gokul-language','en');window.dispatchEvent(new StorageEvent('storage',{key:'gokul-language',newValue:'en'}));});await card(h.page).getByRole('button',{name:'Install App',exact:true}).waitFor();
  }
  await card(h.page).getByRole('button',{name:'Close install banner'}).click();await h.page.reload();await h.page.locator('.account-hub').waitFor();assert.equal(await card(h.page).count(),0);assert.deepEqual(h.errors,[]);await h.context.close();console.log(`PWA iOS guide/layout ${width}px passed`);
 }
 // Standalone launches, desktop breakpoint and flag OFF retain their presentation.
 for(const config of [{ios:true,standalone:true},{standalone:true},{width:641,ios:true},{width:1280},{themed:false,ios:true}]){const h=await setup(config);await h.page.locator('.account-hub').waitFor();await emit(h.page);assert.equal(await card(h.page).count(),0);await h.context.close();}
 // Home/branch placements only; no promotion on checkout, payment or order routes.
 const routes=await setup({ios:true});for(const path of ['/', '/branches/1']){await routes.page.goto(`${base}${path}`);await card(routes.page).getByRole('button',{name:'Install App',exact:true}).waitFor();}
 for(const path of ['/checkout/mobile','/checkout/payment/TEST-PENDING','/orders/TEST-PENDING']){await routes.page.goto(`${base}${path}`);await routes.page.locator('.customer-site-header').waitFor();assert.equal(await card(routes.page).count(),0);assert.equal(await routes.page.locator('.pwa-install-guide[open]').count(),0);}
 await routes.context.close();console.log('PWA route, desktop, flag-OFF and standalone safety passed');
 // Active provider verification suppresses installation without changing login.
 const otp=await setup({ios:true,authenticated:false});await card(otp.page).getByRole('button',{name:'Install App',exact:true}).waitFor();await otp.page.getByRole('button',{name:'Verify with SMS',exact:true}).click();await otp.page.waitForFunction(()=>window.__widgetConfig!==null);await card(otp.page).waitFor({state:'hidden'});await otp.context.close();console.log('PWA OTP suppression passed');
 // Legacy iOS promotions obey the same blocker on flag-OFF phones and wider tablets.
 for(const config of [{width:390,themed:false},{width:768,themed:false},{width:768,themed:true}]){
  const h=await setup({...config,ios:true,authenticated:false});
  const legacy=h.page.getByRole('button',{name:/Install Gokul Sweets Faster access/});
  await legacy.waitFor();await h.page.getByRole('button',{name:'Verify with SMS',exact:true}).click();
  await h.page.waitForFunction(()=>window.__widgetConfig!==null);await legacy.waitFor({state:'hidden'});
  assert.equal(await card(h.page).count(),0);
  await h.page.evaluate(()=>window.__widgetConfig.failure());await legacy.waitFor();
  assert.equal(await h.page.evaluate(()=>window.__nativeCalls),0);assert.deepEqual(h.errors,[]);
  await h.context.close();console.log(`PWA legacy iOS OTP suppression/recovery ${config.width}px themed=${config.themed} passed`);
 }
}finally{await browser.close();}
