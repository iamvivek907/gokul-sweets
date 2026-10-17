import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {mkdir} from 'node:fs/promises';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const base=process.env.BROWSER_BASE??'http://127.0.0.1:3311',browser=await chromium.launch({headless:true});
try { for(const width of [320,390,1280]) {
 const context=await browser.newContext({viewport:{width,height:960},serviceWorkers:'block'}),page=await context.newPage();let saved=[],offers=[];
 const choices=[{id:10,name:'Veg Burger',categoryId:1},{id:20,name:'Lassi',categoryId:2}];
 await context.route('**/api/**',async route=>{
  const req=route.request(),url=new URL(req.url()),path=url.pathname,headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Headers':'content-type,x-staff-csrf','Access-Control-Allow-Methods':'GET,POST,PUT,OPTIONS','Access-Control-Expose-Headers':'X-Staff-CSRF','X-Staff-CSRF':'test-csrf'};
  if(req.method()==='OPTIONS')return route.fulfill({status:204,headers});let json=[];
  if(path==='/api/admin/auth/me')json={staffId:77,username:'owner',fullName:'Owner',roleName:'OWNER_ADMIN',branchIds:[1],permissions:['REBATE_VIEW','REBATE_MANAGE']};
  else if(path==='/api/branches')json=[{id:1,name:'Gokul',active:true}];
  else if(path==='/api/admin/rebates/exclusion-options'){
   assert.equal(url.searchParams.get('branchId'),'1');const selected=(url.searchParams.get('selected')??'').split(',').map(Number),search=(url.searchParams.get('search')??'').toLowerCase();
   json={categories:[{id:1,name:'Snacks',categoryId:null},{id:2,name:'Beverages',categoryId:null}],products:choices.filter(p=>p.name.toLowerCase().includes(search)),selectedProducts:choices.filter(p=>selected.includes(p.id)),hasMore:false};
  } else if(path==='/api/admin/rebates'||path==='/api/admin/rebates/1'){
   if(req.method()==='POST'||req.method()==='PUT'){const body=req.postDataJSON();saved.push(body);offers=[{...body,id:1,active:true,branchName:'Gokul'}];json=offers[0];}else json=offers;
  } else if(path==='/api/storefront/features')json={};
  await route.fulfill({json,headers});
 });
 await page.goto(`${base}/admin/notifications`);await page.getByRole('combobox',{name:'Branch',exact:true}).selectOption('1');
 const picker=page.getByRole('region',{name:'Offer exclusions'});
 // A section with an accessible name is a region; named choices must survive search and re-edit.
 await picker.getByRole('checkbox',{name:'Beverages',exact:true}).check();
 await picker.getByRole('textbox',{name:'Search items',exact:true}).fill('Burger');await picker.getByRole('checkbox',{name:'Veg Burger',exact:true}).check();
 await page.getByRole('textbox',{name:'Offer code',exact:true}).fill('FOOD10');await page.getByRole('textbox',{name:'Offer name',exact:true}).fill('Food welcome');await page.getByRole('spinbutton',{name:'Discount (%)',exact:true}).fill('10');await page.getByRole('spinbutton',{name:'Minimum eligible spend (INR)',exact:true}).fill('300');await page.getByRole('spinbutton',{name:'Maximum discount (INR)',exact:true}).fill('50');
 await page.getByLabel('Starts (India time)',{exact:true}).fill('2026-10-17T11:00');await page.getByLabel('Ends (India time)',{exact:true}).fill('2026-10-31T21:30');await page.getByRole('button',{name:'Create offer',exact:true}).click();await page.getByText('Offer created and enabled for its scheduled dates.').waitFor();
 assert.deepEqual(saved[0].excludedCategoryIds,[2]);assert.deepEqual(saved[0].excludedProductIds,[10]);
 await page.getByRole('button',{name:'Edit',exact:true}).click();await picker.getByRole('checkbox',{name:'Beverages',exact:true}).waitFor();assert.equal(await picker.getByRole('checkbox',{name:'Beverages',exact:true}).isChecked(),true);await picker.getByRole('button',{name:'Remove Veg Burger',exact:true}).waitFor();
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
 if(process.env.SCREENSHOT_DIR){await mkdir(process.env.SCREENSHOT_DIR,{recursive:true});await picker.screenshot({path:`${process.env.SCREENSHOT_DIR}/offer-exclusions-${width}.png`});}
 await picker.getByRole('button',{name:'Clear category exclusions',exact:true}).click();await picker.getByRole('button',{name:'Remove Veg Burger',exact:true}).click();await page.getByRole('button',{name:'Save offer',exact:true}).click();await page.getByText('Offer updated. Existing eligibility rules still apply.').waitFor();assert.deepEqual(saved[1].excludedCategoryIds,[]);assert.deepEqual(saved[1].excludedProductIds,[]);
 console.log(`Offer category/item exclusions round-trip and clear correctly at ${width}px`);await context.close();
 }} finally {await browser.close();}
