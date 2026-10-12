import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright'),base=process.env.BROWSER_BASE??'http://127.0.0.1:3311',browser=await chromium.launch({headless:true});
try{for(const width of [390,1280]){
 const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:'block'}),page=await context.newPage();let saves=[],role='OWNER_ADMIN';
 const row={id:1,version:1,code:'REGULAR',name:'Regular',description:'Thanks for visiting',required_orders:5,minimum_subtotal:0,bonus_percent:0,appearance:'GOLD',active:true};
 await context.route('**/api/**',async route=>{
  const req=route.request(),path=new URL(req.url()).pathname,headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Headers':'content-type,x-staff-csrf','Access-Control-Allow-Methods':'GET,POST,PUT,OPTIONS','Access-Control-Expose-Headers':'X-Staff-CSRF','X-Staff-CSRF':'test-csrf'};if(req.method()==='OPTIONS')return route.fulfill({status:204,headers});let json=[];
  if(path==='/api/admin/auth/me')json={staffId:77,username:'owner',fullName:'Owner',roleName:role,branchIds:[1],permissions:['MENU_MANAGE']};
  else if(path==='/api/admin/loyalty/badges'){
   if(req.method()==='PUT'){const body=req.postDataJSON();saves.push(body);assert.equal(body.id,1);assert.equal(body.version,row.version);Object.assign(row,{version:row.version+1,name:body.name,required_orders:body.requiredOrders,minimum_subtotal:body.minimumSubtotal,bonus_percent:body.bonusPercent,active:body.active});return route.fulfill({status:204,headers});}
   json=[row];
  }else if(path==='/api/admin/loyalty')json={enabled:true,reservedDiscountRupees:0,normalMaximumCostPercent:2.5,liability:[],accounts:[],rewards:[],orderMetrics:[],adjustments:[],exclusions:{productIds:[],rebateCodes:[]}};
  else if(path==='/api/storefront/features')json={};await route.fulfill({json,headers});
 });
 await page.goto(`${base}/admin/loyalty`);const settings=page.locator('#badge-settings');await settings.getByRole('button',{name:'Edit',exact:true}).click();
 await settings.getByLabel('Completed paid orders required',{exact:true}).fill('10');await settings.getByLabel('Minimum qualifying subtotal per order (₹)',{exact:true}).fill('500');await settings.getByLabel('Additional coins (%)',{exact:true}).fill('20');await settings.getByLabel('Audit reason',{exact:true}).fill('Owner approved regular benefit');
 assert.match(await settings.getByText('Customer preview').locator('..').innerText(),/adds 5 coins/);await settings.getByRole('button',{name:'Save badge',exact:true}).click();await page.waitForFunction(()=>document.querySelector('#badge-settings tbody')?.textContent.includes('+20%'));
 assert.equal(saves[0].minimumSubtotal,500);assert.equal(saves[0].requiredOrders,10);assert.equal(saves[0].bonusPercent,20);assert.equal(saves[0].reason,'Owner approved regular benefit');
 await page.reload();await settings.getByRole('button',{name:'Edit',exact:true}).click();assert.equal(await settings.getByLabel('Minimum qualifying subtotal per order (₹)',{exact:true}).inputValue(),'500');assert.equal(await settings.getByLabel('Additional coins (%)',{exact:true}).inputValue(),'20');
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);role='BRANCH_ADMIN';await page.reload();await page.getByText('Owner access is required for funded loyalty controls.').waitFor();assert.equal(await page.locator('#badge-settings').count(),0);assert.equal(saves.length,1);
 console.log(`Owner badge controls, saved thresholds and role guards passed at ${width}px`);await context.close();
}}finally{await browser.close();}
