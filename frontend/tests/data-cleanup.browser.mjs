import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const browser=await chromium.launch({headless:true}),base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
try {
 for(const width of [390,1280]) {
  const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:'block'}),page=await context.newPage();
  let refreshGate=null,refreshRequested;
  let config={enabled:true,dailyTime:'03:30',retentionDays:90,revision:0},runs=0,fail=false,role='OWNER_ADMIN';
  let view={config,timeZone:'Asia/Kolkata',status:'NEVER',startedAt:null,finishedAt:null,trigger:null,error:null,deleted:{},limitPerCategory:500,running:false};
  await context.route('**/api/**',async route=>{
   const request=route.request(),path=new URL(request.url()).pathname,headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Headers':'content-type,x-staff-csrf','Access-Control-Allow-Methods':'GET,POST,PUT,OPTIONS','Access-Control-Expose-Headers':'X-Staff-CSRF'};
   if(request.method()==='OPTIONS')return route.fulfill({status:204,headers});
   let json=[];
   if(path==='/api/admin/auth/me')json={staffId:1,username:'owner',fullName:'Owner',roleName:role,branchIds:[1],permissions:['MENU_MANAGE','ORDER_VIEW']};
   else if(path==='/api/admin/data-cleanup') {
    if(request.method()==='GET' && refreshGate) {const snapshot={...view,config:{...config}};refreshRequested();await refreshGate;return route.fulfill({json:snapshot,headers});}
    if(request.method()==='PUT'){config={...request.postDataJSON(),revision:config.revision+1};view={...view,config};}
    json=view;
   } else if(path.endsWith('/data-cleanup/preview')) json={revision:config.revision,cutoff:'2026-07-11T00:00:00Z',eligible:{customerNotifications:3,customerDeliveries:2,staffAlerts:4,staffReads:8,staffDeliveries:4},limitPerCategory:500};
   else if(path.endsWith('/data-cleanup/run')) {
    assert.equal(request.postDataJSON().revision,config.revision);runs++;
    if(fail)return route.fulfill({status:500,json:{message:'Cleanup failed; no records were deleted.'},headers});
    view={...view,status:'SUCCEEDED',startedAt:'2026-10-09T00:00:00Z',finishedAt:'2026-10-09T00:00:02Z',trigger:'MANUAL',deleted:{customerNotifications:3,staffAlerts:4}};json=view;
   } else if(path==='/api/storefront/features')json={};
   return route.fulfill({json,headers:{...headers,'X-Staff-CSRF':'test-csrf'}});
  });
  await page.goto(`${base}/admin/data-cleanup`);
  await page.getByRole('heading',{name:'Daily schedule'}).waitFor();
  assert.equal(await page.getByLabel('Daily time (IST)').inputValue(),'03:30');
  assert.equal(await page.getByLabel('Keep history for (days)').inputValue(),'90');
  let releaseRefresh;
  refreshGate=new Promise(resolve=>{releaseRefresh=resolve;});
  const requestStarted=new Promise(resolve=>{refreshRequested=resolve;});
  await page.getByRole('button',{name:'Refresh',exact:true}).click();
  await requestStarted;
  assert.equal(await page.getByLabel('Keep history for (days)').isDisabled(),true);
  assert.equal(await page.getByLabel('Daily time (IST)').isDisabled(),true);
  assert.equal(await page.getByLabel('Run automatically every day').isDisabled(),true);
  assert.equal(await page.getByRole('button',{name:'Preview eligible records'}).isDisabled(),true);
  assert.equal(await page.getByRole('button',{name:'Save schedule'}).isDisabled(),true);
  assert.equal(await page.getByRole('button',{name:'Refreshing…',exact:true}).isDisabled(),true);
  refreshGate=null;releaseRefresh();
  await page.getByRole('button',{name:'Refresh',exact:true}).waitFor();
  assert.equal(await page.getByLabel('Keep history for (days)').isEnabled(),true);
  await page.getByLabel('Keep history for (days)').fill('120');
  await page.getByRole('button',{name:'Save schedule'}).click();
  await page.getByText('Daily cleanup settings saved.').waitFor();
  assert.equal(config.retentionDays,120);
  assert.equal(await page.getByLabel('Keep history for (days)').inputValue(),'120');
  await page.getByLabel('Run automatically every day').uncheck();
  assert.equal(await page.getByRole('button',{name:'Preview eligible records'}).isEnabled(),false);
  await page.getByLabel('Daily time (IST)').fill('05:45');
  await page.getByRole('button',{name:'Save schedule'}).click();
  await page.getByText('Daily cleanup settings saved.').waitFor();
  assert.equal(config.enabled,false);assert.equal(config.dailyTime,'05:45');
  await page.getByRole('button',{name:'Preview eligible records'}).click();
  const run=page.getByRole('button',{name:'Clean up these old records'});await run.waitFor();
  assert.equal(runs,0);await run.click();
  await page.getByText('Cleanup finished. Deleted counts are shown below.').waitFor();
  assert.equal(runs,1);await page.getByText('Customer updates: 3 deleted',{exact:true}).waitFor();
  fail=true;await page.getByRole('button',{name:'Preview eligible records'}).click();await run.click();
  await page.getByRole('alert').filter({hasText:'Cleanup failed; no records were deleted.'}).waitFor();
  assert.equal(runs,2);assert.equal(await run.count(),0);
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth),true);
  role='BRANCH_MANAGER';await page.reload();await page.getByText('Only the owner can manage data cleanup.').waitFor();
  assert.equal(await page.getByRole('button',{name:'Preview eligible records'}).count(),0);
  await context.close();console.log(`Data cleanup admin passed at ${width}px`);
 }
 for(const kind of ['save','preview','run']) {
  const context=await browser.newContext({viewport:{width:390,height:900},serviceWorkers:'block'}),page=await context.newPage();
  page.setDefaultTimeout(10000);
  // Drive AbortSignal.timeout with the browser clock, without waiting 90 real seconds.
  await page.clock.install();
  await context.addInitScript(()=>{AbortSignal.timeout=milliseconds=>{
   const controller=new AbortController();
   setTimeout(()=>controller.abort(new DOMException('Request deadline exceeded','TimeoutError')),milliseconds);
   return controller.signal;
  };});
  let config={enabled:true,dailyTime:'03:30',retentionDays:90,revision:0},runs=0,stall=false,recoveryFails=false,releaseAction,actionStarted;
  let view={config,timeZone:'Asia/Kolkata',status:'NEVER',startedAt:null,finishedAt:null,trigger:null,error:null,deleted:{},limitPerCategory:500,running:false};
  const gate=new Promise(resolve=>{releaseAction=resolve;}),started=new Promise(resolve=>{actionStarted=resolve;});
  await context.route('**/api/**',async route=>{
   const request=route.request(),path=new URL(request.url()).pathname;
   const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Headers':'content-type,x-staff-csrf','Access-Control-Allow-Methods':'GET,POST,PUT,OPTIONS','Access-Control-Expose-Headers':'X-Staff-CSRF','X-Staff-CSRF':'test-csrf'};
   if(request.method()==='OPTIONS')return route.fulfill({status:204,headers});
   let json=[];
   if(path==='/api/admin/auth/me')json={staffId:1,username:'owner',fullName:'Owner',roleName:'OWNER_ADMIN',branchIds:[1],permissions:['MENU_MANAGE']};
   else if(path==='/api/admin/data-cleanup') {
    if(request.method()==='GET'&&recoveryFails)return route.fulfill({status:503,headers,json:{message:'Cannot confirm result'}});
    if(request.method()==='PUT'){config={...request.postDataJSON(),revision:config.revision+1};view={...view,config};}
    json=view;
   } else if(path.endsWith('/data-cleanup/preview'))json={revision:config.revision,cutoff:'2026-07-11T00:00:00Z',eligible:{customerNotifications:3},limitPerCategory:500};
   else if(path.endsWith('/data-cleanup/run')){
    runs++;view={...view,status:'SUCCEEDED',startedAt:'2026-10-09T00:00:00Z',finishedAt:'2026-10-09T00:00:02Z',trigger:'MANUAL',deleted:{customerNotifications:3}};json=view;
   }
   if(stall&&((kind==='save'&&request.method()==='PUT')||(kind==='preview'&&path.endsWith('/preview'))||(kind==='run'&&path.endsWith('/run')))){
    actionStarted();await gate;
   }
   return route.fulfill({json,headers}).catch(()=>{});
  });
  await page.goto(`${base}/admin/data-cleanup`);await page.getByRole('heading',{name:'Daily schedule'}).waitFor();
  if(kind==='save')await page.getByLabel('Keep history for (days)').fill('180');
  if(kind==='run'){
   await page.getByRole('button',{name:'Preview eligible records'}).click();
   await page.getByRole('button',{name:'Clean up these old records'}).waitFor();recoveryFails=true;
  }
  stall=true;
  await page.getByRole('button',{name:kind==='save'?'Save schedule':kind==='preview'?'Preview eligible records':'Clean up these old records',exact:true}).click();await started;
  assert.equal(await page.getByRole('button',{name:'Refresh',exact:true}).isDisabled(),true);
  await page.clock.fastForward(kind==='run'?90001:15001);
  await page.getByRole('alert').filter({hasText:'timed out'}).waitFor();
  assert.equal(await page.getByRole('button',{name:'Refresh',exact:true}).isEnabled(),true,'timeout releases the operation lock');
  assert.equal(await page.getByLabel('Keep history for (days)').isEnabled(),true,'timeout releases the form');
  assert.equal(runs,kind==='run'?1:0,'timeouts never retry cleanup automatically');
  if(kind==='run'){
   assert.equal(await page.getByRole('button',{name:'Preview eligible records'}).isDisabled(),true,'unconfirmed execution requires Refresh');
   assert.equal(await page.getByRole('button',{name:'Clean up these old records'}).count(),0,'an uncertain run cannot reuse its preview');
   recoveryFails=false;
  }
  await page.getByRole('button',{name:'Refresh',exact:true}).click();
  await page.getByRole('button',{name:'Preview eligible records'}).waitFor();
  await page.waitForFunction(()=>Array.from(document.querySelectorAll('button')).some(b=>b.textContent==='Preview eligible records'&&!b.disabled));
  if(kind==='save')assert.equal(await page.getByLabel('Keep history for (days)').inputValue(),'180','Refresh confirms a save whose response was lost');
  if(kind==='run')await page.getByText('Customer updates: 3 deleted',{exact:true}).waitFor();
  releaseAction();await context.close();console.log(`Data cleanup ${kind} timeout and recovery passed`);
 }
} finally {await browser.close();}
