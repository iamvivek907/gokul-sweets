import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
const date=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata'}).format(new Date(Date.now()+86400000));
const branch={id:1,name:'Main branch',active:true,operational:true,pickupAvailable:true};
const product=(id,name,categoryId,categoryName,available=true)=>({id,name,categoryId,categoryName,price:50,available,saleMode:'UNIT',imageUrl:'/arrival-mithai.webp'});
const sweets=Array.from({length:112},(_,i)=>product(i+1,i===0?'Rasgulla':`Sweet ${i+1}`,1,'Sweets'));
const food=[product(113,'Unavailable meal',2,'Food')],dairy=[product(114,'Dahi small',3,'Dairy'),product(115,'Dahi large',3,'Dairy')];
const slot={id:1,branchId:1,slotDate:date,startTime:'18:00:00',endTime:'19:00:00',active:true,remainingCapacity:20,priorityEnabled:false,priorityRemainingCapacity:0};
const browser=await chromium.launch({headless:true});
try{for(const width of [320,390,640]){
 const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:'block'}),page=await context.newPage();page.setDefaultTimeout(15000);
 const errors=[],requests=[];let mode='date',fail=false;page.on('pageerror',error=>errors.push(error.message));
 const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Methods':'GET,POST,OPTIONS','Access-Control-Allow-Headers':'content-type'};
 await context.route('**/api/**',async route=>{
  const url=new URL(route.request().url()),path=url.pathname;let json=[];
  if(route.request().method()==='OPTIONS')return route.fulfill({status:204,headers});
  if(path==='/api/storefront/features')json={futuristicStorefrontV2:true,checkoutExperienceV2:true,contextualStorefrontV2:true,smartAvailability:true,today:date,futureOrderingDays:30};
  else if(path==='/api/branches')json=[branch];else if(path==='/api/branches/1')json=branch;
  else if(path==='/api/menu')json=[{id:1,name:'Sweets',products:sweets},{id:2,name:'Food',products:food},{id:3,name:'Dairy',products:dairy.map(p=>({...p,available:mode!=='catalogue'}))}];
  else if(path==='/api/menu/portion-groups')json={groups:[{key:'dahi',title:'Dahi',choices:dairy.map(p=>({productId:p.id,label:p.name.replace('Dahi ', '')}))}]};
  else if(path==='/api/storefront/customer-identity')json={enabled:false};
  else if(path.endsWith('/availability')){
   const body=route.request().postDataJSON();assert.ok(body.items.length<=100);assert.equal(body.days,1);assert.equal(url.searchParams.get('menuPreview'),'true');requests.push(body.items);
   if(fail&&body.items.some(p=>p.productId===113))return route.fulfill({status:503,json:{message:'Unavailable'},headers});
   const blocked=id=>id===113||(id>=114&&(mode==='date'||mode==='slot'||(mode==='partial'&&id===115)));
   const items=body.items.map(p=>({productId:p.productId,available:mode==='slot'?true:!blocked(p.productId),code:blocked(p.productId)?'NO_INVENTORY':null}));
   const issues=body.items.filter(p=>blocked(p.productId)).map(p=>({productId:p.productId,available:false,code:'NO_INVENTORY'}));
   json={today:date,maximumDate:date,dates:[{date,available:!issues.length,items,slots:[{slot,normalAvailable:!issues.length,priorityAvailable:false,issues}]}]};
  }
  return route.fulfill({json,headers});
 });
 await context.addInitScript(({branch,date,slot})=>{localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));localStorage.setItem('gokul-pickup-intent',JSON.stringify({branchId:1,date}));localStorage.setItem('gokul-selected-pickup-slot',JSON.stringify({date,slot,pickupType:'NORMAL'}));},{branch,date,slot});
 const load=async()=>{await page.goto(`${base}/menu`);await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});await page.locator('#gokul-product-113').waitFor();};
 await load();
 await page.waitForFunction(()=>document.querySelector('#gokul-product-113 button[aria-label="Add Unavailable meal to cart"]')?.disabled===true);
 assert.deepEqual([...new Set(requests.flatMap(items=>items.map(p=>p.productId)))].sort((a,b)=>a-b),Array.from({length:115},(_,i)=>i+1),'full catalogue checked, including Food after 112 sweets');
 const search=page.locator('.menu-search input');await search.fill('Rasgulla');await page.locator('#gokul-product-1').waitFor();assert.equal(await page.getByText('No matching items',{exact:true}).count(),0);
 await search.fill('Dahi');const group=page.locator('.mobile-portion-card');await group.waitFor();await group.getByRole('button',{name:'Choose options for Dahi',exact:true}).waitFor();assert.equal(await group.getByRole('button',{name:'Choose options for Dahi',exact:true}).isDisabled(),true);assert.doesNotMatch(await group.innerText(),/sizes? available/);
 // A single orderable variant keeps the group enabled, and only that size can be added.
 mode='partial';await load();await search.fill('Dahi');await group.getByText('1 size available',{exact:true}).first().waitFor();await group.getByRole('button',{name:'Choose options for Dahi',exact:true}).click();const picker=page.getByRole('dialog',{name:'Dahi',exact:true});assert.equal(await picker.getByRole('button',{name:'Add Dahi small to cart',exact:true}).isEnabled(),true);assert.equal(await picker.getByRole('button',{name:'Add Dahi large to cart',exact:true}).isDisabled(),true);await picker.getByRole('button',{name:'Add Dahi small to cart',exact:true}).click();await picker.getByRole('group',{name:'Dahi small quantity: 1',exact:true}).waitFor();await picker.getByRole('button',{name:'Done',exact:true}).click();
 mode='date';await load();await search.fill('Dahi');await group.getByRole('button',{name:'Choose options for Dahi',exact:true}).filter({hasText:'Manage'}).waitFor();assert.equal(await group.getByRole('button',{name:'Choose options for Dahi',exact:true}).isEnabled(),true);await group.getByRole('button',{name:'Choose options for Dahi',exact:true}).click();await picker.getByText('Unavailable for selected pickup',{exact:true}).first().waitFor();await picker.getByRole('button',{name:'Remove one Dahi small',exact:true}).click();await picker.getByRole('button',{name:'Done',exact:true}).click();await page.waitForFunction(()=>document.querySelector('.menu-group-control')?.disabled===true);
 for(const status of ['slot','catalogue']){mode=status;await load();await search.fill('Dahi');await page.waitForFunction(()=>document.querySelector('.menu-group-control')?.disabled===true);assert.doesNotMatch(await group.innerText(),/sizes? available/);}
 // A later batch failure publishes an error, and retry checks the entire menu again.
 mode='date';fail=true;await load();await page.getByRole('button',{name:'Retry availability',exact:true}).waitFor();fail=false;await page.getByRole('button',{name:'Retry availability',exact:true}).click();await page.waitForFunction(()=>document.querySelector('#gokul-product-113 button[aria-label="Add Unavailable meal to cart"]')?.disabled===true);
 assert.deepEqual(errors,[]);await context.close();console.log(`Review regressions passed at ${width}px`);
}}finally{await browser.close();}
