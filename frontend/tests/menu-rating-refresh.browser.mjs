import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const base=process.env.BROWSER_BASE??'http://127.0.0.1:3311',browser=await chromium.launch({headless:true});
try{for(const width of [390,1280]){
 const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:'block'}),page=await context.newPage();page.setDefaultTimeout(10000);
 const branch={id:1,name:'Main',active:true,operational:true,pickupAvailable:true};
 const products=Array.from({length:101},(_,i)=>({id:i+1,name:i===0?'Peda':`Sweet ${i+1}`,categoryId:1,categoryName:'Sweets',price:40,available:true,saleMode:'UNIT'}));
 let fail=false,removed=false,ratingReads=0;
 const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Headers':'content-type','Access-Control-Allow-Methods':'GET,POST,OPTIONS'};
 await context.addInitScript(branch=>{localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));localStorage.setItem('gokul-ordering-tour:v1','seen');},branch);
 await context.route('**/api/**',async route=>{
  const path=new URL(route.request().url()).pathname;let json=[];
  if(route.request().method()==='OPTIONS')return route.fulfill({status:204,headers});
  if(path==='/api/storefront/features')json={futuristicStorefrontV2:true,checkoutExperienceV2:true,contextualStorefrontV2:true};
  else if(path==='/api/branches')json=[branch];else if(path==='/api/branches/1')json=branch;
  else if(path==='/api/menu/catalog')return route.fulfill({status:503,headers,json:{message:'No display cache'}});
  else if(path==='/api/menu'){if(fail)return route.fulfill({status:503,headers,json:{message:'Temporary outage'}});json=[{id:1,name:'Sweets',products}];}
  else if(path==='/api/reviews/product-summaries'){ratingReads++;const ids=route.request().postDataJSON().productIds;json=ids.includes(1)?(removed?[]:[{productId:1,averageRating:4.4,ratingCount:5}]):[{productId:101,averageRating:removed?4.6:4.8,ratingCount:6}];}
  else if(path==='/api/storefront/customer-identity')json={enabled:false};
  else if(path==='/api/menu/portion-groups')json={groups:[]};
  return route.fulfill({headers,json});
 });
 await page.goto(base+'/menu');await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});await page.locator('#gokul-product-1 .product-rating-summary').waitFor();await page.locator('#gokul-product-101 .product-rating-summary').waitFor();
 const savedCart=await page.evaluate(()=>localStorage.getItem('gokul-cart'));
 fail=true;
 await page.evaluate(branch=>{localStorage.setItem('gokul-selected-branch',JSON.stringify({...branch,name:'Main updated'}));window.dispatchEvent(new Event('storage'));},branch);
 await page.locator('.menu-refresh-notice').waitFor();
 fail=false;removed=true;await page.locator('.menu-refresh-notice button').click();await page.waitForFunction(()=>!document.querySelector('.menu-refresh-notice'));await page.locator('#gokul-product-101 .product-rating-summary').filter({hasText:'4.6'}).waitFor();
 await page.locator('#gokul-product-1 .product-rating-summary').waitFor({state:'hidden'});
 assert.equal(ratingReads,4);
 assert.equal(await page.locator('#gokul-product-1 .product-rating-summary').count(),0,'a successful empty response removes the previous rating');
 assert.equal(await page.locator('#gokul-product-101 .product-rating-summary').count(),1,'other batches retain their fresh ratings');
 assert.equal(await page.evaluate(()=>localStorage.getItem('gokul-cart')),savedCart);
 await context.close();console.log(`Rating removal and independent batch refresh ${width}px passed`);
}}finally{await browser.close();}
