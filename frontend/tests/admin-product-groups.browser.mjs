import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const base=process.env.BROWSER_BASE??'http://127.0.0.1:3311',browser=await chromium.launch({headless:true});
try{for(const width of [390,1280]){
 const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:'block'}),page=await context.newPage();page.setDefaultTimeout(15000);
 const products=[200,400,1000,5000].map((size,i)=>({productId:i+1,productCode:`DAHI-${size}`,productName:`Dahi ${size} g`,categoryId:1,categoryName:'Dairy',effectivePrice:[25,50,120,550][i],available:true,productActive:true,categoryActive:true}));
 let saved={version:0,groups:[]},conflict=false,writes=0;const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await context.route('**/api/**',async route=>{const r=route.request(),path=new URL(r.url()).pathname;const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Headers':'content-type,x-staff-csrf','Access-Control-Allow-Methods':'GET,PUT,OPTIONS','Access-Control-Expose-Headers':'X-Staff-CSRF','X-Staff-CSRF':'test-csrf'};if(r.method()==='OPTIONS')return route.fulfill({status:204,headers});let json=[];
  if(path==='/api/admin/auth/me')json={staffId:77,username:'owner',fullName:'Owner',roleName:'OWNER_ADMIN',branchIds:[1],permissions:['MENU_MANAGE']};
  else if(path==='/api/branches')json=[{id:1,name:'Main branch',active:true}];
  else if(path==='/api/admin/branches/1/menu')json=products;
  else if(path==='/api/admin/branches/1/menu/portion-groups'){
   if(r.method()==='PUT'){writes++;assert.equal(r.postDataJSON().version,saved.version);if(conflict)return route.fulfill({status:409,json:{message:'Conflict'},headers});saved={...r.postDataJSON(),version:saved.version+1};}json=saved;
  }else if(path==='/api/storefront/features')json={};
  await route.fulfill({headers,json});
 });
 await page.goto(`${base}/admin/menu/portions`);await page.getByRole('button',{name:'Create group',exact:false}).click();const section=page.locator('section').filter({has:page.getByRole('heading',{name:'New product group',exact:true})});
 await section.getByLabel('Customer-facing name').fill('Dahi');
 for(const p of products){await page.getByLabel('Add existing products').fill(p.productCode);await page.getByRole('button').filter({hasText:p.productName}).click();}
 await page.getByLabel('Option label for Dahi 200 g').fill('200 g pack');await page.getByLabel('Option label for Dahi 400 g').fill('400 g pack');await page.getByLabel('Option label for Dahi 1000 g').fill('1 kg pack');await page.getByLabel('Option label for Dahi 5000 g').fill('5 kg pack');
 await page.getByRole('button',{name:'Move Dahi 400 g up',exact:true}).click();await page.getByRole('button',{name:'Save product groups',exact:true}).click();await page.getByRole('status').filter({hasText:'Product groups saved'}).waitFor();assert.equal(saved.groups[0].title,'Dahi');assert.deepEqual(saved.groups[0].choices.map(c=>c.productId),[2,1,3,4]);assert.equal(saved.groups[0].choices[2].label,'1 kg pack');
 conflict=true;await page.getByLabel('Customer-facing name').fill('Fresh Dahi');await page.getByRole('button',{name:'Save product groups',exact:true}).click();await page.getByRole('alert').filter({hasText:'Another staff member'}).waitFor();assert.equal(saved.groups[0].title,'Dahi');assert.equal(await page.getByLabel('Customer-facing name').inputValue(),'Fresh Dahi');
 conflict=false;await page.getByRole('button',{name:'Reload saved groups',exact:true}).click();await page.getByLabel('Customer-facing name').waitFor();assert.equal(await page.getByLabel('Customer-facing name').inputValue(),'Dahi');await page.getByRole('button',{name:'Ungroup Dahi 5000 g',exact:true}).click();await page.getByRole('button',{name:'Save product groups',exact:true}).click();await page.getByRole('status').filter({hasText:'Product groups saved'}).waitFor();assert.equal(saved.groups[0].choices.length,3);assert.equal(products.length,4);assert.equal(writes,3);assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);assert.deepEqual(errors,[]);await page.screenshot({path:`/tmp/gokul-premium/admin-${width}.png`});await context.close();console.log(`Admin product grouping ${width}px search, labels, ordering, conflict and ungroup passed`);
}}finally{await browser.close();}
