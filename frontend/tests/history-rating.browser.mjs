import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const browser=await chromium.launch({headless:true}),base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
const branch={id:1,name:'Test kitchen',code:'TEST',active:true,pickupAvailable:true};
try{for(const width of [320,390,1280]){
 const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:'block'}),page=await context.newPage();
 await context.addInitScript(branch=>{localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));localStorage.setItem('gokul-social-follow-popup-seen','true');},branch);
 const rows=Array.from({length:1000},(_,i)=>({orderNumber:`HISTORY-${1000-i}`,customerOrderNumber:1000-i,branchId:1,branchName:branch.name,orderStatus:'PICKED_UP',fulfillmentType:'PICKUP',pickupDate:'2026-10-13',pickupStartTime:'10:00:00',pickupEndTime:'10:30:00',pickupType:'NORMAL',createdAt:'2026-10-12T12:00:00',updatedAt:'2026-10-12T12:00:00',totalAmount:100,reviewRating:4}));
 let bulk=0,reviewReads=0,fail=false,emptyLater=false;const historyRequests=[];
 await context.route('**/api/**',async route=>{
  const req=route.request(),url=new URL(req.url()),path=url.pathname;let json=[];
  if(path==='/api/storefront/features')json={futuristicStorefrontV2:true,checkoutExperienceV2:true,simplifiedCheckout:true,acceptedCheckoutQuote:true,customerAccountHub:true,today:'2026-10-12'};
  else if(path==='/api/storefront/customer-identity')json={enabled:true};
  else if(path==='/api/customer/identity/me')json={authenticated:true,name:'Test customer',phone:'+919876543210'};
  else if(path==='/api/branches')json=[branch];else if(path==='/api/branches/1')json=branch;
  else if(path==='/api/customer/identity/orders'){bulk++;json=rows;}
  else if(path==='/api/customer/identity/orders/page'){
   if(fail)return route.fulfill({status:503,json:{message:'Unavailable'}});
   historyRequests.push(Object.fromEntries(url.searchParams));
   const before=url.searchParams.get('before'),after=url.searchParams.get('after');
   if(emptyLater && (before||after))return route.fulfill({json:{orders:[],nextBefore:null,previousAfter:null}});
   const start=before?rows.findIndex(o=>o.orderNumber===before)+1:after?Math.max(0,rows.findIndex(o=>o.orderNumber===after)-12):0;
   const orders=rows.slice(start,start+12);json={orders,nextBefore:start+12<rows.length?orders.at(-1).orderNumber:null,previousAfter:start>0?orders[0].orderNumber:null};
  }else if(path.endsWith('/review')){
   const ref=path.split('/').at(-2),order=rows.find(o=>o.orderNumber===ref);
   if(req.method()==='PUT')order.reviewRating=req.postDataJSON().overallRating;else reviewReads++;
   const review={id:1,overallRating:order.reviewRating,comment:null,status:'PUBLISHED',itemRatings:[]};json=req.method()==='PUT'?review:{eligible:true,products:[],review};
  }else if(/^\/api\/orders\/HISTORY-\d+$/.test(path))json={...rows.find(o=>o.orderNumber===path.split('/').at(-1)),paymentStatus:'PAID',items:[],customerName:'Test customer',maskedCustomerPhone:'******3210',subtotal:100,taxAmount:0,priorityCharge:0,convenienceFee:0,convenienceFeeTax:0,paymentFee:0,paymentFeeTax:0,paymentFeeRate:0,reservationExpiresAt:'2026-10-13T11:00:00',branchPhone:'9876543210'};
  return route.fulfill({json});
 });
 await page.goto(`${base}/orders`);await page.locator('.profile-order-card').first().waitFor();

 const dateFields=page.locator('input[type="date"]');
 async function checkFilters(){
  const a=await dateFields.nth(0).boundingBox(),b=await dateFields.nth(1).boundingBox();
  assert.ok(a.x>=0&&b.x>=0&&a.x+a.width<=width&&b.x+b.width<=width,'native date fields stay inside viewport');
  assert.ok(a.y+a.height<=b.y||b.y+b.height<=a.y||a.x+a.width+8<=b.x,'date fields retain a visible gap');
  for(const field of await dateFields.all())assert.equal(await field.evaluate(node=>node.getBoundingClientRect().width<=node.parentElement.getBoundingClientRect().width+1),true,'date input stays within its label');
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
 }
 await checkFilters();
 await page.addStyleTag({content:'input,button,label{font-size:20px!important}'});await checkFilters();
 const searchInput=page.getByLabel('Search order history',{exact:true});
 assert.equal(await searchInput.evaluate(node=>getComputedStyle(node).backgroundColor),'rgba(0, 0, 0, 0)');
 const nav=page.getByRole('navigation',{name:'Order history pages'});
 for(let n=2;n<=6;n++){await nav.getByRole('button',{name:'Next',exact:true}).click();await nav.getByText(`Page ${n}`,{exact:true}).waitFor();await page.locator('.profile-order-card').first().waitFor();}
 assert.equal(await page.locator('.profile-order-card').count(),12);assert.equal(bulk,0);assert.equal(reviewReads,0,'history has no per-card review reads');
 await page.reload();await nav.getByText('Page 6',{exact:true}).waitFor();
 await page.locator('.profile-order-card').first().getByRole('link',{name:/Your rating/}).click();
 await page.getByRole('button',{name:'Edit my feedback',exact:true}).click();await page.getByRole('group',{name:'Overall order rating'}).getByRole('button',{name:'5 stars',exact:true}).click();
 const submit=page.getByRole('button',{name:'Update Review',exact:true});assert.deepEqual(await submit.evaluate(el=>({color:getComputedStyle(el).color,background:getComputedStyle(el).backgroundColor})),{color:'rgb(255, 255, 255)',background:'rgb(143, 24, 56)'});
 await submit.click();await page.getByText('Thank you for your valuable feedback!',{exact:true}).waitFor();
 await page.getByRole(width<=640?'link':'button',{name:/Back|My orders/i}).first().click();await nav.getByText('Page 6',{exact:true}).waitFor();
 await page.locator('.profile-order-card').first().getByLabel('5 out of 5 stars',{exact:true}).waitFor();assert.equal(await page.locator('.profile-order-card').count(),12);
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
 fail=true;await page.evaluate(()=>window.dispatchEvent(new Event('online')));await page.getByRole('alert').filter({hasText:'could not refresh'}).waitFor();assert.equal(await nav.getByText('Page 6',{exact:true}).isVisible(),true);assert.equal(await page.locator('.profile-order-card').count(),12);
 fail=false;await page.getByRole('button',{name:'Retry',exact:true}).click();await page.getByRole('alert').filter({hasText:'could not refresh'}).waitFor({state:'hidden'});
 // A branch switch ignores the old scope immediately and must not revive it on navigation.
 await page.goto(`${base}/orders?scope=2&status=COMPLETED&q=peda&from=2026-10-01&to=2026-10-11&before=HISTORY-940&historyPage=6`);
 await page.locator('.profile-order-card').first().waitFor();
 assert.equal(await page.getByLabel('Search order history',{exact:true}).inputValue(),'');
 assert.equal(await page.getByRole('button',{name:'All',exact:true}).getAttribute('aria-pressed'),'true');
 const href=await page.locator('.profile-order-card').first().getByRole('link',{name:'View Order',exact:true}).getAttribute('href');
 const returnParams=new URL(new URL(href,base).searchParams.get('from'),base).searchParams;
 assert.equal(returnParams.get('scope'),'1');for(const key of ['q','status','from','to','before','after','historyPage'])assert.equal(returnParams.has(key),false);
 await nav.getByRole('button',{name:'Next',exact:true}).click();await nav.getByText('Page 2',{exact:true}).waitFor();
 const position=new URL(page.url()).searchParams;
 assert.equal(position.get('scope'),'1');for(const key of ['q','status','from','to','after'])assert.equal(position.has(key),false);
 assert.equal(historyRequests.at(-1).status,'ALL');assert.equal(historyRequests.at(-1).q,undefined);
 // An emptied Active page can recover to the newest matching work without discarding filters.
 await page.goto(`${base}/orders?scope=1&status=ACTIVE&q=peda&from=2026-10-01&to=2026-10-12&before=HISTORY-940&historyPage=6`);
 await page.locator('.profile-order-card').first().waitFor();emptyLater=true;
 await page.evaluate(()=>window.dispatchEvent(new Event('online')));
 await page.getByText('No matching orders. Try a different branch, status, date or search.',{exact:true}).waitFor();
 await page.getByRole('button',{name:'Latest orders',exact:true}).click();await nav.getByText('Page 1',{exact:true}).waitFor();
 await page.locator('.profile-order-card').first().waitFor();
 const recovered=new URL(page.url()).searchParams;
 assert.equal(recovered.get('status'),'ACTIVE');assert.equal(recovered.get('q'),'peda');assert.equal(recovered.get('from'),'2026-10-01');assert.equal(recovered.get('to'),'2026-10-12');assert.equal(recovered.has('before'),false);assert.equal(recovered.has('after'),false);
 console.log(`History/rating retention ${width}px passed`);await context.close();
}}finally{await browser.close();}
