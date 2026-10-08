import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {mkdir} from 'node:fs/promises';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const browser=await chromium.launch({headless:true}),base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
const branch={id:1,code:'SERVICE',name:'Service branch',active:true,operational:true,address:'Test address',city:'Test city',openingTime:'08:00:00',closingTime:'22:00:00',pickupAvailable:true};
const product={id:1,name:'Samosa',categoryId:1,categoryName:'Snacks',description:null,price:20,imageUrl:null,available:true,saleMode:'UNIT',minimumWeightGrams:null,weightStepGrams:null};
try{for(const width of [320,390,1280]){
 const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:'block'}),page=await context.newPage();
 // Device time is deliberately years wrong; service boundaries are server-relative.
 await page.clock.install({time:new Date('2030-01-01T00:00:00Z')});
 await context.addInitScript(branch=>{localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));localStorage.setItem('gokul-social-follow-popup-seen','true');},branch);
 let gateway=true,operational=true,saved={enabled:false,revision:0,items:[]},failMenu=false;
 const errors=[];page.on('pageerror',error=>errors.push(error.message));
 await context.route('**/api/**',async route=>{
  const request=route.request(),path=new URL(request.url()).pathname;
  const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Methods':'GET,PUT,POST,OPTIONS','Access-Control-Allow-Headers':'content-type,x-staff-csrf','Access-Control-Expose-Headers':'X-Staff-CSRF','X-Staff-CSRF':'test-csrf'};
  if(request.method()==='OPTIONS')return route.fulfill({status:204,headers});
  let json=[];
  if(path==='/api/admin/auth/me')json={staffId:12,username:'owner',fullName:'Owner',roleName:'OWNER_ADMIN',branchIds:[1],permissions:['MENU_MANAGE','BRANCH_MANAGE']};
  else if(path==='/api/storefront/features')json={futuristicStorefrontV2:true,checkoutExperienceV2:true,branchExperience:true,contextualStorefrontV2:true,preHomeIntentGateway:gateway,customerHomeV2:true,customerAccountHub:true,occasionEnquiries:true,today:'2026-10-05'};
  else if(path==='/api/storefront/customer-identity')json={enabled:true};
  else if(path==='/api/customer/identity/me')json={authenticated:true,phone:'+919876543210',name:'Service customer'};
  else if(path==='/api/branches'||path==='/api/admin/branches')json=[{...branch,operational}];
  else if(path==='/api/branches/1'||path==='/api/admin/branches/1')json={...branch,operational};
  else if(path==='/api/admin/branches/1/offerings')json={draft:[],published:[],version:0};
  else if(path==='/api/admin/branches/1/menu')json=[1,2].map(id=>({branchProductId:100+id,branchId:1,productId:id,productName:id===1?'Samosa':'Chola samosa',categoryId:1,categoryName:'Snacks',productActive:true,categoryActive:true,available:true,effectivePrice:20,displayOrder:id}));
  else if(path==='/api/admin/branches/1/menu-service-windows'){
   if(request.method()==='PUT'){const value=request.postDataJSON();assert.equal(value.revision,saved.revision);saved={...value,revision:saved.revision+1};}
   json=saved;
  }else if(path==='/api/admin/branches/1/operational'){
   if(request.method()==='PUT')operational=request.postDataJSON().operational;json={operational};
  }else if(path==='/api/menu'){
   if(failMenu)return route.fulfill({status:503,headers,json:{message:'Temporary outage'}});
   const sold=saved.enabled&&saved.items.find(item=>item.branchProductId===101)?.soldOut;
   // Browsing at 4 AM may build a cart for an eligible later pickup.
   json=[{id:1,name:'Snacks',description:null,displayOrder:1,products:[1,2].map(id=>({...product,id,name:id===1?'Samosa':'Chola samosa',available:!sold,serviceAvailability:{available:!sold,code:sold?'SOLD_OUT':'AVAILABLE',message:sold?'Sold out.':null,evaluatedAt:'2026-10-04T22:30:00Z',nextChangeAt:null}}))}];
  }else if(path==='/api/branches/1/discovery')json={overallExperience:{average:4.5,count:2},offerings:[],topRatedItems:[]};
  return route.fulfill({headers,json});
 });
 await page.goto(`${base}/menu`);const samosa=page.locator('.gokul-product-card').filter({has:page.getByRole('heading',{name:'Samosa',exact:true})});
 await samosa.getByRole('button',{name:/^Add .* to cart$/}).waitFor();
 assert.equal(await samosa.getByRole('button',{name:/^Add .* to cart$/}).isEnabled(),true,'advance pickup browsing is allowed before service opens');
 assert.equal(await samosa.getByText('Available later',{exact:true}).count(),0);
 await page.goto(`${base}/admin/menu/service-hours`);await page.getByRole('heading',{name:'Menu service hours'}).waitFor();const help=page.getByRole('button',{name:'Help for Samosa sold out',exact:true});await help.click();
 const helpDialog=page.getByRole('dialog',{name:'Samosa sold out',exact:true});await helpDialog.waitFor();await helpDialog.getByText('What to enter or do',{exact:true}).waitFor();
 assert.equal(await page.getByRole('checkbox',{name:'Sold out until cleared'}).first().isChecked(),false,'help does not toggle the setting');
 assert.equal(await helpDialog.evaluate(node=>{const box=node.getBoundingClientRect();return box.left>=0&&box.right<=innerWidth&&box.top>=0&&box.bottom<=innerHeight;}),true,'help fits the viewport');
 await helpDialog.getByRole('button',{name:'Close',exact:true}).click();assert.equal(await help.evaluate(node=>node===document.activeElement),true);
 await help.focus();await page.keyboard.press('Enter');await helpDialog.waitFor();await page.keyboard.press('Escape');assert.equal(await page.getByRole('dialog').count(),0);
 await help.click();await page.mouse.click(2,2);assert.equal(await page.getByRole('dialog').count(),0,'outside tap dismisses help');assert.equal(saved.revision,0);
 await page.getByRole('checkbox',{name:'Enforce service hours and sold-out rules for this branch'}).check();
 await page.getByRole('combobox',{name:/^Category/}).selectOption('1');await page.getByRole('button',{name:'Apply category hours to draft'}).click();
 const baseRule=page.locator('section').filter({has:page.getByRole('heading',{name:'Samosa',exact:true})});const dependent=page.locator('section').filter({has:page.getByRole('heading',{name:'Chola samosa',exact:true})});
 await baseRule.getByRole('checkbox',{name:'Sold out until cleared'}).check();await dependent.getByLabel('Requires an available item').selectOption('101');
 await page.getByRole('button',{name:'Save branch service rules'}).click();await page.getByText(/Saved. New orders use these IST rules/).waitFor();
 assert.equal(saved.items.find(item=>item.branchProductId===101).soldOut,true);assert.equal(saved.items.find(item=>item.branchProductId===102).requiresBranchProductId,101);assert.equal(saved.items[0].startsAt,'11:00');assert.equal(saved.items[0].endsAt,'21:30');
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
 if(process.env.SCREENSHOT_DIR){await mkdir(process.env.SCREENSHOT_DIR,{recursive:true});await page.screenshot({path:`${process.env.SCREENSHOT_DIR}/service-hours-${width}.png`,fullPage:true});}
 await page.reload();await baseRule.getByRole('checkbox',{name:'Sold out until cleared'}).waitFor();assert.equal(await baseRule.getByRole('checkbox',{name:'Sold out until cleared'}).isChecked(),true);
 await page.goto(`${base}/menu`);await page.getByText('Sold out',{exact:true}).first().waitFor();assert.equal(await page.getByRole('button',{name:/^Add .* to cart$/}).first().isDisabled(),true);
 await page.goto(`${base}/admin/branches`);const toggle=page.getByRole('switch',{name:'Branch is operational'});await toggle.waitFor();await toggle.click();await page.getByText('Branch closed for new customer visits and orders. Existing orders are retained.',{exact:true}).waitFor();assert.equal(operational,false);
 gateway=false;operational=true;await page.goto(`${base}/`);await page.locator('.gokul-editorial-home').waitFor();operational=false;await page.clock.fastForward(16000);await page.getByRole('heading',{name:'Currently not operational',exact:true}).waitFor();assert.equal(await page.locator('.gokul-editorial-home').count(),0,'already-open selected branch home hides content after closure');gateway=true;
 await page.goto(`${base}/branches`);const closedCard=page.getByRole('article').filter({has:page.getByRole('heading',{name:branch.name,exact:true})});await closedCard.getByText('Currently not operational',{exact:true}).first().waitFor();assert.equal(await closedCard.getByText('Order for pickup',{exact:true}).count(),0,'closed cards omit pickup availability');assert.equal(await closedCard.getByText('Online pickup unavailable',{exact:true}).count(),0);
 for(const path of ['/branches/1','/menu']){await page.goto(`${base}${path}`);await page.getByRole('heading',{name:'Currently not operational',exact:true}).waitFor();assert.equal(await page.locator('.gokul-product-card').count(),0);}
 await page.goto(`${base}/occasions/requests`);await page.getByRole('heading',{name:'Requests & quotes',exact:true}).waitFor();await page.getByRole('heading',{name:'Your requests',exact:true}).waitFor();assert.equal(await page.getByRole('heading',{name:'Currently not operational',exact:true}).count(),0,'existing requests remain accessible after branch closure');
 await page.evaluate(()=>localStorage.setItem('gokul-language','hi'));await page.goto(`${base}/menu`);await page.getByRole('heading',{name:'अभी संचालन में नहीं है',exact:true}).waitFor();await page.getByRole('link',{name:'दूसरी शाखा चुनें',exact:true}).waitFor();
 operational=true;await page.goto(`${base}/menu`);await page.getByText('स्टॉक खत्म',{exact:true}).first().waitFor();
 await page.evaluate(()=>localStorage.setItem('gokul-language','en'));
 operational=true;saved={...saved,enabled:false};await page.goto(`${base}/branches`);await closedCard.getByText(width<=640?'Collect your order here':'Order for pickup',{exact:true}).waitFor();await page.goto(`${base}/menu`);await samosa.getByRole('button',{name:/^Add .* to cart$/}).waitFor();assert.equal(await page.getByRole('heading',{name:'Currently not operational',exact:true}).count(),0);
 // An advisory menu refresh failure must retain the cart for final pickup checks.
 await samosa.getByRole('button',{name:/^Add .* to cart$/}).click();failMenu=true;await page.clock.fastForward(31000);
 assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-cart')).items.length),1);
 assert.deepEqual(errors,[]);await context.close();console.log(`Menu service hours, saved dependencies, advance browsing, closed branch and refresh outage ${width}px passed`);
}}finally{await browser.close();}
