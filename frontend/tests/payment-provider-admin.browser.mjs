import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const base=process.env.BROWSER_BASE??'http://127.0.0.1:3311',browser=await chromium.launch({headless:true});
const owner={staffId:7,username:'owner',fullName:'Owner',roleName:'OWNER_ADMIN',branchIds:[],permissions:[]};
const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Headers':'content-type,x-staff-csrf','Access-Control-Allow-Methods':'GET,PUT,OPTIONS','Access-Control-Expose-Headers':'X-Staff-CSRF','X-Staff-CSRF':'test-csrf'};
const initial=()=>({activeProvider:'PHONEPE',revision:0,providers:[{provider:'PHONEPE',ready:true,issues:[]},{provider:'RAZORPAY',ready:true,issues:[]}],history:[]});
try{
 for(const width of [320,390,1280]){
  const context=await browser.newContext({viewport:{width,height:950},serviceWorkers:'block'}),page=await context.newPage();
  let state=initial(),mode='success',writes=0;
  await context.route('**/api/**',async route=>{
   const request=route.request(),path=new URL(request.url()).pathname;
   if(request.method()==='OPTIONS')return route.fulfill({status:204,headers});
   if(path==='/api/admin/auth/me')return route.fulfill({json:owner,headers});
   if(path==='/api/admin/payment-providers'){
    if(request.method()==='GET')return route.fulfill({json:state,headers});
    writes++;const input=request.postDataJSON();assert.equal(request.headers()['x-staff-csrf'],'test-csrf');assert.equal(input.expectedRevision,state.revision);
    if(mode==='conflict'){state.activeProvider='RAZORPAY';state.revision++;return route.fulfill({status:409,json:{message:'Payment settings changed.'},headers});}
    state={...state,activeProvider:input.provider,revision:state.revision+1,history:[{previousProvider:state.activeProvider,activeProvider:input.provider,revision:state.revision+1,username:'owner',reason:input.reason,changedAt:'2026-10-13T07:00:00Z'},...state.history]};
    if(mode==='lost-ack')return route.abort('failed');
    return route.fulfill({json:state,headers});
   }
   return route.fulfill({json:[],headers});
  });
  await page.goto(`${base}/admin/payment-providers`);const active=page.getByRole('region',{name:'Active payment provider'});
  await active.getByText('PhonePe',{exact:true}).waitFor();
  await page.getByRole('radio').nth(1).check();await page.getByLabel('Reason for switching').fill('Validated DEV refund flow');await page.getByRole('button',{name:'Activate selected provider'}).click();
  await active.getByText('Razorpay',{exact:true}).waitFor();assert.equal(writes,1);
  await page.reload();await active.getByText('Razorpay',{exact:true}).waitFor();
  mode='conflict';await page.getByRole('radio').nth(0).check();await page.getByLabel('Reason for switching').fill('Stale owner');await page.getByRole('button',{name:'Activate selected provider'}).click();
  await page.getByRole('alert').filter({hasText:'Refresh to confirm'}).waitFor();assert.equal(await page.getByRole('button',{name:'Activate selected provider'}).count(),0);
  await page.getByRole('button',{name:'Refresh settings'}).click();await active.getByText('Razorpay',{exact:true}).waitFor();
  mode='lost-ack';await page.getByRole('radio').nth(0).check();await page.getByLabel('Reason for switching').fill('Lost acknowledgement');await page.getByRole('button',{name:'Activate selected provider'}).click();
  await page.getByRole('alert').filter({hasText:'Refresh to confirm'}).waitFor();const written=writes;
  await page.getByRole('button',{name:'Refresh settings'}).click();await active.getByText('PhonePe',{exact:true}).waitFor();assert.equal(writes,written,'refresh must not replay an uncertain mutation');
  state.providers[1]={provider:'RAZORPAY',ready:false,issues:['Configure Razorpay webhook secret.']};
  await page.getByRole('button',{name:'Refresh settings'}).click();await page.getByText('Configure Razorpay webhook secret.').waitFor();assert.equal(await page.getByRole('radio').nth(1).isDisabled(),true);
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
  assert.deepEqual(await page.getByRole('button',{name:'Activate selected provider'}).evaluate(el=>({text:getComputedStyle(el).color,background:getComputedStyle(el).backgroundColor})),{text:'rgb(255, 255, 255)',background:'rgb(122, 22, 37)'});
  if(process.env.BROWSER_SCREENSHOT_DIR)await page.screenshot({path:`${process.env.BROWSER_SCREENSHOT_DIR}/payment-providers-${width}.png`,fullPage:true});
  await context.close();console.log(`Payment provider owner controls passed at ${width}px`);
 }
 const context=await browser.newContext({serviceWorkers:'block'}),page=await context.newPage();let privateReads=0;
 await context.route('**/api/**',async route=>{
  const path=new URL(route.request().url()).pathname;
  if(route.request().method()==='OPTIONS')return route.fulfill({status:204,headers});
  if(path==='/api/admin/auth/me')return route.fulfill({json:{...owner,roleName:'MANAGER'},headers});
  if(path==='/api/admin/payment-providers')privateReads++;
  return route.fulfill({json:[],headers});
 });
 await page.goto(`${base}/admin/payment-providers`);await page.getByRole('alert').filter({hasText:'Only owners'}).waitFor();assert.equal(privateReads,0);await context.close();console.log('Manager cannot load provider configuration');
}finally{await browser.close();}
