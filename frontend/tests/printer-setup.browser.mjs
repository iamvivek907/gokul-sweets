import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const browser=await chromium.launch({headless:true}),base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
try {
 for(const width of [390,1280]) {
  const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:'block'}),page=await context.newPage();
  await page.clock.install();
  let reads=0,release,started;
  const firstStarted=new Promise(resolve=>{started=resolve;});
  let gate=new Promise(resolve=>{release=resolve;});
  let state={configured:true,enabled:false,online:true,profile:{branchId:1,station:'KITCHEN',agentId:'shop',printerCode:'KITCHEN_MAIN',protocol:'ESC_POS_USB',target:'Test Queue',port:9100,baudRate:9600,paperWidthMm:80,autoCut:false},runtime:{status:'READY',devices:{usb:['Test Queue'],bluetooth:[]}}};
  await context.route('**/api/**',async route=>{
   const request=route.request(),path=new URL(request.url()).pathname;
   const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Headers':'content-type,x-staff-csrf','Access-Control-Allow-Methods':'GET,POST,OPTIONS','Access-Control-Expose-Headers':'X-Staff-CSRF','X-Staff-CSRF':'test-csrf'};
   if(request.method()==='OPTIONS')return route.fulfill({status:204,headers});
   let json=[];
   if(path==='/api/admin/auth/me')json={staffId:1,username:'owner',fullName:'Owner',roleName:'OWNER_ADMIN',branchIds:[1],permissions:['ORDER_VIEW','BRANCH_MANAGE','ORDER_START_PREPARATION']};
   else if(path==='/api/branches')json=[{id:1,name:'Test shop',code:'TEST',active:true}];
   else if(path==='/api/admin/printing/station') {
    reads++;json=structuredClone(state);
    if(gate){started();await gate;}
   } else if(path.endsWith('/printing/station/mode')) {
    assert.equal(request.headers()['x-staff-csrf'],'test-csrf');
    state={...state,enabled:request.postDataJSON().enabled};json=state;
   }
   await route.fulfill({json,headers});
  });
  await page.goto(`${base}/admin/printing/setup`);await firstStarted;
  await page.clock.fastForward(15000);
  assert.equal(reads,1,'Slow requests must not be superseded by overlapping polls');
  const firstReply=page.waitForResponse(response=>new URL(response.url()).pathname==='/api/admin/printing/station');
  const releaseFirst=release;gate=null;releaseFirst();await firstReply;
  const start=page.getByRole('button',{name:'Start printing',exact:true});
  await start.waitFor();await page.waitForFunction(()=>[...document.querySelectorAll('button')].some(button=>button.textContent==='Start printing'&&!button.disabled));
  assert.equal(await page.getByText('Paused',{exact:true}).isVisible(),true);
  // A staff mutation must still invalidate a poll already in flight.
  const secondStarted=new Promise(resolve=>{started=resolve;});gate=new Promise(resolve=>{release=resolve;});
  await page.clock.fastForward(5000);await secondStarted;
  const staleReply=page.waitForResponse(response=>new URL(response.url()).pathname==='/api/admin/printing/station');
  await start.click();await page.getByText('Running',{exact:true}).waitFor();
  const releaseSecond=release;gate=null;releaseSecond();await (await staleReply).finished();
  await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
  assert.equal(await page.getByText('Running',{exact:true}).isVisible(),true);
  assert.equal(await page.getByRole('button',{name:'Pause printing',exact:true}).isEnabled(),true);
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
  await context.close();console.log(`Printer setup slow polling and mutation ordering passed at ${width}px`);
 }
} finally {await browser.close();}
