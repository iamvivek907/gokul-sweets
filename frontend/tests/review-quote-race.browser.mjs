import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const browser=await chromium.launch({headless:true}),base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
const date=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata'}).format(new Date(Date.now()+86400000));
const branch={id:1,code:'TEST',name:'Test branch',active:true,pickupAvailable:true,address:'Test address'};
const main={id:1,name:'Test meal',categoryId:1,categoryName:'Meals',price:100,available:true,saleMode:'UNIT',imageUrl:null},addon={...main,id:2,name:'Test drink',price:40};
const slot={id:1,branchId:1,slotDate:date,startTime:'18:00:00',endTime:'19:00:00',active:true,remainingCapacity:50};
const context=await browser.newContext({viewport:{width:390,height:900},serviceWorkers:'block'}),page=await context.newPage();page.setDefaultTimeout(15000);
let releaseQuote,quoteStarted,quoteCalls=0,orders=0,payments=0;
const quoteGate=new Promise(r=>releaseQuote=r),started=new Promise(r=>quoteStarted=r),errors=[];page.on('pageerror',e=>errors.push(e.message));
const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Methods':'GET,POST,OPTIONS','Access-Control-Allow-Headers':'content-type,idempotency-key'};
try{
 await context.route('**/api/**',async route=>{const request=route.request(),p=new URL(request.url()).pathname;let json=[];
  if(request.method()==='OPTIONS')return route.fulfill({status:204,headers});
  if(p==='/api/storefront/features')json={futuristicStorefrontV2:true,checkoutExperienceV2:true,contextualStorefrontV2:true,branchExperience:true,acceptedCheckoutQuote:true,pickupAddOns:true,simplifiedCheckout:false,smartAvailability:false,today:date,futureOrderingDays:30};
  else if(p==='/api/storefront/customer-identity')json={enabled:false,guestCheckoutEnabled:true};
  else if(p==='/api/branches')json=[branch];else if(p==='/api/branches/1')json=branch;
  else if(p==='/api/menu')json=[{id:1,name:'Meals',products:[main,addon]}];
  else if(p==='/api/menu/pickup-addons')json=[{product:addon,weightGrams:null,portionPrice:40,portionTotal:40,reason:'An optional drink'}];
  else if(p==='/api/menu/pickup-addons/check')json={orderable:true};
  else if(p.includes('inventory'))json={enforcementEnabled:false,orderable:true,items:[]};
  else if(p==='/api/orders/quote'){const call=++quoteCalls,body=request.postDataJSON(),total=body.items.reduce((sum,item)=>sum+(item.productId===1?100:40)*item.quantity,0);if(call===1){quoteStarted();await quoteGate;}json={token:`quote-${call}`,subtotal:String(total),taxAmount:'0',priorityCharge:'0',totalAmount:String(total),currency:'INR',items:[],expiresAt:new Date(Date.now()+600000).toISOString()};}
  else if(p==='/api/orders'&&request.method()==='POST')orders++;
  else if(p==='/api/payments'&&request.method()==='POST')payments++;
  try{return await route.fulfill({json,headers});}catch{/* A newer cart may abort the obsolete response. */}
 });
 await context.addInitScript(({branch,main,slot,date})=>{localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));localStorage.setItem('gokul-cart',JSON.stringify({branchId:1,items:[{product:main,quantity:1,weightGrams:null}]}));localStorage.setItem('gokul-selected-pickup-slot',JSON.stringify({date,slot,pickupType:'NORMAL'}));localStorage.setItem('gokul-customer-details',JSON.stringify({name:'Test customer',phone:'9000000000'}));},{branch,main,slot,date});
 await page.goto(`${base}/checkout/review`);await page.getByRole('button',{name:'Add Test drink',exact:true}).click();await started;
 await page.evaluate(()=>{const cart=JSON.parse(localStorage.getItem('gokul-cart'));cart.items.find(i=>i.product.id===1).quantity=2;localStorage.setItem('gokul-cart',JSON.stringify(cart));window.dispatchEvent(new Event('gokul-cart-change'));});
 releaseQuote();await page.getByRole('button',{name:'Check price & offers',exact:true}).waitFor();await page.waitForFunction(()=>!Array.from(document.querySelectorAll('button')).find(b=>b.textContent==='Check price & offers')?.disabled);
 assert.equal(await page.getByText('Total before optional offers',{exact:false}).count(),0,'obsolete quote is not displayed for the changed cart');
 await page.getByRole('button',{name:'Check price & offers',exact:true}).click();await page.getByText('Total before optional offers',{exact:false}).waitFor();assert.match(await page.getByText('Total before optional offers',{exact:false}).textContent(),/240/);
 assert.equal(orders,0);assert.equal(payments,0);assert.deepEqual(errors,[]);console.log('PASS: standard review discards a delayed add-on quote after cart changes and shows only the refreshed current total.');
}finally{await context.close();await browser.close();}
