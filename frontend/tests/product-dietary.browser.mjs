import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const browser=await chromium.launch({headless:true}),base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
const branch={id:1,name:'Gokul Test',active:true,operational:true,pickupAvailable:true};
const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Headers':'content-type,x-staff-csrf','Access-Control-Allow-Methods':'GET,POST,PATCH,OPTIONS','Access-Control-Expose-Headers':'X-Staff-CSRF','X-Staff-CSRF':'test-csrf'};
try {
 for(const width of [390,1280]){
  const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:'block'}),page=await context.newPage();
  const item={branchProductId:1,productId:1,code:'BISCUIT',name:'Egg biscuit',categoryId:1,categoryName:'Snacks',basePrice:40,effectivePrice:40,available:true,active:true,vegetarian:true,saleMode:'UNIT',productVersion:0,branchVersion:0,policy:null,allocation:null};
  let calls=0,conflict=true;
  await context.route('**/api/**',async route=>{
   const req=route.request(),path=new URL(req.url()).pathname;let json=[];
   if(req.method()==='OPTIONS')return route.fulfill({status:204,headers});
   if(path==='/api/admin/auth/me')json={staffId:1,username:'owner',roleName:'OWNER_ADMIN',branchIds:[1],permissions:['MENU_MANAGE','INVENTORY_VIEW']};
   else if(path==='/api/branches')json=[branch];
   else if(path.endsWith('/workspace/1/dietary')){
    calls++;const body=req.postDataJSON();assert.equal(req.method(),'PATCH');assert.equal(req.headers()['x-staff-csrf'],'test-csrf');assert.deepEqual(body,{version:item.productVersion,vegetarian:false});
    if(conflict)return route.fulfill({status:409,headers,json:{message:'This item changed. Reload before saving.'}});
    item.vegetarian=false;item.productVersion++;return route.fulfill({status:204,headers});
   }
   else if(path.endsWith('/workspace/groups'))json={version:0,groups:[],total:0,page:0,totalPages:0};
   else if(path.endsWith('/workspace'))json={content:[item],totalElements:1,page:0,totalPages:1,categories:[{id:1,name:'Snacks'}],branchCategories:[{id:1,name:'Snacks'}],taxes:[]};
   else if(path==='/api/storefront/features')json={};
   await route.fulfill({json,headers});
  });
  await page.goto(`${base}/admin/menu/workspace`);const type=page.getByLabel('Egg biscuit food type',{exact:true});await type.waitFor();
  assert.equal(await type.inputValue(),'true');await type.selectOption('false');await page.getByRole('alert').filter({hasText:'Reload before saving.'}).waitFor();
  assert.equal(await type.inputValue(),'true','failed writes retain the confirmed classification');assert.equal(calls,1);
  conflict=false;await page.getByRole('button',{name:'Retry',exact:true}).click();await page.waitForFunction(()=>!document.querySelector('[aria-label="Egg biscuit food type"]').disabled);
  await type.selectOption('false');await page.waitForFunction(()=>document.querySelector('[aria-label="Egg biscuit food type"]')?.value==='false');
  assert.equal(calls,2);await page.reload();await type.waitFor();assert.equal(await type.inputValue(),'false');
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);await context.close();console.log(`Dietary admin ${width}px passed`);
 }
 for(const width of [320,390,640,1280])for(const modern of [true,false]){
  if(!modern&&width!==390)continue;
  const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:'block'}),page=await context.newPage();
  const products=['Veg snack','Egg biscuit','Small pack','Large egg pack'].map((name,index)=>({id:index+1,name,categoryId:1,categoryName:'Snacks',price:40,available:true,saleMode:'UNIT',vegetarian:index%2===0,imageUrl:null}));
  const group={key:'packs',title:'Biscuit packs',choices:[{productId:3,label:'Small'},{productId:4,label:'Large'}]};
  await context.route('**/api/**',async route=>{
   const req=route.request(),path=new URL(req.url()).pathname;let json=[];
   if(req.method()==='OPTIONS')return route.fulfill({status:204,headers});
   if(path==='/api/storefront/features')json={futuristicStorefrontV2:modern,checkoutExperienceV2:modern,contextualStorefrontV2:modern};
   else if(path==='/api/branches')json=[branch];else if(path==='/api/branches/1')json=branch;
   else if(path==='/api/menu')json=[{id:1,name:'Snacks',products}];
   else if(path==='/api/menu/portion-groups')json={groups:[group]};
   else if(path==='/api/storefront/customer-identity')json={enabled:false};
   await route.fulfill({json,headers});
  });
  await context.addInitScript(branch=>{localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));localStorage.setItem('gokul-ordering-guide-seen-v1','true');},branch);
  await page.goto(`${base}/menu`);await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});
  const veg=page.locator('#gokul-product-1'),nonveg=page.locator('#gokul-product-2');
  await veg.getByRole('img',{name:'Veg',exact:true}).waitFor();await nonveg.getByRole('img',{name:'Non-veg',exact:true}).waitFor();
  assert.equal(await veg.locator('[data-dietary="veg"] circle').count(),1);assert.equal(await nonveg.locator('[data-dietary="non-veg"] path').count(),1);
  if(modern&&width<=640){
   const card=page.locator('.mobile-portion-card').filter({has:page.getByRole('heading',{name:'Biscuit packs',exact:true})});
   await card.getByText('Varies by option',{exact:true}).waitFor();assert.equal(await card.locator('[data-dietary]').count(),2);
   await card.getByRole('button',{name:'Choose options for Biscuit packs',exact:true}).click();
   const dialog=page.getByRole('dialog',{name:'Biscuit packs',exact:true});
   await dialog.getByRole('img',{name:'Veg',exact:true}).waitFor();await dialog.getByRole('img',{name:'Non-veg',exact:true}).waitFor();await dialog.getByRole('button',{name:'Done',exact:true}).click();
  }
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
  if(width===390&&modern){await page.evaluate(()=>{localStorage.setItem('gokul-language','hi');});await page.reload();await page.locator('#gokul-product-2').getByRole('img',{name:'मांसाहारी',exact:true}).waitFor();}
  await context.close();console.log(`Dietary customer ${width}px modern=${modern} passed`);
 }
} finally {await browser.close();}
