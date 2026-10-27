import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const browser=await chromium.launch({headless:true}),base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
const branch={id:1,name:'Test branch',active:true,operational:true,pickupAvailable:true,address:'Test road'};
try {for(const width of [320,390,1280]){
 const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:'block'}),page=await context.newPage();
 let release;const gate=new Promise(resolve=>release=resolve);let fail=true,reads=0;
 const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true'};
 await context.route('**/api/**',async route=>{
  const path=new URL(route.request().url()).pathname;let json=[];
  if(path==='/api/storefront/features')json={futuristicStorefrontV2:true,preHomeIntentGateway:true,branchExperience:true};
  else if(path==='/api/branches') {reads++;if(fail)await gate;if(fail)return route.fulfill({status:503,headers,json:{message:'unavailable'}});json=[branch];}
  else if(path==='/api/branches/1')json=branch;
  else if(path==='/api/branches/1/discovery')json={offerings:[],overallExperience:{average:4,count:1},topRatedItems:[]};
  else if(path==='/api/branches/1/discovery/reviews')json=new URL(route.request().url()).searchParams.has('before')
   ? {reviews:[{comment:'Another customer enjoyed their order.',overallRating:5,displayName:'Customer'}],nextBefore:null}
   : {reviews:[{comment:'The sweets were fresh and carefully packed.',overallRating:4,displayName:'Riya'}],nextBefore:20};
  else if(path==='/api/customer/identity/orders/page')return route.fulfill({status:401,headers,json:{}});
  return route.fulfill({headers,json});
 });
 await page.goto(base+'/');const section=page.locator('#gokul-branches');await section.getByText('Loading branches…').waitFor();
 assert.equal(await section.getByText('Branch details will appear here when available. You can still explore the menu.').count(),0);
 release();await section.getByRole('alert').filter({hasText:'Could not load branches'}).waitFor();fail=false;
 await section.getByRole('button',{name:'Retry'}).click();await section.getByText('Test branch',{exact:true}).first().waitFor();
 await page.goto(base+'/branches/1');await page.getByText('The sweets were fresh and carefully packed.').waitFor();
 assert.equal(await page.getByLabel('4 out of 5 stars').count()>0,true);
 await page.getByText('Riya · Completed order').waitFor();
 await page.getByRole('button',{name:'More reviews'}).click();await page.getByText('Another customer enjoyed their order.').waitFor();
 assert.equal(await page.getByText('The sweets were fresh and carefully packed.').count(),0);
 await page.getByRole('button',{name:'Latest reviews'}).click();await page.getByText('Riya · Completed order').waitFor();
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
 await context.close();
}console.log('Branch loading, retry and published feedback passed');}finally{await browser.close();}
