import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {mkdir} from 'node:fs/promises';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const browser=await chromium.launch({headless:true}),base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
const branch={id:1,code:'TEST',name:'Gokul Tamkuhi Road with a long branch name',active:true,pickupAvailable:true,address:'Test address',phone:'9876543210',operational:true,openingTime:'08:00:00',closingTime:'21:30:00',coverImageUrl:'/arrival-mithai.webp',coverAltText:'Sweets prepared at this branch'};
const otherBranch={...branch,id:2,code:"OTHER",name:"Other Gokul branch",pickupAvailable:false,coverImageUrl:"/missing-branch-cover.png",openingTime:null,closingTime:null};
const sweet={id:1,name:'Gulab Jamun',description:'Fresh sweets for your celebration',saleMode:'WEIGHT',occasionOnly:false,published:true,leadDays:2,pieceGrams:50,categoryName:'Sweets',unitPrice:300,taxPercent:5,imageUrl:'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="600" height="400"%3E%3Crect width="600" height="400" fill="%23c76752"/%3E%3C/svg%3E'};
const today=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata'}).format(new Date());
try{
 for(const [width,themed] of [[320,true],[390,true],[640,true],[1280,true],[390,false]]){
  let authenticated=true,welcomeEnabled=false;const logoutError=true,customerName='Vivek Chaurasia',branchExperience=true,bulkFailure=false;
  const context=await browser.newContext({viewport:{width,height:844},serviceWorkers:'block',timezoneId:'America/Los_Angeles'}),page=await context.newPage();page.setDefaultTimeout(15000);
  await context.addInitScript(branch=>{if(!sessionStorage.getItem('test-branch-initialized')){localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));sessionStorage.setItem('test-branch-initialized','true');}localStorage.setItem('gokul-social-follow-popup-seen','true');window.initSendOTP=config=>config.success({accessToken:'test-provider-proof'});},branch);
  await context.route('**/api/**',async route=>{
   const req=route.request(),path=new URL(req.url()).pathname;let json=[];if(req.method()==='OPTIONS')return route.fulfill({status:204,headers:{'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Methods':'GET,POST,OPTIONS','Access-Control-Allow-Headers':'content-type,idempotency-key'}});
   if(path==='/api/storefront/features')json={preHomeIntentGateway:welcomeEnabled,futuristicStorefrontV2:themed,checkoutExperienceV2:themed,simplifiedCheckout:themed,acceptedCheckoutQuote:themed,customerAccountHub:true,notificationInbox:true,contextualStorefrontV2:true,gokulRewards:true,branchExperience,occasionEnquiries:true,today};
   else if(path==='/api/storefront/customer-identity')json={enabled:true,guestCheckoutEnabled:false};
   else if(path==='/api/customer/identity/me')json={authenticated,name:customerName,phone:'+919876543210'};
   else if(path==='/api/customer/identity/start')return route.fulfill({status:204});
   else if(path==='/api/customer/identity/exchange'){authenticated=true;json={authenticated,name:customerName,phone:'+919876543210'};}
   else if(path==='/api/customer/identity/logout'){if(logoutError)return route.fulfill({status:503,json:{message:'Unavailable'}});authenticated=false;return route.fulfill({status:204});}
   else if(path==='/api/customer/identity/notifications')json={messages:[],unreadCount:1,nextBefore:null,readThrough:0};
   else if(path==='/api/customer/identity/orders/page')json={nextBefore:null,orders:[{orderNumber:'GKS-LONG-OPAQUE-REFERENCE',customerOrderNumber:100,orderStatus:'CONFIRMED',branchId:1,branchName:branch.name,fulfillmentType:'PICKUP',pickupDate:today,totalAmount:250,createdAt:new Date().toISOString(),updatedAt:new Date().toISOString()},{orderNumber:'GKS-UNNUMBERED-REFERENCE-VERY-LONG',orderStatus:'CANCELLED',branchId:1,branchName:branch.name,fulfillmentType:'PICKUP',pickupDate:today,totalAmount:150,createdAt:new Date().toISOString(),updatedAt:new Date().toISOString()}]};
   else if(path==='/api/occasion-enquiries'&&bulkFailure)return route.fulfill({status:503,json:{message:'Unavailable'}});
   else if(path==='/api/occasion-enquiries')json=[{id:'11111111-1111-4111-8111-111111111111',branchId:1,occasionType:'Family celebration',serviceDate:today,guestCount:20,status:'REQUESTED',quotedAmount:null,depositAmount:null,paidAmount:0,nextStep:'Our branch is reviewing your request.',fulfilment:'PICKUP',items:[{productId:1,productName:'Gulab Jamun',quantity:20,unit:'PIECE'}],pricedLines:[],orderNumber:null,balancePaymentOpen:false}];
   else if(path==='/api/customer/identity/rewards')json={balance:42,pendingCoins:8,debt:0,nextExpiry:null,rewards:[],history:[],terms:'Earn from completed orders.',maximumRedemptionPercent:10};
   else if(path==='/api/menu')json=[{id:1,name:'Sweets',products:[{id:1,categoryId:1,categoryName:'Sweets',name:'Gulab Jamun',price:300,saleMode:'WEIGHT',minimumWeightGrams:250,weightStepGrams:50,imageUrl:null,available:true}]}];
   else if(path==='/api/menu/portion-groups')json={groups:[]};
   else if(path==='/api/menu/offers')json=[];
   else if(path==='/api/customer/identity/account')json={completedOrders:1,paidOrders:1,favouriteProductIds:[],addresses:[],preferences:{dietaryNotes:null,preferredBranchId:null}};
   else if(path==='/api/branches')json=[branch,otherBranch];else if(path==='/api/branches/1')json=branch;else if(path==='/api/branches/2')json=otherBranch;else if(path==='/api/branches/3')json={...branch,id:3,operational:false};
   else if(/^\/api\/branches\/[12]\/discovery$/.test(path))json={overallExperience:{average:4.8,count:2},offerings:[{title:'Fresh sweets',description:'Made at this branch'}],topRatedItems:[{productId:1,name:'Gulab Jamun',imageUrl:null,average:5,count:2,reviews:[]}]};
   else if(/^\/api\/branches\/[12]\/occasion-catalogue$/.test(path))json={sweets:[sweet],boxes:[],branding:{headline:'Wedding Dhamaka',description:'Exclusive for weddings',imageUrl:null,published:true}};
   else if(path==='/api/admin/auth/me'){return route.fulfill({status:401,json:{message:'Not signed in'}});}

   return route.fulfill({json,headers:{'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true'}});
  });

  await page.goto(`${base}/occasions/branches`);await page.getByRole('heading',{name:'Choose a branch for your occasion',exact:true}).waitFor();
  await page.getByRole('button',{name:`Plan at this branch · ${branch.name}`,exact:true}).waitFor();
  assert.equal(new URL(page.url()).pathname,'/occasions/branches','stored branch does not bypass explicit occasion selection');
  await page.getByRole('button',{name:`Plan at this branch · ${branch.name}`,exact:true}).click();await page.waitForURL('**/occasions');
  const errors=[];page.on('pageerror',error=>errors.push(error.message));
  const routes=[['/branches/1','.branch-home-hero'],['/menu','#gokul-product-1'],['/profile','.account-cover'],['/profile/rewards','.customer-rewards-page'],['/profile/orders','.profile-focused-route'],['/notifications','.notification-page-heading'],['/orders','h1:visible'],[width<=640&&themed?'/checkout/mobile':'/cart',width<=640&&themed?'.mobile-empty-cart':'h1:visible'],['/occasions','.occasion-hero'],['/occasions/requests','#occasion-tracker'],['/about','h1:visible'],['/careers','h1:visible'],['/cancellation-policy','h1:visible'],['/profile/privacy','h1:visible'],['/','h1:visible']];
  for(const [route,ready] of routes){
   console.log(`Checking customer design ${width}px themed=${themed} ${route}`);
   await page.goto(`${base}${route}`);await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});await page.locator(ready).first().waitFor();
   if(themed){
    const shell=page.locator('[data-customer-design=reference]');await shell.waitFor();
    assert.equal(await shell.evaluate(node=>getComputedStyle(node).getPropertyValue('--customer-brand').trim()),'#980c31',`${route} shares the maroon brand token`);
    if(width<=640&&!route.startsWith('/checkout/')&&route!=='/'){
     const nav=page.getByRole('navigation',{name:'Primary navigation',exact:true});assert.equal(await nav.locator('a').count(),4,`${route} has the same four navigation destinations`);
     assert.equal(await nav.evaluate(node=>getComputedStyle(node).borderRadius),'0px');
     for(const link of await nav.locator('a').all()){
      const active=await link.getAttribute('aria-current')==='page';
      assert.equal(await link.locator('svg').evaluate(node=>getComputedStyle(node).stroke),active?'rgb(152, 12, 49)':'rgb(100, 112, 128)',`${route} uses matching navigation icon colours`);
      assert.equal(await link.locator(':scope>span').first().evaluate(node=>getComputedStyle(node).backgroundColor),'rgba(0, 0, 0, 0)',`${route} removes old coloured icon tiles`);
     }
     assert.equal(await page.locator('.reference-wordmark').isVisible(),true,`${route} shares the wordmark`);
     assert.ok((await page.locator('.customer-site-header').boundingBox()).height<=60,`${route} keeps a compact header`);
    }
   }else assert.equal(await page.locator('[data-customer-design]').count(),0,'flag-OFF preserves the existing presentation');
   assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false,`${route} fits ${width}px`);
   if(route==='/branches/1'&&themed){
    assert.equal(await page.locator('.branch-overview').count(),1);assert.equal(await page.locator('.branch-home-actions').getByRole('link',{name:/Browse menu/}).count(),1);
    await page.getByRole('heading',{name:'Your order, ready to collect',exact:true}).waitFor();
    assert.equal(await page.locator('.branch-home .gokul-product-card').count(),0,'branch home introduces the branch without ordering cards');
    assert.match(await page.getByRole('link',{name:'Get directions',exact:false}).getAttribute('href'),/Test%20address/);
    await page.getByRole('button',{name:'Branch details',exact:true}).click();await page.locator('.branch-details-facts').waitFor();
    await page.getByRole('button',{name:'Home',exact:true}).click();await page.locator('.branch-rated-grid article').waitFor();
   }
   if(process.env.SCREENSHOT_DIR&&width===390&&themed){await mkdir(process.env.SCREENSHOT_DIR,{recursive:true});await page.evaluate(()=>window.scrollTo({top:0,behavior:'instant'}));await page.waitForTimeout(500);if(route==='/branches/1')await page.screenshot({path:`${process.env.SCREENSHOT_DIR}/branch-home.png`});await page.screenshot({path:`${process.env.SCREENSHOT_DIR}/customer-${route.replaceAll('/','-')||'welcome'}.png`,fullPage:true});}
  }
  if(width===390&&themed){
   await page.goto(`${base}/branches/2`);await page.locator('.branch-overview-monogram').waitFor();
   assert.equal(await page.locator('.branch-pickup-guide').count(),0,'pickup-unavailable branches do not advertise online collection');
   await page.getByText('Contact the branch for hours',{exact:true}).waitFor();
   await page.getByText('Explore the branch · Contact us to visit',{exact:true}).waitFor();
   const cart={branchId:1,items:[{product:{id:1,name:'Test sweet',price:300,saleMode:'UNIT',available:true},quantity:1,weightGrams:null}]};
   await page.evaluate(cart=>localStorage.setItem('gokul-cart',JSON.stringify(cart)),cart);await page.reload();
   await page.getByRole('button',{name:'Browse menu · Other Gokul branch',exact:true}).click();
   const switchDialog=page.getByRole('dialog');await switchDialog.getByRole('button',{name:'Keep my cart',exact:true}).click();
   assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-cart'))),cart,'declining a branch change preserves the cart');
   await page.getByRole('button',{name:'Browse menu · Other Gokul branch',exact:true}).click();await switchDialog.getByRole('button',{name:'Clear cart and switch',exact:true}).click();await page.waitForURL('**/menu');
   assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-selected-branch')).id),2);
   assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-cart'))?.items.length??0),0);
   await page.goto(`${base}/branches/3`);await page.getByRole('heading',{name:'Currently not operational',exact:true}).waitFor();assert.equal(await page.locator('.branch-overview').count(),0);
  }
  // Exercise the editorial entrance separately: the regular home uses different CSS.
  welcomeEnabled=true;
  await page.goto(`${base}/`);await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});
  const welcomeTitle=page.locator('#gokul-arrival-title');await welcomeTitle.waitFor();
  assert.equal(await welcomeTitle.evaluate(node=>getComputedStyle(node).color),'rgb(255, 250, 240)','welcome heading stays light over the dark photo');
  assert.equal(await welcomeTitle.locator('..').locator(':scope>p').evaluate(node=>getComputedStyle(node).color),'rgb(255, 250, 240)','welcome eyebrow stays light over the dark photo');
  const photoCard=page.locator('.arrival-pickup-card').first(),fallbackCard=page.locator('.arrival-pickup-card').nth(1);
  await photoCard.locator('img').waitFor();
  await fallbackCard.locator('img').waitFor({state:'detached'});
  if(width>640){
   for(const label of await photoCard.locator('.arrival-pickup-copy>span').all()){
    assert.equal(await label.evaluate(node=>getComputedStyle(node).color),'rgb(255, 255, 255)','photo-card labels retain light text over the dark scrim');
   }
   assert.equal(await fallbackCard.locator('.arrival-pickup-copy>span').first().evaluate(node=>getComputedStyle(node).color),'rgb(143, 24, 56)','fallback-card labels use maroon on the light surface');
  }
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false,'welcome fits the viewport');
  assert.deepEqual(errors,[]);await context.close();console.log(`Customer design ${width}px enabled=${themed}: 15 routes and editorial welcome passed`);
 }
}finally{await browser.close();}
