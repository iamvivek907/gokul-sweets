import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const browser=await chromium.launch({headless:true}),base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
try{
 for(const width of [390,1280]){
  const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:'block'}),page=await context.newPage();
  await context.route('**/api/**',async route=>{
   const request=route.request(),path=new URL(request.url()).pathname,headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Headers':'content-type,x-staff-csrf','Access-Control-Allow-Methods':'GET,OPTIONS','Access-Control-Expose-Headers':'X-Staff-CSRF'};
   if(request.method()==='OPTIONS')return route.fulfill({status:204,headers});
   let json=[];
   if(path==='/api/admin/auth/me')json={staffId:77,username:'owner',fullName:'Owner',roleName:'OWNER_ADMIN',branchIds:[1],permissions:['MENU_MANAGE','ORDER_VIEW']};
   else if(path==='/api/admin/loyalty')json={enabled:false,reservedDiscountRupees:75,normalMaximumCostPercent:2.5,liability:[{redemptionPercent:100,rupees:25}],accounts:[],rewards:[],orderMetrics:[],adjustments:[],exclusions:{productIds:[],rebateCodes:[]}};
   else if(path==='/api/storefront/features')json={};
   return route.fulfill({json,headers:{...headers,'X-Staff-CSRF':'test-csrf'}});
  });
  await page.goto(`${base}/admin/loyalty`);
  const exposure=page.getByText('Active reward reservations:',{exact:false});await exposure.waitFor();
  assert.match(await exposure.innerText(),/₹75.00/);assert.match(await exposure.innerText(),/additional to the available coin liability/);
  assert.match(await page.getByRole('heading',{name:'Outstanding coin liability'}).locator('..').innerText(),/₹25.00/);
  console.log(`Loyalty admin liability passed at ${width}px`);await context.close();
 }
}finally{await browser.close();}
