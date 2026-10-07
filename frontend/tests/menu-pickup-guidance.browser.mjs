import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
const date=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata'}).format(new Date(Date.now()+86400000));
const branch={id:1,name:'Gokul Test',active:true,operational:true,pickupAvailable:true};
const products=[1,2].map(id=>({id,name:`Meal ${id}`,categoryId:1,categoryName:'Meals',price:100,available:true,saleMode:'UNIT',imageUrl:null}));
const slot={id:1,branchId:1,slotDate:date,startTime:'18:00:00',endTime:'19:00:00',active:true,remainingCapacity:20,priorityEnabled:false};
const browser=await chromium.launch({headless:true});
try{
 for(const width of [320,390,640]){
  const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:'block'}),page=await context.newPage();
  await page.clock.install({time:new Date()});page.setDefaultTimeout(15000);let checks=0,menuRevision="v1",blocked=true;const errors=[];page.on('pageerror',e=>errors.push(e.message));
  const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Methods':'GET,POST,OPTIONS','Access-Control-Allow-Headers':'content-type,idempotency-key'};
  await context.route('**/api/**',async route=>{
   const request=route.request(),path=new URL(request.url()).pathname;let json=[];
   if(request.method()==='OPTIONS')return route.fulfill({status:204,headers});
   if(path==='/api/storefront/features')json={futuristicStorefrontV2:true,checkoutExperienceV2:true,contextualStorefrontV2:true,simplifiedCheckout:true,acceptedCheckoutQuote:true,smartAvailability:true,today:date,futureOrderingDays:30};
   else if(path==='/api/branches')json=[branch];else if(path==='/api/branches/1')json=branch;
   else if(path==='/api/menu')json=[{id:1,name:'Meals',products:products.map(product=>({...product,availabilityRevision:menuRevision}))}];
   else if(path==='/api/menu/portion-groups')json={groups:[]};
   else if(path==='/api/storefront/customer-identity')json={enabled:false};
   else if(path.endsWith('/pickup-discovery'))json={today:date,maximumDate:date,dates:[{date,available:true,items:[],slots:[{slot,normalAvailable:true,priorityAvailable:false}]}]};
   else if(path.endsWith('/availability')){
    checks++;const body=request.postDataJSON();assert.equal(body.days,1);
    assert.deepEqual(body.items.map(i=>i.productId).sort(),[1,2],'search must not hide recommended items from the check');
    json={today:date,maximumDate:date,dates:[{date,available:!blocked,items:products.map(p=>({productId:p.id,available:!blocked})),slots:[{slot,normalAvailable:!blocked,priorityAvailable:false,issues:blocked?products.map(p=>({productId:p.id,available:false})):[]}]}]};
   }
   await route.fulfill({json,headers});
  });
  await context.addInitScript(branch=>localStorage.setItem('gokul-selected-branch',JSON.stringify(branch)),branch);
  await page.goto(`${base}/menu`);await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});
  const pickup=page.getByRole('region',{name:'Menu pickup time'});await pickup.getByText('Choose pickup date & time',{exact:true}).waitFor();
  const recommendations=page.locator('#gokul-menu-items');
  await recommendations.getByRole('button',{name:'Add Meal 1 to cart'}).waitFor();
  assert.equal(await recommendations.getByText('Unavailable for selected pickup',{exact:true}).count(),0);assert.equal(checks,0);
  // Discovery is a draft: even the first available slot requires a deliberate tap.
  await pickup.getByRole('button',{name:'Choose time',exact:true}).click();
  const dialog=page.getByRole('dialog',{name:'Choose pickup date & time'});
  assert.equal(await dialog.getByRole('button',{name:'Use this pickup',exact:true}).isDisabled(),true);
  await dialog.getByRole('button',{name:'Cancel',exact:true}).click();
  assert.equal(await page.evaluate(()=>localStorage.getItem('gokul-selected-pickup-slot')),null);
  await page.getByLabel('Find a favourite',{exact:true}).fill('Meal 1');
  await page.evaluate(date=>{localStorage.setItem('gokul-pickup-intent',JSON.stringify({branchId:1,date}));window.dispatchEvent(new Event('gokul-pickup-intent-change'));},date);
  await pickup.getByText(/Time not selected/).waitFor();await pickup.getByText('Item availability is checked for the date shown above. Confirm your pickup at checkout.',{exact:true}).waitFor();
  await page.getByLabel('Find a favourite',{exact:true}).fill('');
  await recommendations.getByText('Unavailable for selected pickup',{exact:true}).nth(1).waitFor();
  assert.equal(await recommendations.getByRole('button',{name:'Add Meal 1 to cart'}).isDisabled(),true);
  assert.equal(await recommendations.getByRole('button',{name:'Add Meal 2 to cart'}).isDisabled(),true);
  assert.equal(await page.evaluate(()=>localStorage.getItem('gokul-selected-pickup-slot')),null);
  await page.evaluate(({date,slot})=>{localStorage.setItem('gokul-selected-pickup-slot',JSON.stringify({date,slot,pickupType:'NORMAL'}));window.dispatchEvent(new Event('gokul-pickup-slot-change'));},{date,slot});
  await page.waitForFunction(()=>document.querySelector('.mobile-menu-pickup strong')?.textContent?.includes('6:00'));
  // Staff change service hours without changing which products can be browsed.
  // The lightweight live revision must invalidate the dated preview without a manual retry.
  const before=checks;menuRevision='v2';blocked=false;await page.clock.fastForward(31000);
  await page.waitForFunction(()=>{const button=document.querySelector('button[aria-label="Add Meal 2 to cart"]');return button&&!button.disabled;});
  assert.ok(checks>before,'changed menu revision rechecks service hours for the selected date');
  assert.equal(await recommendations.getByRole('button',{name:'Add Meal 2 to cart'}).isEnabled(),true);
  menuRevision='v3';blocked=true;await page.clock.fastForward(31000);
  await recommendations.getByText('Unavailable for selected pickup',{exact:true}).nth(1).waitFor();
  assert.equal(await recommendations.getByRole('button',{name:'Add Meal 2 to cart'}).isDisabled(),true);
  assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-selected-pickup-slot'))),{date,slot,pickupType:'NORMAL'},'background rechecks preserve the customer’s chosen pickup');
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);assert.deepEqual(errors,[]);
  await context.close();console.log(`Pickup guidance ${width}px passed`);
 }
}finally{await browser.close();}
