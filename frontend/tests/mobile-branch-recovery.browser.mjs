import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const browser=await chromium.launch({headless:true}),base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
const branch={id:1,name:'Review branch',operational:true,active:true,pickupAvailable:true};
const products=Array.from({length:12},(_,i)=>({id:i+1,name:`Fresh sweet ${i+1}`,categoryId:1,categoryName:'Sweets',price:100,saleMode:'UNIT',available:true}));
try{for(const missing of ['none','any','both']){
 const context=await browser.newContext({viewport:{width:390,height:844},isMobile:true,hasTouch:true,serviceWorkers:'block',userAgent:'Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.127 Mobile Safari/537.36'}),page=await context.newPage();page.setDefaultTimeout(15000);let fail=false,reads=0;const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await context.addInitScript(({branch,missing})=>{localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));if(missing!=='none')Object.defineProperty(AbortSignal,'any',{value:undefined,configurable:true});if(missing==='both')Object.defineProperty(AbortSignal,'timeout',{value:undefined,configurable:true});},{branch,missing});
 const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Methods':'GET,OPTIONS','Access-Control-Allow-Headers':'content-type'};
 await context.route('**/api/**',async route=>{const path=new URL(route.request().url()).pathname;let json=[];
  if(route.request().method()==='OPTIONS')return route.fulfill({status:204,headers});
  if(path==='/api/storefront/features')json={futuristicStorefrontV2:true,checkoutExperienceV2:true,simplifiedCheckout:true,contextualStorefrontV2:true,acceptedCheckoutQuote:true,notificationInbox:true};
  else if(path==='/api/storefront/customer-identity')json={enabled:true};else if(path==='/api/customer/identity/me')json={authenticated:true,name:'Review customer',phone:'+919876543210'};
  else if(path==='/api/customer/identity/notification-preferences')json={offerInboxEnabled:false,marketingConsentGranted:false};
  else if(path==='/api/customer/identity/notifications')json={messages:[],unreadCount:0,nextBefore:null};
  else if(path==='/api/branches')json=[branch];
  else if(path==='/api/branches/1'){reads++;if(fail)return route.fulfill({status:503,headers,json:{message:'Temporary outage'}});json=branch;}
  else if(path==='/api/menu')json=[{id:1,name:'Sweets',products}];else if(path==='/api/menu/portion-groups')json={groups:[]};
  return route.fulfill({headers,json});
 });
 await page.goto(base+'/menu');await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});const search=page.getByLabel('Find a favourite',{exact:true});await search.fill('Fresh');
 // Preserve component identity as well as input value through a failed background read.
 await search.evaluate(n=>n.dataset.reviewMarker='retained');
 await page.getByRole('button',{name:'Browse all item categories',exact:true}).click();
 const picker=page.getByRole('dialog',{name:'Items',exact:true});await picker.waitFor({state:'visible'});
 await page.clock.install();fail=true;await page.clock.fastForward(15001);
 const notice=page.getByRole('dialog',{name:'Branch availability could not be checked',exact:true});await notice.waitFor({state:'visible'});
 assert.equal(await search.inputValue(),'Fresh');assert.equal(await search.getAttribute('data-review-marker'),'retained');
 assert.equal(await page.evaluate(()=>document.activeElement.closest('dialog')?.className),'customer-branch-refresh-dialog','recovery controls take focus above an existing category dialog');
 fail=false;await notice.getByRole('button',{name:'Try again',exact:true}).click();await notice.waitFor({state:'detached'});
 assert.equal(await search.inputValue(),'Fresh');assert.equal(await search.getAttribute('data-review-marker'),'retained');assert.ok(reads>=3);
 await picker.waitFor({state:'visible'});await page.keyboard.press('Escape');await picker.waitFor({state:'hidden'});
 await page.goto(base+'/notifications');await page.getByText('You’re all caught up. Your order updates will appear here.',{exact:true}).waitFor();
 assert.deepEqual(errors,[]);assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
 await context.close();console.log(`Android touch recovery and inbox, missing APIs=${missing}: passed`);
}}finally{await browser.close();}
