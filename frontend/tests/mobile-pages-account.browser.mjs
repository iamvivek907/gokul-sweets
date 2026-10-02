import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {mkdir} from 'node:fs/promises';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const browser=await chromium.launch({headless:true}),base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
const branch={id:1,code:'TEST',name:'Gokul Tamkuhi Road with a long branch name',active:true,pickupAvailable:true,address:'Test address',phone:'9876543210'};
const otherBranch={...branch,id:2,code:"OTHER",name:"Other Gokul branch"};
const sweet={id:1,name:'Gulab Jamun',description:'Fresh sweets for your celebration',saleMode:'WEIGHT',occasionOnly:false,published:true,leadDays:2,pieceGrams:50,categoryName:'Sweets',unitPrice:300,taxPercent:5,imageUrl:null};
const today=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata'}).format(new Date());
try{
 for(const [width,themed] of [[320,true],[390,true],[601,true],[640,true],[641,true],[1280,true],[390,false]]){
  let authenticated=true,logoutError=true,staffChecks=0,orders=0,customerName='Vivek Chaurasia',branchExperience=true;
  const context=await browser.newContext({viewport:{width,height:844},serviceWorkers:'block',timezoneId:'America/Los_Angeles'}),page=await context.newPage();
  await context.addInitScript(branch=>{if(!sessionStorage.getItem('test-branch-initialized')){localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));sessionStorage.setItem('test-branch-initialized','true');}window.initSendOTP=config=>config.success({accessToken:'test-provider-proof'});},branch);
  await context.route('**/api/**',async route=>{
   const req=route.request(),path=new URL(req.url()).pathname;let json=[];
   if(path==='/api/storefront/features')json={futuristicStorefrontV2:themed,checkoutExperienceV2:themed,simplifiedCheckout:themed,acceptedCheckoutQuote:themed,customerAccountHub:true,notificationInbox:true,branchExperience,occasionEnquiries:true,today};
   else if(path==='/api/storefront/customer-identity')json={enabled:true,guestCheckoutEnabled:false};
   else if(path==='/api/customer/identity/me')json={authenticated,name:customerName,phone:'+919876543210'};
   else if(path==='/api/customer/identity/start')return route.fulfill({status:204});
   else if(path==='/api/customer/identity/exchange'){authenticated=true;json={authenticated,name:customerName,phone:'+919876543210'};}
   else if(path==='/api/customer/identity/logout'){if(logoutError)return route.fulfill({status:503,json:{message:'Unavailable'}});authenticated=false;return route.fulfill({status:204});}
   else if(path==='/api/customer/identity/notifications')json={messages:[],unreadCount:1,nextBefore:null,readThrough:0};
   else if(path==='/api/customer/identity/account')json={paidOrders:1,favouriteProductIds:[],addresses:[],preferences:{dietaryNotes:null,preferredBranchId:null}};
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
  if(process.env.SCREENSHOT_DIR&&compact){await mkdir(process.env.SCREENSHOT_DIR,{recursive:true});await page.screenshot({path:`${process.env.SCREENSHOT_DIR}/branch-home-${width}.png`,fullPage:true});}
  await page.goto(`${base}/branches/2`);await page.locator('.branch-rated-grid article').waitFor();await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});
  const celebration=page.getByRole('button',{name:'Celebrations & Gifts · Other Gokul branch',exact:true});
  assert.equal(await celebration.locator('.mobile-celebration-label').isVisible(),compact);
  assert.equal(await celebration.locator('.desktop-celebration-label').isVisible(),!compact);
  await celebration.click();await page.waitForURL('**/occasions');
  assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-selected-branch')).id),2,'celebration tab selects its own branch');
  await page.goto(`${base}/occasions`);await page.getByLabel('Gulab Jamun quantity').waitFor();await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});
  assert.equal(await page.locator('.mobile-occasion-navigation').isVisible(),compact);
  assert.equal(await page.locator('.occasion-product').evaluate(e=>getComputedStyle(e).display),compact?'block':themed?'flex':'block');
  await page.getByLabel('Gulab Jamun quantity').fill('2.5');
  await page.getByLabel('Gulab Jamun unit').selectOption('PIECE');
  assert.equal(await page.getByLabel('Gulab Jamun quantity').inputValue(),'0','unit changes preserve the existing reset behavior');
  await page.getByLabel('Gulab Jamun quantity').fill('10');
  await page.getByText('1 items · 0 packing groups',{exact:true}).waitFor();
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
  if(process.env.SCREENSHOT_DIR&&compact){await mkdir(process.env.SCREENSHOT_DIR,{recursive:true});await page.screenshot({path:`${process.env.SCREENSHOT_DIR}/celebrations-${width}.png`,fullPage:true});}
  await page.locator('.customer-account-link').click();await page.waitForURL('**/profile');await page.locator('.account-hub').waitFor();
  assert.equal(await page.locator('.mobile-account-navigation').isVisible(),compact);
  assert.equal(await page.locator('.mobile-account-logout').isVisible(),compact);
  assert.equal(await page.locator('.mobile-account-links a[href="/admin"]').count(),0,'staff access remains role checked');
  assert.equal(staffChecks,compact?1:0,'desktop and flag OFF add no new staff checks');
  if(compact){
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
    branchExperience=true;await page.evaluate(()=>localStorage.removeItem('gokul-selected-branch'));await page.reload();
    await page.locator('.mobile-account-back').getByRole('link',{name:'All branches',exact:true}).waitFor();
    assert.equal(await page.locator('.mobile-account-back').getByRole('link',{name:'All branches',exact:true}).getAttribute('href'),'/branches');
    await page.evaluate(branch=>{localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));localStorage.setItem('gokul-language','hi');},otherBranch);await page.reload();
    await page.locator('.mobile-account-back').getByRole('link',{name:'शाखा का मुख्य पृष्ठ',exact:true}).waitFor();
    await page.getByText('मिठाइयाँ, नाश्ता और भोजन देखें',{exact:true}).waitFor();
    await page.getByText('अपना कार्ट देखें',{exact:true}).waitFor();
    await page.getByText('बड़ी मात्रा में मिठाइयों और उत्सव के उपहार बॉक्स की योजना बनाएँ',{exact:true}).waitFor();
    await page.locator('.mobile-account-links').getByRole('link',{name:'गोकुल स्वीट्स के बारे में',exact:true}).waitFor();
    await page.locator('.mobile-account-back').getByRole('link',{name:'मेन्यू पर वापस जाएँ',exact:true}).waitFor();
    await page.goto(`${base}/occasions`);await page.locator('.mobile-occasion-navigation').getByRole('link',{name:'अनुरोध और मूल्य प्रस्ताव',exact:true}).waitFor();
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false,'Hindi navigation stays contained');
    await page.evaluate(()=>localStorage.setItem('gokul-language','en'));await page.goto(`${base}/profile`);await page.locator('.account-hub').waitFor();
   }
   await page.locator('.mobile-account-back a[href="/menu"]').click();await page.waitForURL('**/menu');
   await page.locator('.customer-bottom-navigation a[href="/profile"]').click();await page.waitForURL('**/profile');await page.locator('.account-hub').waitFor();
   await page.locator('.mobile-account-logout').click();await page.getByRole('status').filter({hasText:'Could not sign out'}).waitFor();assert.equal(authenticated,true);
   logoutError=false;await page.locator('.mobile-account-logout').click();await page.locator('.account-hub').waitFor({state:'detached'});
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
   const browse=page.getByRole('link',{name:'Browse menu',exact:true});assert.ok((await browse.boundingBox()).height>=48);
   assert.equal(await page.getByRole('link',{name:'Back to menu',exact:true}).isVisible(),true);
   if(process.env.SCREENSHOT_DIR)await page.screenshot({path:`${process.env.SCREENSHOT_DIR}/empty-cart-${width}.png`,fullPage:true});
   await browse.click();await page.waitForURL('**/menu');
  }
  assert.equal(orders,0,'navigation, profile and presentation never place orders');
  await context.close();console.log(`Mobile pages/account ${width}px theme=${themed} passed`);
 }
}finally{await browser.close();}
