import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const browser=await chromium.launch({headless:true}),base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
const branch={id:1,name:'Test branch',active:true,operational:true,pickupAvailable:true};
const row=(orderNumber,reviewRating=null)=>({orderNumber,reviewRating,branchId:1,branchName:branch.name,orderStatus:'PICKED_UP'});
async function setup({orders=[row('LATEST')],historyStatus=200,reviewStatus=200,eligible=true,existing=null,width=390,putFailure=false}={}) {
 const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:'block'}),page=await context.newPage();
 let historyCalls=[],reviewReads=[],writes=[],failSave=putFailure;
 const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Methods':'GET,POST,PUT,OPTIONS','Access-Control-Allow-Headers':'content-type'};
 await context.addInitScript(branch=>{localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));localStorage.setItem('gokul-ordering-tour:v1','seen');},branch);
 await context.route('**/api/**',async route=>{
  const req=route.request(),url=new URL(req.url()),path=url.pathname;
  if(req.method()==='OPTIONS')return route.fulfill({status:204,headers});
  let json=[];
  if(path==='/api/storefront/features')json={futuristicStorefrontV2:true,preHomeIntentGateway:true,branchExperience:true};
  else if(path==='/api/branches')json=[branch];
  else if(path==='/api/customer/identity/orders/page'){
   historyCalls.push(Object.fromEntries(url.searchParams));
   if(historyStatus!==200)return route.fulfill({status:historyStatus,headers,json:{message:'Unavailable'}});
   json={orders:orders.slice(0,Number(url.searchParams.get('limit'))),nextBefore:null};
  }else if(path.match(/^\/api\/orders\/[^/]+\/review$/)){
   const number=decodeURIComponent(path.split('/').at(-2));
   if(req.method()==='PUT'){
    writes.push({number,body:req.postDataJSON()});
    if(failSave)return route.fulfill({status:503,headers,json:{message:'Unavailable'}});
    json={id:1,orderNumber:number,...req.postDataJSON(),status:'PUBLISHED',createdAt:'2026-01-01',updatedAt:'2026-01-01'};
   }else{
    reviewReads.push(number);
    if(reviewStatus!==200)return route.fulfill({status:reviewStatus,headers,json:{message:'Unavailable'}});
    json={eligible,products:[],review:existing};
   }
  }else if(path==='/api/customer/identity/me')json={authenticated:true};
  return route.fulfill({headers,json});
 });
 await page.goto(base+'/');
 return {context,page,historyCalls,reviewReads,writes,allowSave:()=>{failSave=false;}};
}
try {
 for(const width of [320,390,1280]){
  const t=await setup({width,putFailure:true,orders:[row('LATEST'),row('OLDER')]});
  const sheet=t.page.getByRole('dialog',{name:'Rate your last order'});await sheet.waitFor({state:'visible',timeout:15000});
  const stars=sheet.getByRole('group',{name:'Overall order rating'});await stars.getByRole('button',{name:'1 star',exact:true}).click();
  const choices=sheet.getByRole('group',{name:'Feedback suggestions'});
  await choices.getByRole('button',{name:'Taste needs improvement'}).click();await choices.getByRole('button',{name:'Price felt too high'}).click();
  assert.equal(await choices.getByRole('button',{name:'Price felt too high'}).getAttribute('aria-pressed'),'true');
  await choices.getByRole('button',{name:'Taste needs improvement'}).click();
  assert.equal(await choices.getByRole('button',{name:'Taste needs improvement'}).getAttribute('aria-pressed'),'false');
  await sheet.getByLabel('Tell us more (optional)').fill('Please improve the value');
  await sheet.getByRole('button',{name:'Submit Review'}).click();await sheet.getByRole('alert').filter({hasText:'could not be confirmed'}).waitFor();
  assert.equal(await sheet.getByLabel('Tell us more (optional)').inputValue(),'Please improve the value');
  assert.equal(t.writes[0].number,'LATEST');assert.equal(t.writes[0].body.comment,'Price felt too high. Please improve the value');
  t.allowSave();await sheet.getByRole('button',{name:'Submit Review'}).click();await sheet.getByText('Thank you for your valuable feedback!').waitFor();
  assert.equal(t.writes[1].body.overallRating,1);assert.equal(new URL(t.page.url()).pathname,'/');
  assert.equal(t.historyCalls[0].limit,'1');assert.equal(t.historyCalls[0].status,'COMPLETED');assert.ok(t.reviewReads.every(n=>n==='LATEST'));
  assert.equal(await t.page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
  await sheet.getByRole('button',{name:'Close review'}).click();await sheet.waitFor({state:'hidden'});await t.context.close();
 }
 {
  const t=await setup();const sheet=t.page.getByRole('dialog',{name:'Rate your last order'});await sheet.waitFor({timeout:15000});
  const stars=sheet.getByRole('group',{name:'Overall order rating'}),suggestions=sheet.getByRole('group',{name:'Feedback suggestions'});
  assert.equal(await sheet.getByRole('button',{name:'Submit Review'}).isDisabled(),true);
  await stars.getByRole('button',{name:'1 star',exact:true}).click();await suggestions.getByRole('button',{name:'Taste needs improvement'}).click();
  for(const [rating,choice] of [[2,'Taste could be better'],[3,'Taste was okay'],[4,'Tasty food']]) {
   await stars.getByRole('button',{name:`${rating} stars`,exact:true}).click();
   assert.equal(await suggestions.getByRole('button',{name:choice}).isVisible(),true);
  }
  await stars.getByRole('button',{name:'5 stars',exact:true}).click();assert.equal(await suggestions.getByRole('button',{name:'Taste needs improvement'}).count(),0);
  await suggestions.getByRole('button',{name:'Loved the taste'}).click();await suggestions.getByRole('button',{name:'Quick pickup'}).click();
  await sheet.getByLabel('Tell us more (optional)').fill('x'.repeat(1000));assert.equal(await sheet.getByRole('button',{name:'Submit Review'}).isDisabled(),true);
  await sheet.getByLabel('Tell us more (optional)').fill('');await sheet.getByRole('button',{name:'Submit Review'}).click();await sheet.getByText('Thank you for your valuable feedback!').waitFor();
  assert.equal(t.writes[0].body.comment,'Loved the taste. Quick pickup');assert.equal(t.writes[0].body.overallRating,5);
  await t.context.close();
 }
 // A reviewed latest order never causes a prompt for an older unreviewed order.
 for(const config of [{orders:[row('LATEST',4),row('OLDER')]},{orders:[]},{historyStatus:401},{historyStatus:503},{eligible:false},{existing:{id:1,overallRating:4,comment:null,status:'HIDDEN',itemRatings:[]}},{reviewStatus:503}]){
  const t=await setup(config);await t.page.waitForTimeout(4500);
  assert.equal(await t.page.getByRole('dialog',{name:'Rate your last order'}).isVisible(),false);
  assert.ok(t.reviewReads.every(n=>n==='LATEST'));assert.equal(t.writes.length,0);await t.context.close();
 }
 {
  const orders=[row('LATEST'),row('OLDER')];const t=await setup({orders});const sheet=t.page.getByRole('dialog',{name:'Rate your last order'});await sheet.waitFor({timeout:15000});
  await sheet.getByRole('button',{name:'Maybe later'}).click();await sheet.waitFor({state:'hidden'});assert.equal(t.writes.length,0);
  orders.unshift(row('NEWEST'));await t.page.reload();await sheet.waitFor({timeout:15000});assert.equal(t.reviewReads.at(-1),'NEWEST');assert.equal(t.reviewReads.includes('OLDER'),false);await t.page.keyboard.press('Escape');await sheet.waitFor({state:'hidden'});assert.equal(t.writes.length,0);await t.context.close();
 }
 console.log('Return review: latest-only eligibility, rating suggestions, submission, recovery, dismissal and narrow layout passed');
} finally {await browser.close();}
