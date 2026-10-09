import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const browser=await chromium.launch({headless:true}),base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
try {
 for(const width of [390,1280]) {
  const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:'block'}),page=await context.newPage();
  await page.clock.install();
  await context.addInitScript(()=>{AbortSignal.timeout=milliseconds=>{
   const controller=new AbortController();
   setTimeout(()=>controller.abort(new DOMException('Request deadline exceeded','TimeoutError')),milliseconds);
   return controller.signal;
  };});
  let reads=0,release,started,stalledRequest;
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
    reads++;stalledRequest=request;json=structuredClone(state);
    if(gate){started();await gate;}
   } else if(path.endsWith('/printing/station/mode')) {
    assert.equal(request.headers()['x-staff-csrf'],'test-csrf');
    state={...state,enabled:request.postDataJSON().enabled};json={...state,actionReceipt:{requestId:new URL(request.url()).searchParams.get("requestId"),outcome:"ACCEPTED"}};
   }
   try {await route.fulfill({json,headers});}
   catch(error){if(!request.failure())throw error;}
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
  // An unresponsive poll must time out and allow the next poll to recover.
  const stalledStarted=new Promise(resolve=>{started=resolve;});
  gate=new Promise(resolve=>{release=resolve;});
  await page.clock.fastForward(5000);await stalledStarted;
  const stalledReads=reads;
  const failedRequest=page.waitForEvent('requestfailed',{predicate:request=>request===stalledRequest});
  await page.clock.fastForward(30001);await failedRequest;
  await page.getByText('Printer status request timed out. Retrying automatically.',{exact:true}).waitFor();
  assert.equal(reads,stalledReads,'A stuck request must be aborted before a new poll starts');
  // Abort left the old route pending; release it before starting a healthy read.
  const releaseStalled=release;gate=null;releaseStalled();
  state={...state,enabled:false};
  const recoveredReply=page.waitForResponse(response=>new URL(response.url()).pathname==='/api/admin/printing/station');
  // Process each timer tick and its network callbacks instead of jumping directly to
  // one assumed interval boundary after the abort. A stuck guard still fails within two periods.
  for(let tick=0;tick<10&&reads===stalledReads;tick++) {
   await page.clock.runFor(1000);
   await page.evaluate(()=>Promise.resolve());
  }
  assert.equal(reads,stalledReads+1,'Polling must recover within two intervals after abort');
  await recoveredReply;
  await page.getByText('Paused',{exact:true}).waitFor();
  await page.waitForFunction(()=>!document.body.textContent.includes('Printer status request timed out. Retrying automatically.'));
  assert.equal(reads,stalledReads+1);
  assert.equal(await start.isEnabled(),true);
  await context.close();console.log(`Printer setup slow polling, mutation ordering and stalled-request recovery passed at ${width}px`);
 }
 for(const width of [390,1280]) for(const action of ['mode','TEST','RETRY','PRINTED','profile','rejected']) {
  const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:'block'}),page=await context.newPage();
  await page.clock.install();
  await context.addInitScript(()=>{AbortSignal.timeout=milliseconds=>{
   const controller=new AbortController();
   setTimeout(()=>controller.abort(new DOMException('Request deadline exceeded','TimeoutError')),milliseconds);
   return controller.signal;
  };});
  let posts=0,holdReads=false,releasePost,releaseRead,postStarted,readStarted;
  const postGate=new Promise(resolve=>{releasePost=resolve;});
  const readGate=new Promise(resolve=>{releaseRead=resolve;});
  const posted=new Promise(resolve=>{postStarted=resolve;});
  const reading=new Promise(resolve=>{readStarted=resolve;});
  const recovery=['RETRY','PRINTED'].includes(action);
  let state={configured:true,enabled:false,online:true,profile:{branchId:1,station:'KITCHEN',agentId:'shop',printerCode:'KITCHEN_MAIN',protocol:'ESC_POS_USB',target:'Test Queue',port:9100,baudRate:9600,paperWidthMm:80,autoCut:false},runtime:{status:recovery?'NEEDS_ATTENTION':'READY',pendingJobId:recovery?12:undefined,devices:{usb:['Test Queue'],bluetooth:[]}}};
  await context.route('**/api/**',async route=>{
   const request=route.request(),path=new URL(request.url()).pathname;
   const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Headers':'content-type,x-staff-csrf','Access-Control-Allow-Methods':'GET,POST,OPTIONS','Access-Control-Expose-Headers':'X-Staff-CSRF','X-Staff-CSRF':'test-csrf'};
   if(request.method()==='OPTIONS')return route.fulfill({status:204,headers});
   let json=[];
   if(path==='/api/admin/auth/me')json={staffId:1,username:'owner',fullName:'Owner',roleName:'OWNER_ADMIN',branchIds:[1],permissions:['ORDER_VIEW','BRANCH_MANAGE','ORDER_START_PREPARATION']};
   else if(path==='/api/branches')json=[{id:1,name:'Test shop',code:'TEST',active:true}];
   else if(path==='/api/admin/printing/station'||path.endsWith('/station/reconcile')) {
    if(holdReads){readStarted();await readGate;return route.fulfill({status:503,json:{message:'Reconciliation temporarily unavailable'},headers});}
    json=path.endsWith("/reconcile") ? {...state,actionReceipt:{requestId:new URL(request.url()).searchParams.get("requestId"),outcome:"ACCEPTED"}} : state;
   } else if(path.startsWith('/api/admin/printing/station/')&&request.method()==='POST') {
    assert.equal(request.headers()['x-staff-csrf'],'test-csrf');posts++;
    if(action==='rejected'){postStarted();return route.fulfill({status:409,json:{message:'Printer has pending work.'},headers});}
    if(action==='mode')state={...state,enabled:true};
    else if(action==='profile')state={...state,profile:request.postDataJSON()};
    else if(action==='TEST')state={...state,command:{id:'accepted-test'}};
    else state={...state,runtime:{...state.runtime,pendingJobId:undefined,status:'READY'},commandResult:{status:'DONE',message:'Pending ticket resolved.'}};
    json={...state,actionReceipt:{requestId:new URL(request.url()).searchParams.get("requestId"),outcome:"ACCEPTED"}};postStarted();await postGate;
   }
   try {await route.fulfill({json,headers});}
   catch(error){if(!request.failure())throw error;}
  });
  await page.goto(`${base}/admin/printing/setup`);
  await page.waitForFunction(()=>[...document.querySelectorAll('button')].some(button=>button.textContent==='Save printer settings'&&!button.disabled)||document.body.textContent.includes('I checked the paper and spooler for this ticket.'));
  const names={mode:'Start printing',TEST:'Print test ticket',RETRY:'Approve another attempt',PRINTED:'Ticket already printed',profile:'Save printer settings',rejected:'Print test ticket'};
  if(recovery)await page.getByRole('checkbox',{name:'I checked the paper and spooler for this ticket.'}).check();
  holdReads=action!=='rejected';
  await page.getByRole('button',{name:names[action],exact:true}).click();await posted;
  if(action==='rejected') {
   await page.getByText('Printer has pending work.',{exact:true}).waitFor();
   await page.waitForFunction(()=>[...document.querySelectorAll('button')].some(button=>button.textContent==='Print test ticket'&&!button.disabled));
   await page.clock.fastForward(5000);
   assert.equal(await page.getByText('Printer has pending work.',{exact:true}).isVisible(),true);
   assert.equal(posts,1);await context.close();console.log(`Printer rejected action preserves its error at ${width}px`);continue;
  }
  await page.clock.fastForward(30001);
  await page.getByText(/The action reply was lost. Checking server status/).waitFor();
  assert.equal(posts,1);
  assert.equal(await page.getByRole('button',{name:'Start printing',exact:true}).isDisabled(),true);
  assert.equal(await page.getByRole('button',{name:'Pause printing',exact:true}).isDisabled(),true);
  assert.equal(await page.getByRole('button',{name:'Print test ticket',exact:true}).isDisabled(),true);
  assert.equal(await page.getByRole('button',{name:'Save printer settings',exact:true}).isDisabled(),true);
  assert.equal(await page.locator('select').first().isEnabled(),true,'The busy state must be released');
  releasePost();
  await page.clock.fastForward(5000);await reading;
  assert.equal(await page.getByRole('button',{name:'Start printing',exact:true}).isDisabled(),true);
  releaseRead();await page.getByText('Reconciliation temporarily unavailable',{exact:true}).waitFor();
  assert.equal(await page.getByRole('button',{name:'Print test ticket',exact:true}).isDisabled(),true);
  holdReads=false;
  await page.clock.fastForward(5000);
  await page.getByText('Server status refreshed. Check the paper and action result before requesting another printer action.',{exact:true}).waitFor();
  assert.equal(posts,1,'Uncertain mutations must never be automatically replayed');
  if(action==='mode')assert.equal(await page.getByRole('button',{name:'Pause printing',exact:true}).isEnabled(),true);
  else if(action==='TEST')assert.equal(await page.getByRole('button',{name:'Print test ticket',exact:true}).isDisabled(),true,'The accepted command remains pending');
  else assert.equal(await page.getByRole('button',{name:'Start printing',exact:true}).isEnabled(),true);
  await context.close();console.log(`Printer ${action} lost reply and failed/successful reconciliation passed at ${width}px`);
 }
 for(const width of [390,1280]) {
  const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:'block'}),page=await context.newPage();
  await page.clock.install();
  await context.addInitScript(()=>{AbortSignal.timeout=milliseconds=>{
   const controller=new AbortController();
   setTimeout(()=>controller.abort(new DOMException('Request deadline exceeded','TimeoutError')),milliseconds);
   return controller.signal;
  };});
  let reads=0,release,started,posts=0,commitFirst,firstPostStarted,reconciles=0,cancelled=false,requestId;
  const firstCommitGate=new Promise(resolve=>{commitFirst=resolve;});
  const firstPosting=new Promise(resolve=>{firstPostStarted=resolve;});
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
   } else if(path.endsWith('/printing/station/reconcile')) {
    assert.equal(new URL(request.url()).searchParams.get('requestId'),requestId);reconciles++;
    if(reconciles===1)json=state;
    else {cancelled=true;json={...state,actionReceipt:{requestId,outcome:'CANCELLED'}};}
   } else if(path.endsWith('/printing/station/command')) {
    posts++;const id=posts;requestId=new URL(request.url()).searchParams.get('requestId');assert.match(requestId,/^[0-9a-f-]{36}$/);
    if(id===1){firstPostStarted();await firstCommitGate;}
    assert.equal(cancelled,true);json={...state,actionReceipt:{requestId,outcome:'CANCELLED'}};
   } else if(path.endsWith('/printing/station/mode')) {
    assert.equal(request.headers()['x-staff-csrf'],'test-csrf');
    state={...state,enabled:request.postDataJSON().enabled};json=state;
   }
   try {await route.fulfill({json,headers});}
   catch(error){if(!request.failure())throw error;}
  });
  await page.goto(`${base}/admin/printing/setup`);await firstStarted;
  await page.clock.fastForward(15000);
  assert.equal(reads,1,'Slow requests must not be superseded by overlapping polls');
  const firstReply=page.waitForResponse(response=>new URL(response.url()).pathname==='/api/admin/printing/station');
  const releaseFirst=release;gate=null;releaseFirst();await firstReply;
  const start=page.getByRole('button',{name:'Start printing',exact:true});
  await start.waitFor();await page.waitForFunction(()=>[...document.querySelectorAll('button')].some(button=>button.textContent==='Start printing'&&!button.disabled));
  assert.equal(await page.getByText('Paused',{exact:true}).isVisible(),true);
  const test=page.getByRole('button',{name:'Print test ticket',exact:true});
  await test.click();await firstPosting;
  await page.clock.fastForward(30001);
  await page.getByText(/The action reply was lost. Checking server status/).waitFor();
  const snapshotReply=page.waitForResponse(response=>new URL(response.url()).pathname.endsWith('/station/reconcile'));
  await page.clock.fastForward(5000);
  await (await snapshotReply).finished();
  await page.waitForFunction(()=>document.body.textContent.includes('The action reply was lost.'));
  assert.equal(reconciles,1);
  assert.equal(await test.isDisabled(),true,'A fresh snapshot without the exact receipt must not unlock another test');
  await page.reload();
  await page.getByText('The previous action was cancelled before acceptance. It will not run later. Check the paper before requesting another print.',{exact:true}).waitFor();
  assert.equal(reconciles,2,'Reload must restore and reconcile the same pending request');
  assert.equal(await test.isEnabled(),true);
  commitFirst();
  await new Promise(resolve=>setTimeout(resolve,100));
  assert.equal(posts,1,'Uncertain requests must never be resent');
  assert.equal(state.command?.id,undefined,'The cancelled original cannot queue a late test');
  assert.equal(await page.evaluate(()=>sessionStorage.getItem('gokul-printer-pending-actions')),'{}');
  await context.close();console.log(`Late commit cancellation and reload reconciliation passed at ${width}px`);
 }
 for(const width of [390,1280]) for(const oldReply of ['accepted','rejected']) {
  const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:'block'}),page=await context.newPage();
  let posts=0,releaseA,releaseB,startedA,startedB,finishedA,finishedB,ids=[],reconciledIds=[],cancelledB=false;
  const gateA=new Promise(r=>releaseA=r),gateB=new Promise(r=>releaseB=r);
  const aStarted=new Promise(r=>startedA=r),bStarted=new Promise(r=>startedB=r);
  const aFinished=new Promise(r=>finishedA=r),bFinished=new Promise(r=>finishedB=r);
  const state={configured:true,enabled:false,online:true,profile:{branchId:1,station:'KITCHEN',agentId:'shop',printerCode:'KITCHEN_MAIN',protocol:'ESC_POS_USB',target:'Test Queue',port:9100,baudRate:9600,paperWidthMm:80,autoCut:false},runtime:{status:'READY',devices:{usb:['Test Queue'],bluetooth:[]}},command:{}};
  await context.route('**/api/**',async route=>{
   const req=route.request(),url=new URL(req.url()),path=url.pathname;
   const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Headers':'content-type,x-staff-csrf','Access-Control-Allow-Methods':'GET,POST,OPTIONS','Access-Control-Expose-Headers':'X-Staff-CSRF','X-Staff-CSRF':'test-csrf'};
   if(req.method()==='OPTIONS')return route.fulfill({status:204,headers});
   let json=[],status=200,finished;
   if(path==='/api/admin/auth/me')json={staffId:1,username:'owner',fullName:'Owner',roleName:'OWNER_ADMIN',branchIds:[1],permissions:['ORDER_VIEW','BRANCH_MANAGE','ORDER_START_PREPARATION']};
   else if(path==='/api/branches')json=[{id:1,name:'Test shop',code:'TEST',active:true}];
   else if(path.endsWith('/station/reconcile')){
    const id=url.searchParams.get('requestId');reconciledIds.push(id);
    if(id===ids[1])cancelledB=true;
    json={...state,actionReceipt:{requestId:id,outcome:id===ids[0]&&oldReply==='accepted'?'ACCEPTED':'CANCELLED'}};
   } else if(path==='/api/admin/printing/station')json=state;
   else if(path.endsWith('/station/command')){
    posts++;const id=url.searchParams.get('requestId');ids.push(id);
    if(posts===1){startedA();await gateA;finished=finishedA;status=oldReply==='rejected'?409:200;json=status===409?{message:'Original action rejected.'}:{...state,actionReceipt:{requestId:id,outcome:'ACCEPTED'}};}
    else {startedB();await gateB;assert.equal(cancelledB,true,'The delayed second action must be cancelled by reload reconciliation');finished=finishedB;json={...state,actionReceipt:{requestId:id,outcome:'CANCELLED'}};}
   } else if(path.includes('counts'))json={queued:0,claimed:0,failed:0,printed:0,permanentlyFailed:0};
   else if(path.includes('print-jobs'))json={jobs:[],totalElements:0,totalPages:0,page:0,size:20};
   else if(path.includes('/printing/health'))json={branchId:1,station:'KITCHEN',agent:{status:'ONLINE'},printer:{status:'READY'},queue:{queued:0,claimed:0,failed:0,permanentlyFailed:0},activity:{}};
   try{await route.fulfill({status,json,headers});}catch(error){if(!req.failure())throw error;}finally{finished?.();}
  });
  await page.goto(base+'/admin/printing/setup');
  const test=page.getByRole('button',{name:'Print test ticket',exact:true});
  await page.waitForFunction(()=>[...document.querySelectorAll('button')].some(b=>b.textContent==='Print test ticket'&&!b.disabled));
  await test.click();await aStarted;
  await page.getByRole('link',{name:'Back to printer queue',exact:true}).click();
  await page.waitForURL('**/admin/printing');
  await page.getByRole('link',{name:'Set up a USB or Bluetooth printer',exact:true}).click();
  await page.getByText(oldReply==='accepted'?'Server status refreshed. Check the paper and action result before requesting another printer action.':'The previous action was cancelled before acceptance. It will not run later. Check the paper before requesting another print.',{exact:true}).waitFor();
  await test.click();await bStarted;
  const savedB=JSON.stringify({'1:KITCHEN':ids[1]});
  assert.equal(await page.evaluate(()=>sessionStorage.getItem('gokul-printer-pending-actions')),savedB);
  releaseA();await aFinished;
  await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
  assert.equal(await page.evaluate(()=>sessionStorage.getItem('gokul-printer-pending-actions')),savedB,'A reply to the unmounted page must not erase the newer request');
  await page.reload();
  await page.getByText('The previous action was cancelled before acceptance. It will not run later. Check the paper before requesting another print.',{exact:true}).waitFor();
  assert.deepEqual(reconciledIds,ids,'Reload must reconcile B using its preserved ID');
  assert.equal(await test.isEnabled(),true);
  releaseB();await bFinished;
  assert.equal(posts,2,'Neither uncertain request is automatically resent');
  await context.close();console.log(`Printer navigation and delayed ${oldReply} reply preserves newer action at ${width}px`);
 }
} finally {await browser.close();}
