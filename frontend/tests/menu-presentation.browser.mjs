import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {mkdir} from 'node:fs/promises';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const base=process.env.BROWSER_BASE??'http://127.0.0.1:3311',screens=process.env.SCREENSHOT_DIR??'/tmp/gokul-compact-header';
const date=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata'}).format(new Date(Date.now()+86400000));
const branch={id:1,name:'Main branch',active:true,operational:true,pickupAvailable:true};
const products=Array.from({length:30},(_,i)=>({id:i+1,name:i===0?'Rasgulla':`Sweet ${i+1}`,categoryId:1,categoryName:'Sweets',price:15,available:true,saleMode:'UNIT',imageUrl:'/arrival-mithai.webp'}));
const categories=[{id:1,name:'Sweets',products},...['Food','Bakery','Snacks'].map((name,i)=>({id:i+2,name,products:[{...products[0],id:31+i,name:`${name} favourite`,categoryId:i+2,categoryName:name}]}))];
const banner={key:'festival',titleEn:'Celebrate Diwali',subtitleEn:'Sweets, meals & easy pickup.',buttonLabel:'Explore sweets',categoryId:1,visible:true,order:0,mediaUrl:'/arrival-mithai.webp',mediaType:'image/webp',frame:{x:50,y:50,zoom:100,fit:'COVER'}};
const slot={id:1,branchId:1,slotDate:date,startTime:'18:00:00',endTime:'19:00:00',active:true,remainingCapacity:20,priorityEnabled:false};
const isPickerRequest=request=>request.method()!=='OPTIONS'&&(new URL(request.url()).pathname.endsWith('/pickup-discovery')||(new URL(request.url()).pathname.endsWith('/availability')&&!request.url().includes('menuPreview=true')));
const browser=await chromium.launch({headless:true});
const classicBrowser=await chromium.launch({headless:true,ignoreDefaultArgs:['--hide-scrollbars']});
try{for(const [width,enabled,expired,reduced,classic] of [[320,true,true,true,false],[390,true,true,true,false],[640,true,true,true,false],[390,true,false,true,false],[1280,true,false,true,false],[390,false,false,true,false]]){
 const context=await (classic?classicBrowser:browser).newContext({viewport:{width,height:900},serviceWorkers:'block',reducedMotion:reduced?'reduce':'no-preference'}),page=await context.newPage();page.setDefaultTimeout(15000);let discoveryFails=false,previewFails=false,pickerGate=null;const errors=[];page.on('pageerror',e=>errors.push(e.message));
 const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Methods':'GET,POST,OPTIONS','Access-Control-Allow-Headers':'content-type'};
 await context.route('**/api/**',async route=>{
  const path=new URL(route.request().url()).pathname;let json=[];if(path.endsWith('/availability')&&route.request().url().includes('menuPreview=true')){if(previewFails)return route.fulfill({status:503,json:{message:'Temporary outage'},headers});}
  if(route.request().method()==='OPTIONS')return route.fulfill({status:204,headers});
  if(isPickerRequest(route.request())&&pickerGate)await pickerGate;
  if(path.endsWith('/pickup-discovery')&&discoveryFails)return route.fulfill({status:503,json:{message:'Temporary outage'},headers});
  if(path==='/api/storefront/features')json={futuristicStorefrontV2:enabled,checkoutExperienceV2:enabled,contextualStorefrontV2:true,smartAvailability:true,today:date,futureOrderingDays:30};
  else if(path==='/api/branches')json=[branch];else if(path==='/api/branches/1')json=branch;
  else if(path==='/api/menu')json=categories;
  else if(path==='/api/menu/appearance')json={banners:[banner],categories:[]};
  else if(path==='/api/menu/portion-groups')json={groups:[]};
  else if(path==='/api/storefront/customer-identity')json={enabled:false};
  else if(path.endsWith('/availability')||path.endsWith('/pickup-discovery'))json={today:date,maximumDate:date,dates:[{date,available:true,items:products.map(p=>({productId:p.id,available:true})),slots:expired?[]:[{slot,normalAvailable:true,priorityAvailable:false,issues:[]}]}]};
  await route.fulfill({json,headers}).catch(()=>{});
 });
 await context.addInitScript(({branch,date,slot,expired,product})=>{localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));localStorage.setItem('gokul-social-follow-popup-seen','true');localStorage.setItem('gokul-pickup-intent',JSON.stringify({branchId:1,date}));localStorage.setItem('gokul-selected-pickup-slot',JSON.stringify({date:expired?'2026-01-01':date,slot:expired?{...slot,slotDate:'2026-01-01'}:slot,pickupType:'NORMAL'}));if(expired)localStorage.setItem('gokul-cart',JSON.stringify({branchId:1,items:[{product,quantity:1}]}));},{branch,date,slot,expired,product:products[0]});
 await page.goto(`${base}/menu`);await page.locator('#gokul-product-1').waitFor();await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});
 const card=page.getByRole('region',{name:'Menu pickup time',exact:true});
 await card.waitFor();
 if(enabled&&width<=640){
  assert.equal(await card.getAttribute('data-menu-refined'),'true');
  if(expired)await card.getByRole('status').filter({hasText:'No verified normal pickup'}).waitFor();
  const box=await card.boundingBox();assert.ok(box.height<210,`compact pickup status height ${box.height}`);
  assert.equal(await card.locator('.menu-pickup-notices').count(),1);
  const action=card.getByRole('button',{name:expired?'Choose time':'Change time',exact:true});assert.ok(await action.isVisible());
  assert.equal(await action.locator('span').first().evaluate(n=>getComputedStyle(n).color),'rgb(255, 255, 255)');
  for(const warning of await card.locator('.menu-pickup-notices>p').all())assert.ok((await warning.boundingBox()).height>0,'warnings remain visible');
 }
 await page.getByRole('heading',{name:'Celebrate Diwali',exact:true}).waitFor();
 if(enabled&&width<=640){
  const carousel=page.getByRole('region',{name:'Published menu banners',exact:true});
  const action=await carousel.getByRole('button',{name:'Explore sweets',exact:true}).boundingBox();
  const next=await carousel.getByRole('button',{name:'Next banner',exact:true}).boundingBox();
  assert.ok(action.y+action.height<=next.y,'integrated carousel controls do not overlap the category action');
  assert.ok((await carousel.boundingBox()).height<300,'short festive copy keeps the phone carousel compact');
 }

 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
 const before=await page.evaluate(()=>localStorage.getItem('gokul-cart'));
 await mkdir(screens,{recursive:true});await page.screenshot({path:`${screens}/menu-presentation-${width}-${expired?'expired':'selected'}-${enabled}.png`});
 assert.equal(await page.evaluate(()=>localStorage.getItem('gokul-cart')),before);
 assert.deepEqual(errors,[]);await context.close();console.log(`Menu presentation ${width}px expired=${expired} enabled=${enabled} passed`);
}}finally{await Promise.all([browser.close(),classicBrowser.close()]);}
