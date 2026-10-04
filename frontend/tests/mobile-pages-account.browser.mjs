import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {mkdir} from 'node:fs/promises';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const browser=await chromium.launch({headless:true}),base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
const branch={id:1,code:'TEST',name:'Gokul Tamkuhi Road with a long branch name',active:true,pickupAvailable:true,address:'Test address',phone:'9876543210'};
const otherBranch={...branch,id:2,code:"OTHER",name:"Other Gokul branch"};
const sweet={id:1,name:'Gulab Jamun',description:'Fresh sweets for your celebration',saleMode:'WEIGHT',occasionOnly:false,published:true,leadDays:2,pieceGrams:50,categoryName:'Sweets',unitPrice:300,taxPercent:5,imageUrl:'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="600" height="400"%3E%3Crect width="600" height="400" fill="%23c76752"/%3E%3C/svg%3E'};
const today=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata'}).format(new Date());
try{
 for(const [width,themed] of [[320,true],[390,true],[601,true],[640,true],[641,true],[1280,true],[390,false]]){
  let authenticated=true,logoutError=true,logoutCalls=0,staffChecks=0,orders=0,customerName='Vivek Chaurasia',branchExperience=true;
  const context=await browser.newContext({viewport:{width,height:844},serviceWorkers:'block',timezoneId:'America/Los_Angeles'}),page=await context.newPage();
  await context.addInitScript(branch=>{if(!sessionStorage.getItem('test-branch-initialized')){localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));sessionStorage.setItem('test-branch-initialized','true');}window.initSendOTP=config=>config.success({accessToken:'test-provider-proof'});},branch);
  await context.route('**/api/**',async route=>{
   const req=route.request(),path=new URL(req.url()).pathname;let json=[];
   if(path==='/api/storefront/features')json={futuristicStorefrontV2:themed,checkoutExperienceV2:themed,simplifiedCheckout:themed,acceptedCheckoutQuote:themed,customerAccountHub:true,notificationInbox:true,branchExperience,occasionEnquiries:true,today};
   else if(path==='/api/storefront/customer-identity')json={enabled:true,guestCheckoutEnabled:false};
   else if(path==='/api/customer/identity/me')json={authenticated,name:customerName,phone:'+919876543210'};
   else if(path==='/api/customer/identity/start')return route.fulfill({status:204});
   else if(path==='/api/customer/identity/exchange'){authenticated=true;json={authenticated,name:customerName,phone:'+919876543210'};}
   else if(path==='/api/customer/identity/logout'){logoutCalls++;if(logoutError)return route.fulfill({status:503,json:{message:'Unavailable'}});authenticated=false;return route.fulfill({status:204});}
   else if(path==='/api/customer/identity/notifications')json={messages:[],unreadCount:1,nextBefore:null,readThrough:0};
   else if(path==='/api/customer/identity/account')json={completedOrders:1,paidOrders:1,favouriteProductIds:[],addresses:[],preferences:{dietaryNotes:null,preferredBranchId:null}};
   else if(path==='/api/branches')json=[branch,otherBranch];else if(path==='/api/branches/1')json=branch;else if(path==='/api/branches/2')json=otherBranch;
   else if(/^\/api\/branches\/[12]\/discovery$/.test(path))json={overallExperience:{average:4.8,count:2},offerings:[{title:'Fresh sweets',description:'Made at this branch'}],topRatedItems:[{productId:1,name:'Gulab Jamun',imageUrl:null,average:5,count:2,reviews:[]}]};
   else if(/^\/api\/branches\/[12]\/occasion-catalogue$/.test(path))json={sweets:[sweet],boxes:[],branding:{headline:'Wedding Dhamaka',description:'Exclusive for weddings',imageUrl:null,published:true}};
   else if(path==='/api/admin/auth/me'){staffChecks++;return route.fulfill({status:401,json:{message:'Not signed in'}});}
   else if(req.method()==='POST'&&['/api/orders','/api/payments','/api/occasion-enquiries'].includes(path))orders++;
   return route.fulfill({json});
  });
  const compact=width<=640&&themed;
  await page.goto(`${base}/branches/1`);await page.locator('.branch-rated-grid article').waitFor();
  await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});
  assert.equal(await page.locator('.customer-menu-trigger').isVisible(),!compact);
  assert.equal(await page.locator('.customer-account-mobile-label').isVisible(),compact);
  if(compact){assert.ok((await page.locator('.customer-site-header').boundingBox()).height<=110,'header is at most two short rows');assert.equal(await page.locator('.branch-rated-grid article').evaluate(e=>getComputedStyle(e).gridTemplateColumns.split(' ')[0]),'88px');}
  else assert.equal(await page.locator('.branch-rated-grid article').evaluate(e=>getComputedStyle(e).display),'block');
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
  if(compact){for(const box of await page.locator('.branch-home .gokul-branch-tabs :is(a,button):visible').all()){const b=await box.boundingBox();assert.ok(b.x>=0&&b.x+b.width<=width,'branch tabs fit without clipping');}assert.equal(await page.locator('.gokul-branch-tabs [aria-current=page]').evaluate(e=>getComputedStyle(e).color),'rgb(255, 250, 242)','active tab remains readable');assert.equal(await page.locator('.branch-request-desktop').isVisible(),false);assert.equal(await page.locator('.branch-welcome-mobile').isVisible(),true);}
  if(process.env.SCREENSHOT_DIR&&compact){await mkdir(process.env.SCREENSHOT_DIR,{recursive:true});await page.screenshot({path:`${process.env.SCREENSHOT_DIR}/branch-home-${width}.png`,fullPage:true});}
  await page.goto(`${base}/branches/2`);await page.locator('.branch-rated-grid article').waitFor();await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});
  const celebration=page.getByRole('button',{name:'Explore this branch · Bulk order · Other Gokul branch',exact:true});
  assert.equal(await celebration.locator('.mobile-celebration-label').isVisible(),compact);
  assert.equal(await celebration.locator('.desktop-celebration-label').isVisible(),!compact);
  const visibleCelebrationLabel=await celebration.locator(compact?'.mobile-celebration-label':'.desktop-celebration-label').innerText();
  assert.ok((await celebration.getAttribute('aria-label')).includes(visibleCelebrationLabel),'accessible name includes the visible label for speech control');
  await celebration.click();await page.waitForURL('**/occasions');
  assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-selected-branch')).id),2,'celebration tab selects its own branch');
  await page.goto(`${base}/occasions`);await page.getByLabel('Gulab Jamun quantity').waitFor();await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});
  assert.equal(await page.locator('.mobile-occasion-navigation').isVisible(),compact);
  assert.equal(await page.locator('.occasion-product').evaluate(e=>getComputedStyle(e).display),compact?'block':themed?'flex':'block');
  if(compact){for(const control of await page.locator('#occasion-plan input:not([type=checkbox]),#occasion-plan select').all()){const b=await control.boundingBox();assert.ok(b.x>=0&&b.x+b.width<=width,'bulk-order inputs fit the viewport');}}
  await page.getByLabel('Gulab Jamun quantity').fill('2.5');
  await page.getByLabel('Gulab Jamun unit').selectOption('PIECE');
  assert.equal(await page.getByLabel('Gulab Jamun quantity').inputValue(),'0','unit changes preserve the existing reset behavior');
  await page.getByLabel('Gulab Jamun quantity').fill('10');
  await page.getByText('1 items · 0 packing groups',{exact:true}).waitFor();
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
  if(process.env.SCREENSHOT_DIR&&compact){await mkdir(process.env.SCREENSHOT_DIR,{recursive:true});await page.screenshot({path:`${process.env.SCREENSHOT_DIR}/celebrations-${width}.png`,fullPage:true});}
  assert.match(await page.locator('.customer-account-link').getAttribute('aria-label'),/\bVC\b/,'account accessible name includes the visible initials');
  await page.locator('.customer-account-link').click();await page.waitForURL('**/profile');await page.locator('.account-hub').waitFor();
  assert.equal(await page.locator('.mobile-account-navigation').isVisible(),compact);
  assert.equal(await page.locator('.mobile-account-logout').isVisible(),compact);
  assert.equal(await page.locator('.mobile-account-links a[href="/admin"]').count(),0,'staff access remains role checked');
  assert.equal(staffChecks,compact?1:0,'desktop and flag OFF add no new staff checks');
  if(compact){
   assert.equal(await page.locator('.mobile-account-links a[href="/menu"],.mobile-account-links a[href="/cart"],.mobile-account-links a[href="/occasions"]').count(),0);
   assert.equal(await page.locator('.mobile-profile-rewards').isVisible(),true);
   await page.getByRole('button',{name:'Order history',exact:true}).click();
   await page.locator('.mobile-profile-order-types').getByRole('link',{name:'Bulk order requests',exact:true}).waitFor();
   assert.equal(await page.locator('.mobile-profile-order-types a').getAttribute('href'),'/occasions/requests');
   await page.getByRole('link',{name:'Back to profile',exact:true}).click();await page.waitForURL('**/profile');await page.getByRole('button',{name:'Badges',exact:true}).click();await page.waitForURL('**/profile/badges');await page.getByRole('link',{name:'Back to profile',exact:true}).click();await page.waitForURL('**/profile');
   const lastLink=page.locator('.mobile-account-links a').last();
   await lastLink.scrollIntoViewIfNeeded();await page.evaluate(()=>window.scrollTo({top:document.documentElement.scrollHeight,behavior:'instant'}));
   const lastBox=await lastLink.boundingBox(),navigationBox=await page.locator('.customer-bottom-navigation').boundingBox();
   assert.ok(lastBox.y+lastBox.height<=navigationBox.y,'last profile link scrolls fully above fixed navigation');
   if(width===390){
    customerName=undefined;await page.reload();await page.locator('.customer-account-mobile-label svg').waitFor();
    assert.equal(await page.locator('.customer-account-mobile-label').textContent(),'','no-name session uses a neutral account icon');
    assert.match(await page.locator('.customer-account-link').getAttribute('aria-label'),/3210/);
    customerName='Vivek Chaurasia';branchExperience=false;await page.reload();
    await page.locator('.mobile-account-back').getByRole('link',{name:'Home',exact:true}).waitFor();
    assert.equal(await page.locator('.mobile-account-back').getByRole('link',{name:'Home',exact:true}).getAttribute('href'),'/');
    await page.goto(`${base}/occasions`);
    await page.locator('.mobile-occasion-navigation').getByRole('link',{name:'Home',exact:true}).waitFor();
    assert.equal(await page.locator('.mobile-occasion-navigation a').first().getAttribute('href'),'/','celebration back respects flag OFF with a stored branch');
    await page.evaluate(()=>localStorage.removeItem('gokul-selected-branch'));await page.reload();
    await page.locator('.mobile-occasion-navigation').getByRole('link',{name:'Home',exact:true}).waitFor();
    assert.equal(await page.locator('.mobile-occasion-navigation a').first().getAttribute('href'),'/','flag OFF without a branch goes home');
    branchExperience=true;await page.reload();
    await page.locator('.mobile-occasion-navigation').getByRole('link',{name:'All branches',exact:true}).waitFor();
    assert.equal(await page.locator('.mobile-occasion-navigation a').first().getAttribute('href'),'/branches');
    await page.goto(`${base}/profile`);
    branchExperience=true;await page.evaluate(()=>localStorage.removeItem('gokul-selected-branch'));await page.reload();
    await page.locator('.mobile-account-back').getByRole('link',{name:'All branches',exact:true}).waitFor();
    assert.equal(await page.locator('.mobile-account-back').getByRole('link',{name:'All branches',exact:true}).getAttribute('href'),'/branches');
    await page.evaluate(branch=>{localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));localStorage.setItem('gokul-language','hi');},otherBranch);await page.reload();
    await page.locator('.mobile-account-back').getByRole('link',{name:'शाखा का मुख्य पृष्ठ',exact:true}).waitFor();
    await page.locator('.mobile-account-links').getByRole('link',{name:'गोकुल स्वीट्स के बारे में',exact:true}).waitFor();
    await page.locator('.mobile-account-back').getByRole('link',{name:'मेन्यू पर वापस जाएँ',exact:true}).waitFor();
    await page.goto(`${base}/occasions`);await page.locator('.mobile-occasion-navigation').getByRole('link',{name:'अनुरोध और मूल्य प्रस्ताव',exact:true}).waitFor();
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false,'Hindi navigation stays contained');
    await page.evaluate(()=>localStorage.setItem('gokul-language','en'));await page.goto(`${base}/profile`);await page.locator('.account-hub').waitFor();
   }
   await page.locator('.mobile-account-back a[href="/menu"]').click();await page.waitForURL('**/menu');
   await page.locator('.customer-bottom-navigation a[href="/profile"]').click();await page.waitForURL('**/profile');await page.locator('.account-hub').waitFor();
   const logoutDialog=page.getByRole('dialog',{name:'Log out of Gokul?'});
   await page.locator('.mobile-account-logout').click();await logoutDialog.waitFor();
   assert.equal(await logoutDialog.getByRole('button',{name:'Stay signed in'}).evaluate(e=>e===document.activeElement),true);
   await logoutDialog.getByRole('button',{name:'Stay signed in'}).click();assert.equal(logoutCalls,0);assert.equal(authenticated,true);
   await page.locator('.mobile-account-logout').click();await page.keyboard.press('Escape');assert.equal(logoutCalls,0);
   await page.locator('.mobile-account-logout').click();await page.mouse.click(2,2);await logoutDialog.waitFor({state:'hidden'});assert.equal(logoutCalls,0);
   await page.locator('.mobile-account-logout').click();await logoutDialog.getByRole('button',{name:'Log out',exact:true}).click();
   await logoutDialog.getByRole('alert').filter({hasText:'Could not sign out'}).waitFor();assert.equal(authenticated,true);assert.equal(logoutCalls,1);
   logoutError=false;await logoutDialog.getByRole('button',{name:'Log out',exact:true}).click();await page.locator('.account-hub').waitFor({state:'detached'});assert.equal(logoutCalls,2);
   await page.locator('.customer-account-mobile-label').getByText('Log in',{exact:true}).waitFor();
   assert.equal(await page.locator('.mobile-account-navigation').isVisible(),true,'public menu remains after logout');
   assert.equal(await page.locator('.account-navigation').count(),0,'private profile sections disappear');
   await page.getByRole('button',{name:'Verify with SMS',exact:true}).click();await page.locator('.account-hub').waitFor();
   await page.locator('.customer-account-mobile-label').getByText('VC',{exact:true}).waitFor();
   await page.getByRole('button',{name:'Edit details',exact:true}).click();await page.locator('#account-name-edit').waitFor();
   assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
   const largeText=await page.addStyleTag({content:'html{font-size:20px}'});
   assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false,'larger root text remains contained');
   await largeText.evaluate(e=>e.remove());
   if(process.env.SCREENSHOT_DIR)await page.screenshot({path:`${process.env.SCREENSHOT_DIR}/profile-${width}.png`,fullPage:true});
   await page.goto(`${base}/cart`);await page.waitForURL('**/checkout/mobile');await page.locator('.mobile-empty-cart').waitFor();await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});
   assert.equal(await page.locator('.mobile-checkout-nav').getByRole('link',{name:'Branch home',exact:true}).getAttribute('href'),'/branches/2');
   if(width===390){
    branchExperience=false;await page.reload();await page.locator('.mobile-empty-cart').waitFor();
    assert.equal(await page.locator('.mobile-checkout-nav a[href^="/branches"]').count(),0,'empty cart does not advertise disabled branch home');
    assert.equal(await page.getByRole('link',{name:'Back to menu',exact:true}).isVisible(),true);
    branchExperience=true;await page.reload();await page.locator('.mobile-checkout-nav').getByRole('link',{name:'Branch home',exact:true}).waitFor();
   }
   const browse=page.getByRole('link',{name:'Browse menu',exact:true});assert.ok((await browse.boundingBox()).height>=48);
   assert.equal(await page.getByRole('link',{name:'Back to menu',exact:true}).isVisible(),true);
   if(process.env.SCREENSHOT_DIR)await page.screenshot({path:`${process.env.SCREENSHOT_DIR}/empty-cart-${width}.png`,fullPage:true});
   await browse.click();await page.waitForURL('**/menu');
  }
  if(!compact){
   await page.getByRole('button',{name:'Profile details',exact:true}).click();
   const signout=page.locator('.account-details-signout'),dialog=page.getByRole('dialog',{name:'Log out of Gokul?'});
   await signout.click();await dialog.waitFor();assert.equal(logoutCalls,0,'desktop opening does not send logout');
   assert.equal(await dialog.getByRole('button',{name:'Stay signed in'}).evaluate(e=>e===document.activeElement),true);
   await dialog.getByRole('button',{name:'Stay signed in'}).click();assert.equal(logoutCalls,0);
   await signout.click();await page.keyboard.press('Escape');await dialog.waitFor({state:'hidden'});assert.equal(logoutCalls,0);
   await signout.click();await page.mouse.click(2,2);await dialog.waitFor({state:'hidden'});assert.equal(logoutCalls,0);
   await signout.click();await dialog.getByRole('button',{name:'Log out',exact:true}).click();await dialog.getByRole('alert').filter({hasText:'Could not sign out'}).waitFor();assert.equal(authenticated,true);assert.equal(logoutCalls,1);
   logoutError=false;await dialog.getByRole('button',{name:'Log out',exact:true}).click();await page.locator('.account-hub').waitFor({state:'detached'});assert.equal(authenticated,false);assert.equal(logoutCalls,2);
  }
  assert.equal(orders,0,'navigation, profile and presentation never place orders');
  await context.close();console.log(`Mobile pages/account ${width}px theme=${themed} passed`);
 }
}finally{await browser.close();}
