import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {mkdir,readFile} from 'node:fs/promises';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
const branch={id:1,name:'Gokul test branch',active:true,operational:true,pickupAvailable:true};
const categories=['Food','Sweets','Bakery','Snacks'].map((name,i)=>({id:i+1,name,products:[{id:i+1,name:`${name} favourite`,categoryId:i+1,categoryName:name,price:80,available:true,saleMode:'UNIT',imageUrl:'/arrival-mithai.webp'}]}));
const banner={key:'festival',titleEn:'Celebrate Diwali',titleHi:'दीवाली की मिठास',subtitleEn:'Sweets, meals & easy pickup.',subtitleHi:'मिठाइयाँ और आसान पिकअप।',buttonLabel:'Explore sweets',categoryId:2,visible:true,order:0,startAt:null,endAt:null,mediaUrl:'/campaign-diwali.png',mediaType:'image/png',posterUrl:null,frame:{x:50,y:50,zoom:100,fit:'COVER'}};
const browser=await chromium.launch({headless:true,executablePath:process.env.CHROMIUM_PATH});
try {for(const width of [320,390,640,1280]) {
 const context=await browser.newContext({viewport:{width,height:960},serviceWorkers:'block',reducedMotion:width===390?'no-preference':'reduce'}),page=await context.newPage();page.setDefaultTimeout(15000);
 let banners=[banner],reads=0,failed=false;const errors=[];page.on('pageerror',e=>errors.push(e.message));
 const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Methods':'GET,POST,OPTIONS','Access-Control-Allow-Headers':'content-type'};
 await context.route('**/campaign-diwali.png',async route=>process.env.CAMPAIGN_IMAGE?route.fulfill({contentType:'image/png',body:await readFile(process.env.CAMPAIGN_IMAGE)}):route.fulfill({status:302,headers:{location:'/arrival-mithai.webp'}}));
 await context.route('**/api/**',async route=>{
  const req=route.request(),path=new URL(req.url()).pathname;let json=[];
  if(req.method()==='OPTIONS')return route.fulfill({status:204,headers});
  if(path==='/api/storefront/features')json={futuristicStorefrontV2:true,checkoutExperienceV2:true,contextualStorefrontV2:true,pickupAddOns:true,smartAvailability:false,reviews:true,today:'2026-10-17',futureOrderingDays:7};
  else if(path==='/api/branches')json=[branch];else if(path==='/api/branches/1')json=branch;
  else if(path==='/api/menu')json=categories;
  else if(path==='/api/menu/portion-groups')json={groups:[]};
  else if(path==='/api/storefront/customer-identity')json={enabled:false};
  else if(path==='/api/menu/appearance') {reads++;assert.equal(new URL(req.url()).searchParams.get('branchId'),'1');if(failed)return route.fulfill({status:503,headers,json:{message:'Unavailable'}});json={banners,categories:categories.map(c=>({id:c.id,order:c.id,imageUrl:null}))};}
  await route.fulfill({headers,json});
 });
 await context.addInitScript(branch=>{localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));localStorage.setItem('gokul-social-follow-popup-seen','true');},branch);
 const load=async()=>{await page.goto(`${base}/menu`);await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});};
 await load();const heading=page.getByRole('heading',{name:'Celebrate Diwali',exact:true});await heading.waitFor();assert.equal(await heading.count(),1);
 assert.equal(reads,1,'one appearance request serves the menu');
 assert.equal(await page.getByText('Little extras.',{exact:true}).count(),0);
 assert.equal(await page.getByText('Big smiles.',{exact:true}).count(),0);
 if(width<=640) {
  const strip=page.locator('[aria-label="Published menu banners"]');
  for(const family of ['Food','Sweets','Bakery','Snacks']) {await page.getByRole('button',{name:`Browse ${family}`,exact:true}).click();await heading.waitFor();assert.equal(await heading.count(),1);}
  const rect=await strip.boundingBox(),offers=await page.locator('.menu-offers').boundingBox(),items=await page.locator('#gokul-menu-items').boundingBox();
  assert.ok(rect.y>=offers.y+offers.height-1,'banner is below offers');
  assert.ok(rect.y>=items.y&&rect.y+rect.height<items.y+items.height,'banner occupies the product discovery strip');
  await page.getByRole('button',{name:'Explore sweets',exact:true}).click();await page.getByRole('heading',{name:'Sweets favourite',exact:true}).waitFor();assert.equal(await page.getByRole('button',{name:'Browse Sweets',exact:true}).getAttribute('aria-current'),'true');
 }
 // The same artwork now offers separate live category slides in one contained viewport.
 const carousel=page.getByRole('region',{name:'Published menu banners',exact:true});
 await carousel.getByRole('button',{name:'Show banner 2',exact:true}).click();
 await carousel.getByRole('button',{name:'Explore food',exact:true}).waitFor();
 await carousel.getByRole('button',{name:'Explore food',exact:true}).click();
 await page.getByRole('heading',{name:'Food favourite',exact:true}).waitFor();
 // Narrow phone and desktop use the same arrows and exact category actions.
 await carousel.getByRole('button',{name:'Next banner',exact:true}).click();
 await carousel.getByRole('button',{name:'Explore bakery',exact:true}).waitFor();
 await carousel.getByRole('button',{name:'Previous banner',exact:true}).click();
 await carousel.getByRole('button',{name:'Explore food',exact:true}).waitFor();
 const track=carousel.getByLabel('Swipe to explore categories',{exact:true});
 await track.evaluate(node=>node.scrollBy({left:node.clientWidth,behavior:'smooth'}));
 await carousel.getByRole('button',{name:'Explore bakery',exact:true}).waitFor();
 await track.focus();await page.keyboard.press('ArrowLeft');
 await carousel.getByRole('button',{name:'Explore food',exact:true}).waitFor();
 assert.equal(await track.evaluate(node=>getComputedStyle(node).scrollBehavior),width===390?'smooth':'auto');
 await carousel.getByRole('button',{name:'Show banner 1',exact:true}).click();
 await carousel.getByRole('button',{name:'Explore sweets',exact:true}).waitFor();
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
 if(process.env.SCREENSHOT_DIR){if(width<=640)await page.getByRole('button',{name:'All',exact:true}).click();if(width<=640)await page.evaluate(()=>window.scrollTo(0,0));else await heading.scrollIntoViewIfNeeded();await mkdir(process.env.SCREENSHOT_DIR,{recursive:true});await page.screenshot({path:`${process.env.SCREENSHOT_DIR}/diwali-menu-${width}.jpg`,fullPage:false,quality:85});}
 await page.getByRole('button',{name:'Language / भाषा',exact:true}).click();
 await page.getByRole('group',{name:'Language / भाषा',exact:true}).getByRole('button',{name:/हिन्दी/}).click();
 await page.getByRole('heading',{name:'दीवाली की मिठास',exact:true}).waitFor();
 await page.getByRole('button',{name:'Language / भाषा',exact:true}).click();
 await page.getByRole('group',{name:'Language / भाषा',exact:true}).getByRole('button',{name:/English/}).click();
 // Multiple admin-published banners preserve their exact ordering and content.
 banners=[banner,{...banner,key:'meals',titleEn:'Diwali meal plans',buttonLabel:'Explore meals',categoryId:1,order:1}];
 await load();await page.getByRole('button',{name:'Next banner',exact:true}).click();
 await page.getByRole('heading',{name:'Diwali meal plans',exact:true}).waitFor();
 await page.getByRole('button',{name:'Explore meals',exact:true}).click();
 await page.getByRole('heading',{name:'Food favourite',exact:true}).waitFor();
 // An admin replacement becomes visible after reload without a frontend deployment.
 banners=[{...banner,titleEn:'Winter sweets',categoryId:999}];await load();await page.getByRole('heading',{name:'Winter sweets',exact:true}).waitFor();assert.equal(await page.getByRole('button',{name:'Explore sweets',exact:true}).count(),0,'unknown category has no dead CTA');
 // A publish with no active banners (hidden, expired or removed) does not restore built-in artwork.
 banners=[];await load();await page.getByRole('heading',{name:'Food favourite',exact:true}).waitFor();assert.equal(await page.locator('[aria-label="Published menu banners"]').count(),0);
 failed=true;await load();await page.getByRole('heading',{name:'Food favourite',exact:true}).waitFor();assert.equal(await page.locator('[aria-label="Published menu banners"]').count(),0);assert.deepEqual(errors,[]);
 await context.close();console.log(`Published admin menu strip: ${width}px passed`);
}} finally {await browser.close();}
