import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {recoveryContext,recoveryFailure} from './recoveryEvidence.mjs';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const browser=await chromium.launch({headless:true});
const base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
const branch={id:1,name:'Kitchen',code:'TEST',active:true,pickupAvailable:true};
try {
 for (const width of [390,1280]) {
  const context=await recoveryContext(browser,{viewport:{width,height:900}});
  let failChecks=false,waiting=4,checks=0;
  await context.route('**/api/**',async route=>{
   const request=route.request(),path=new URL(request.url()).pathname;
   const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Headers':'content-type,x-staff-csrf','Access-Control-Allow-Methods':'GET,POST,OPTIONS'};
   if(request.method()==='OPTIONS')return route.fulfill({status:204,headers});
   let json=[];
   if(path==='/api/admin/auth/me')return route.fulfill({json:{staffId:1,username:'kitchen',fullName:'Kitchen staff',roleName:'KITCHEN_STAFF',branchIds:[1],permissions:['ORDER_VIEW','ORDER_START_PREPARATION','ORDER_MARK_READY']},headers:{...headers,'X-Staff-CSRF':'test','Access-Control-Expose-Headers':'X-Staff-CSRF'}});
   if(path==='/api/storefront/features')json={adminPreparationBoard:true};
   else if(path==='/api/branches')json=[branch];
   else if(path==='/api/admin/notifications/settings')json={enabled:false};
   else if(path==='/api/admin/orders/planning/alerts') {
    checks++;if(failChecks)return route.fulfill({status:503,json:{},headers});
    json={needsPreparation:waiting,readyOverdue:0};
   } else if(path==='/api/admin/orders/planning')json={orders:[],slots:[],counts:{WAITING:0,IN_PROGRESS:0},page:0,total:0};
   else if(path==='/api/admin/orders/queue/counts')json={overdue:0,eligible:0,scheduled:0,preparing:0,ready:0,actionableTotal:0,confirmedTotal:0};
   return route.fulfill({json,headers});
  });
  await context.addInitScript(()=>{
   window.__chimes=0;window.__blocked=true;window.__hang=false;window.__contexts=[];
   window.addEventListener('pointerdown',()=>{window.__blocked=false;},true);
   window.AudioContext=class {
    state='suspended';currentTime=0;destination={};onstatechange=null;
    constructor(){window.__contexts.push(this);}
    change(state){this.state=state;this.onstatechange?.();}
    resume(){if(window.__hang)return new Promise(resolve=>{window.__finishResume=()=>{this.change('running');resolve();};});if(window.__blocked)return Promise.reject(new Error('Autoplay blocked'));this.change('running');return Promise.resolve();}
    async suspend(){this.change('suspended');}async close(){this.change('closed');}
    createOscillator(){return {frequency:{value:0},connect(){},start(){window.__chimes++;},stop(){}};}
    createGain(){return {gain:{setValueAtTime(){},exponentialRampToValueAtTime(){}},connect(){}};}
   };
  });
  const page=await context.newPage(),errors=[];page.on('pageerror',error=>errors.push(error.message));
  await page.goto(`${base}/admin/order-desk`);
  await page.getByRole('button',{name:'Enable kitchen alarm',exact:true}).click();
  await page.waitForFunction(()=>window.__chimes>=3);
  await page.reload();
  await page.getByRole('button',{name:'Resume kitchen sound',exact:true}).waitFor();
  await page.getByText(/^4 orders need preparation/).waitFor();
  assert.equal(await page.evaluate(()=>window.__chimes),0,'saved enabled preference cannot bypass autoplay');
  await page.getByRole('button',{name:'Resume kitchen sound',exact:true}).click();
  await page.waitForFunction(()=>window.__chimes>=3);
  // Inactivity suspension is observed even with no tap, then recovered automatically.
  await page.evaluate(()=>window.__contexts.at(-1).change('suspended'));
  const before=await page.evaluate(()=>window.__chimes);
  await page.waitForFunction(previous=>window.__chimes>previous,before);
  // Browser interruption that needs a gesture remains visible until an actual tap.
  await page.evaluate(()=>{window.__blocked=true;window.__contexts.at(-1).change('suspended');});
  await page.getByRole('button',{name:'Resume kitchen sound',exact:true}).waitFor();
  await page.waitForTimeout(5500);
  assert.equal(await page.getByRole('button',{name:'Resume kitchen sound',exact:true}).count(),1);
  await page.getByText(/^4 orders need preparation/).click();
  await page.waitForFunction(()=>window.__contexts.at(-1).state==='running');
  // A background tab can recover too; returning to it refreshes authoritative counts.
  await page.evaluate(()=>{Object.defineProperty(document,'visibilityState',{configurable:true,get:()=> 'hidden'});window.__contexts.at(-1).change('suspended');document.dispatchEvent(new Event('visibilitychange'));});
  const hidden=await page.evaluate(()=>window.__chimes);
  await page.waitForFunction(previous=>window.__chimes>previous,hidden);
  // A timed-out automatic attempt cannot overwrite a later successful gesture.
  await page.evaluate(()=>{window.__hang=true;window.__contexts.at(-1).change('suspended');window.dispatchEvent(new Event('focus'));});
  await page.getByRole('button',{name:'Resume kitchen sound',exact:true}).waitFor();
  await page.evaluate(()=>{window.__hang=false;});
  await page.getByText(/^4 orders need preparation/).click();
  await page.waitForFunction(()=>window.__contexts.at(-1).state==='running');
  await page.waitForTimeout(2500);
  assert.equal(await page.getByRole('button',{name:'Resume kitchen sound',exact:true}).count(),0);
  // Temporary network failures do not instantly silence still-fresh due work.
  failChecks=true;
  await page.evaluate(()=>window.dispatchEvent(new Event('gokul-kitchen-changed')));
  await page.getByText(/^Live alert checks are delayed/).waitFor();
  const transient=await page.evaluate(()=>window.__chimes);
  await page.waitForFunction(previous=>window.__chimes>previous,transient);
  // Simulate long inactivity/network loss without waiting a real minute.
  await page.clock.install();await page.clock.fastForward(20*60*1000);
  const stale=await page.evaluate(()=>window.__chimes);
  await page.clock.runFor(4000);
  assert.equal(await page.evaluate(()=>window.__chimes),stale,'no chimes from stale counts');
  failChecks=false;waiting=0;
  await page.evaluate(()=>{delete document.visibilityState;window.dispatchEvent(new Event('focus'));});
  await page.getByText('No orders need action. Alarm is watching for new work.').waitFor();
  await page.clock.runFor(4000);
  assert.equal(await page.evaluate(()=>window.__chimes),stale,'fresh zero counts stop sound');
  // A late resume cannot re-enable a disabled alarm, even with newly due work.
  waiting=4;
  await page.evaluate(()=>window.dispatchEvent(new Event('gokul-kitchen-changed')));
  await page.getByText(/^4 orders need preparation/).waitFor();
  await page.evaluate(()=>{window.__hang=true;window.__contexts.at(-1).change('suspended');window.dispatchEvent(new Event('focus'));});
  await page.getByRole('button',{name:'Disable kitchen alarm',exact:true}).click();
  const muted=await page.evaluate(()=>window.__chimes);
  await page.evaluate(()=>{window.__hang=false;window.__finishResume?.();});
  await page.clock.runFor(5000);
  assert.equal(await page.evaluate(()=>window.__chimes),muted);
  await page.getByRole('button',{name:'Enable kitchen alarm',exact:true}).waitFor();
  assert.equal(await page.evaluate(()=>localStorage.getItem(Object.keys(localStorage).find(key=>key.startsWith('gokul-kitchen-alarm:')))),'off');
  assert.deepEqual(errors,[]);assert.ok(checks>1);
  console.log(`Kitchen alarm recovery, autoplay, background and stale-data guards pass at ${width}px`);
  await context.close();
 }
} catch(error){await recoveryFailure(browser,'kitchen-alarm-recovery');throw error;} finally {await browser.close();}
