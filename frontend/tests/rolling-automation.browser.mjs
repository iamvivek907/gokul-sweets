import assert from "node:assert/strict";
import {mkdir} from "node:fs/promises";
import {tmpdir} from "node:os";
import {join} from "node:path";
import {createRequire} from "node:module";
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??"playwright");
const browser=await chromium.launch({headless:true,...(process.env.BROWSER_EXECUTABLE?{executablePath:process.env.BROWSER_EXECUTABLE,args:["--no-sandbox"]}:{})});
const base=process.env.BROWSER_BASE??"http://127.0.0.1:3311";
const evidenceDir=join(tmpdir(),"gokul-layouts");
await mkdir(evidenceDir,{recursive:true});
try{
 for(const [width,viewOnly,largeCatalog,maximumMode] of [[390,false,false,false],[1280,false,false,false],[390,true,false,false],[1280,true,false,false],[390,false,true,false],[1280,false,true,false],[390,false,false,true],[1280,false,false,true]]){
  const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:"block"});
  let plan=null,saved=null,runs=0,saves=0,delayReload=false,releaseReload=null,reloadEntered=null,failReload=false;
  if(viewOnly)plan={enabled:true,openingTime:"10:00",closingTime:"21:00",slotMinutes:30,capacity:10,maintainDays:14,weekdays:127,version:0,templates:[{policyId:6,quantity:80,autoApprove:true}]};
  if(maximumMode)plan={enabled:false,openingTime:"10:00",closingTime:"21:00",slotMinutes:30,capacity:10,maintainDays:14,weekdays:127,version:0,usePolicyMaximum:false,templates:[{policyId:6,quantity:80,autoApprove:false}]};
  const policies=[{id:6,name:"Samosa",unit:"PIECE",bookingDays:14,buffer:2,maximum:100,readyStockRequired:false,eligible:true,selectable:true,unavailableReason:null},
   {id:7,name:"Rasmalai",unit:"PIECE",bookingDays:3,buffer:1,maximum:40,readyStockRequired:true,eligible:true,selectable:true,unavailableReason:null},
   {id:8,name:"Counter capacity",unit:"CAPACITY_POINT",bookingDays:14,buffer:0,maximum:100,readyStockRequired:false,eligible:false,selectable:false,unavailableReason:"Capacity-only policies do not support physical inventory automation."}];
  if(maximumMode)policies.push(
   {id:9,name:"No maximum",unit:"PIECE",bookingDays:14,buffer:0,maximum:null,readyStockRequired:false,eligible:true,selectable:true,unavailableReason:null},
   {id:10,name:"Buffer blocks",unit:"PIECE",bookingDays:14,buffer:10,maximum:10,readyStockRequired:false,eligible:true,selectable:true,unavailableReason:null},
   {id:11,name:"Fractional cap",unit:"PIECE",bookingDays:14,buffer:0,maximum:2.5,readyStockRequired:false,eligible:true,selectable:true,unavailableReason:null},
   {id:12,name:"Offline policy",unit:"PIECE",bookingDays:14,buffer:0,maximum:100,readyStockRequired:false,eligible:false,selectable:true,unavailableReason:"Offline policy is skipped."});
  if(largeCatalog)policies.push(...Array.from({length:300},(_,index)=>({id:100+index,name:`Fixture dish ${String(index+1).padStart(3,"0")}`,unit:"PIECE",bookingDays:14,buffer:0,maximum:100,readyStockRequired:false,eligible:true,selectable:true,unavailableReason:null})));
  await context.route("**/api/**",async route=>{
   const request=route.request(),path=new URL(request.url()).pathname;
   const headers={"Access-Control-Allow-Origin":base,"Access-Control-Allow-Credentials":"true",
    "Access-Control-Allow-Headers":"content-type,x-staff-csrf","Access-Control-Allow-Methods":"GET,POST,PUT,OPTIONS",
    "Access-Control-Expose-Headers":"X-Staff-CSRF","X-Staff-CSRF":"test-csrf"};
   if(request.method()==="OPTIONS")return route.fulfill({status:204,headers});
   if(path==="/api/admin/auth/me")return route.fulfill({headers,json:{staffId:77,roleName:viewOnly?"MANAGER":"OWNER_ADMIN",username:"owner",fullName:"Owner",branchIds:[1],permissions:viewOnly?["BRANCH_MANAGE","INVENTORY_VIEW"]:["BRANCH_MANAGE","INVENTORY_VIEW","INVENTORY_MANAGE","APPROVAL_MANAGE"]}});
   if(path==="/api/branches")return route.fulfill({headers,json:[{id:1,name:"Test branch",active:true}]});
   if(path.endsWith("/rolling-automation")&&request.method()==="PUT"){
    assert.equal(request.headers()["x-staff-csrf"],"test-csrf","writes carry the exposed CSRF token");
    saves++;saved=request.postDataJSON();plan={...saved,version:plan?plan.version+1:0};
   }
   if(path.endsWith("/rolling-automation/reconcile"))runs++;
   if(path.includes("/rolling-automation")){
    if(request.method()==="GET"&&failReload){failReload=false;return route.fulfill({status:503,headers,json:{message:"Temporary failure"}});}
    const json=JSON.parse(JSON.stringify({plan,policies,storefrontBookingDays:24,lastAttempt:runs?"2026-11-05T01:15:00Z":null,lastSuccess:runs?"2026-11-05T01:15:00Z":null,message:runs?"2 slots created; 1 allocations created":null}));
    if(request.method()==="GET"&&delayReload){delayReload=false;await new Promise(resolve=>{releaseReload=resolve;reloadEntered?.();});}
    return route.fulfill({headers,json});
   }
   return route.fulfill({headers,json:path.endsWith("/notifications")?{messages:[],unreadCount:0,nextBefore:null}:[]});
  });
  const page=await context.newPage();
  await page.goto(base+"/admin/rolling-automation");
  await page.getByRole("heading",{name:"Automatic slots & inventory",exact:true}).waitFor();
  const enabled=page.getByRole("checkbox",{name:"Enable daily setup for this branch",exact:true});
  await enabled.waitFor();
  await page.getByText("Saved stock details & run history",{exact:true}).click();
  if(!viewOnly&&!maximumMode)await page.getByRole("combobox",{name:"Inventory quantity source",exact:true}).selectOption("selected");
  async function checkHelp(title,copy){
   const trigger=page.getByRole("button",{name:`Help for ${title}`,exact:true});
   await trigger.click();
   const dialog=page.getByRole("dialog",{name:title,exact:true});
   await dialog.waitFor({state:"visible"});
   assert.match(await dialog.innerText(),copy);
   assert.equal(await dialog.evaluate(node=>{const box=node.getBoundingClientRect();return box.left>=0&&box.right<=innerWidth&&box.top>=0&&box.bottom<=innerHeight;}),true,"help fits the viewport");
   await page.keyboard.press("Escape");
   await dialog.waitFor({state:"hidden"});
   assert.equal(await trigger.evaluate(node=>node===document.activeElement),true,"help returns focus to its icon");
  }
  for(const [title,copy] of [
   ["Branch",/does not configure another branch/],
   ["Enable daily setup",/once daily at 12:00 AM IST/],
   ["Start time · IST",/10:00/],
   ["End time · IST",/20:30–21:00/],
   ["Slot duration · minutes",/5–180/],
   ["Orders per slot",/independent of dish stock/],
   ["Calendar days to maintain",/existing future-dated regular slots/],
   ["Selling weekdays",/No new allocations are generated on unselected weekdays/],
   ["Inventory policy: Samosa",/Unselected dishes receive no allocation/],
  ])await checkHelp(title,copy);
  assert.equal(saved,null,"opening help never saves the plan");
  assert.equal(runs,0,"opening help never runs generation");
  if(maximumMode){
   await checkHelp("Inventory quantity source",/buffer is deducted once|withholds 50 once/);
   const source=page.getByRole("combobox",{name:"Inventory quantity source",exact:true});
   await source.selectOption("maximum");await enabled.check();
   await page.getByTestId("draft-ready-count").filter({hasText:/^2$/}).waitFor();
   await page.getByText("Review dishes & quantities",{exact:true}).click();
   assert.equal(await page.getByRole("spinbutton",{name:/Guaranteed daily quantity/}).count(),0,"maximum mode needs no per-dish entries");
   await page.getByText("Planned daily stock: 100 PIECE · After buffer: 98 PIECE",{exact:true}).waitFor();
   await page.getByRole("combobox",{name:"Show",exact:true}).selectOption("attention");
   assert.equal(await page.locator('[data-policy-row]').count(),5,"unsupported, missing, fractional and buffer-blocked maxima are skipped");
   await page.getByRole("button",{name:"Save automation settings",exact:true}).click();
   await page.getByText("Schedule saved.",{exact:false}).waitFor();
   assert.equal(saved.usePolicyMaximum,true);
   assert.deepEqual(saved.templates,[{policyId:6,quantity:80,autoApprove:false}],"saved manual settings are preserved separately");
   await source.selectOption("selected");
   await page.getByRole("combobox",{name:"Show",exact:true}).selectOption("all");
   assert.equal(await page.getByRole("spinbutton",{name:/Guaranteed daily quantity/}).inputValue(),"80");
   await page.getByRole("button",{name:"Select all matching dishes",exact:true}).click();
   const samosa=page.locator('[data-policy-row]').filter({has:page.getByRole("checkbox",{name:"Samosa",exact:true})});
   assert.equal(await samosa.getByRole("spinbutton").inputValue(),"80","bulk preserves individual quantity");
   assert.equal(await samosa.getByRole("combobox",{name:"Approval mode",exact:true}).inputValue(),"draft","bulk preserves manual approval choice");
   assert.equal(await page.getByRole("checkbox",{name:"Offline policy",exact:true}).isChecked(),false,"bulk skips offline policies");
   const noMaximum=page.locator('[data-policy-row]').filter({has:page.getByRole("checkbox",{name:"No maximum",exact:true})});
   assert.equal(await noMaximum.getByRole("spinbutton").inputValue(),"","missing maximum needs an explicit quantity");
   await page.getByRole("button",{name:"Save automation settings",exact:true}).click();
   await page.getByRole("alert").filter({hasText:"selected dishes need a valid daily quantity"}).waitFor();
   assert.equal(saves,1,"bulk selection never saves invalid quantities");
   await context.close();console.log(`PASS: policy maxima preview, buffer-once, skip reasons and manual preservation at ${width}px`);continue;
  }
  if(largeCatalog){
   const rows=page.locator('[data-policy-row]');
   assert.equal(await rows.count(),25,"a 303-dish catalogue renders only the requested page");
   await enabled.check();
   await page.getByRole("checkbox",{name:"Samosa",exact:true}).check();
   await page.getByRole("spinbutton",{name:/Guaranteed daily quantity/}).fill("80");
   await page.getByRole("button",{name:"Next dishes",exact:true}).click();
   await page.getByText("Page 2 of 13",{exact:true}).waitFor();
   await page.getByRole("checkbox",{name:"Fixture dish 030",exact:true}).check();
   await page.getByRole("spinbutton",{name:/Guaranteed daily quantity/}).fill("7");
   await page.getByRole("button",{name:"Previous dishes",exact:true}).click();
   assert.equal(await page.getByRole("spinbutton",{name:/Guaranteed daily quantity/}).inputValue(),"80","returning to a page retains its quantity");
   await page.getByRole("combobox",{name:"Rows per page",exact:true}).selectOption("10");
   assert.equal(await rows.count(),10);
   await page.getByRole("combobox",{name:"Show",exact:true}).selectOption("selected");
   assert.equal(await rows.count(),2,"selected view includes selections from other pages");
   await page.getByRole("button",{name:"Save automation settings",exact:true}).click();
   await page.getByText("Schedule saved.",{exact:false}).waitFor();
   assert.deepEqual(saved.templates.map(item=>[item.policyId,item.quantity]).sort((a,b)=>a[0]-b[0]),[[6,80],[129,7]],"save retains off-page selections");
   const search=page.getByRole("searchbox",{name:"Find a dish",exact:true});
   await search.fill("nothing matches");await search.press("Enter");
   await page.getByText("No dishes match this view.",{exact:false}).waitFor();
   assert.equal(saves,1,"search Enter never submits settings");
   assert.equal(await page.getByRole("button",{name:"Next dishes",exact:true}).isDisabled(),true);
   await page.getByRole("combobox",{name:"Show",exact:true}).selectOption("all");
   await search.fill("Fixture dish 299");
   await page.getByRole("checkbox",{name:"Fixture dish 299",exact:true}).check();
   await search.fill("Samosa");
   await page.getByRole("button",{name:"Save automation settings",exact:true}).click();
   await page.getByRole("alert").filter({hasText:"selected dishes need a valid daily quantity"}).waitFor();
   assert.equal(saves,1,"invalid off-page quantities block saving");
   assert.equal(await search.inputValue(),"","validation clears a search that hides the invalid selection");
   assert.equal(await page.getByRole("combobox",{name:"Show",exact:true}).inputValue(),"attention");
   await page.getByRole("checkbox",{name:"Fixture dish 299",exact:true}).waitFor();
   assert.equal(await rows.count(),1);
   assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
   await page.getByRole("checkbox",{name:"Fixture dish 299",exact:true}).click();
   await page.getByRole("combobox",{name:"Show",exact:true}).selectOption("all");
   await search.fill("");
   await page.getByRole("button",{name:"Select all matching dishes",exact:true}).click();
   assert.equal(await page.getByRole("button",{name:"Select all matching dishes",exact:true}).isDisabled(),true,"all eligible matches already selected");
   await page.getByRole("button",{name:"Save automation settings",exact:true}).click();
   await page.getByText("Schedule saved.",{exact:false}).waitFor();
   assert.equal(saved.templates.length,302,"bulk selects eligible matches across every page, excluding capacity policies");
   assert.equal(saved.templates.find(item=>item.policyId===6).quantity,80,"bulk preserves edited quantities");
   assert.equal(saved.templates.find(item=>item.policyId===129).quantity,7,"bulk preserves off-page quantities");
   assert.equal(saved.templates.find(item=>item.policyId===398).quantity,100,"new bulk selections use the valid maximum");
   await search.fill("Fixture dish 299");
   await page.getByRole("button",{name:"Clear matching selections",exact:true}).click();
   assert.equal(await page.getByRole("checkbox",{name:"Fixture dish 299",exact:true}).isChecked(),false);
   await page.getByRole("button",{name:"Select all matching dishes",exact:true}).click();
   await search.fill("Samosa");
   assert.equal(await page.getByRole("spinbutton",{name:/Guaranteed daily quantity/}).inputValue(),"80","filtered clearing keeps nonmatching edits");
   await context.close();console.log(`PASS: 303 dishes, bounded pages, cross-page edits, search and hidden validation at ${width}px`);continue;
  }
  if(viewOnly){
   await checkHelp("Guaranteed daily quantity · PIECE",/1,000 means 1 kg/);
   await checkHelp("Approval mode",/staff must still record actual production readiness/);
   const controls=page.locator('form input:not([data-browse-control]), form select:not([data-browse-control]), form button[type="submit"]');
   assert.ok(await controls.count()>10);
   for(const control of await controls.all())assert.equal(await control.isDisabled(),true,"view-only configuration stays disabled");
   assert.equal(await page.getByRole("button",{name:"Generate missing coverage now",exact:true}).isDisabled(),true);
   assert.equal(await page.getByRole("button",{name:"Select all matching dishes",exact:true}).isDisabled(),true);
   assert.equal(await page.getByRole("button",{name:"Clear matching selections",exact:true}).isDisabled(),true);
   assert.equal(saved,null);assert.equal(runs,0);
   console.log(`PASS: read-only field help and write protection at ${width}px`);
   await context.close();continue;
  }
  assert.equal(await enabled.isChecked?.()??await enabled.evaluate(e=>e.checked),false);
  const run=page.getByRole("button",{name:"Generate missing coverage now",exact:true});
  assert.equal(await run.isDisabled(),true);
  assert.equal(await page.getByRole("checkbox",{name:"Counter capacity",exact:true}).isDisabled(),true);
  await page.getByText("Capacity-only policies do not support physical inventory automation.",{exact:true}).waitFor();
  await enabled.check();
  await page.getByRole("checkbox",{name:"Samosa",exact:true}).check();
  await checkHelp("Guaranteed daily quantity · PIECE",/1,000 means 1 kg/);
  await checkHelp("Approval mode",/staff must still record actual production readiness/);
  assert.equal(saved,null,"policy help never submits the form");
  await page.getByRole("spinbutton",{name:/Guaranteed daily quantity/}).fill("80");
  await page.getByRole("combobox",{name:"Approval mode",exact:true}).selectOption("approved");
  await page.getByRole("button",{name:"Save automation settings",exact:true}).click();
  await page.getByText("Schedule saved.",{exact:false}).waitFor();
  assert.deepEqual(saved.templates,[{policyId:6,quantity:80,autoApprove:true}]);
  assert.equal(saved.enabled,true);assert.equal(saved.maintainDays,14);
  delayReload=true;
  const reload=page.getByRole("button",{name:"Reload saved settings",exact:true});
  const save=page.getByRole("button",{name:"Save automation settings",exact:true});
  await reload.click();
  await page.getByText("Loading saved settings…",{exact:true}).waitFor();
  assert.equal(await enabled.isDisabled(),true);
  assert.equal(await save.isDisabled(),true,"a reload cannot overlap a save");
  assert.equal(await run.isDisabled(),true);
  await page.waitForFunction(()=>document.querySelector("main[aria-busy='true']")!==null);
  if(!releaseReload)await new Promise((resolve,reject)=>{
   const deadline=setTimeout(()=>reject(new Error("Reload request did not reach the mock")),15000);
   reloadEntered=()=>{clearTimeout(deadline);resolve();};
  });
  assert.equal(typeof releaseReload,"function");releaseReload();
  await page.getByText("Loading saved settings…",{exact:true}).waitFor({state:"hidden"});
  await page.getByRole("spinbutton",{name:/Guaranteed daily quantity/}).fill("81");
  await save.click();
  await page.getByText("Schedule saved.",{exact:false}).waitFor();
  assert.equal(saved.templates[0].quantity,81,"new edits after reload remain authoritative");
  failReload=true;
  await reload.click();
  await page.getByRole("alert").filter({hasText:"Could not load this branch"}).waitFor();
  await page.getByText("Loading saved settings…",{exact:true}).waitFor({state:"hidden"});
  assert.equal(await reload.isDisabled(),false,"a failed reload can be retried");
  assert.equal(await page.getByRole("spinbutton",{name:/Guaranteed daily quantity/}).inputValue(),"81");
  await reload.click();
  await page.getByRole("alert").filter({hasText:"Could not load this branch"}).waitFor({state:"hidden"});
  await page.getByText("Loading saved settings…",{exact:true}).waitFor({state:"hidden"});
  await run.click();
  await page.getByRole("status").filter({hasText:"Stock check finished"}).waitFor();
  assert.equal(runs,1);
  // A previously selected dish may become bulk-only after this schedule was saved.
  policies[0]={...policies[0],eligible:false,selectable:false,unavailableReason:"Bulk-only items are excluded from daily automation. Remove this saved selection."};
  await page.getByRole("button",{name:"Reload saved settings",exact:true}).click();
  await page.getByText("Bulk-only items are excluded from daily automation. Remove this saved selection.",{exact:true}).waitFor();
  await page.getByText("Loading saved settings…",{exact:true}).waitFor({state:"hidden"});
  assert.equal(await page.getByRole("checkbox",{name:"Samosa",exact:true}).isChecked(),true);
  assert.equal(await page.getByRole("checkbox",{name:"Samosa",exact:true}).isDisabled(),false,"saved unsupported selections can be removed");
  await enabled.uncheck();
  assert.equal(await run.isDisabled(),true,"unsaved edits cannot be used by generation");
  await page.getByRole("button",{name:"Save automation settings",exact:true}).click();
  await page.getByRole("status").filter({hasText:"Automation paused. This daily job will not generate"}).waitFor();
  assert.equal(saved.enabled,false);
  assert.equal(await run.isDisabled(),true);
  await page.getByRole("checkbox",{name:"Samosa",exact:true}).uncheck();
  await enabled.check();
  await page.getByRole("checkbox",{name:"Rasmalai",exact:true}).check();
  await page.getByRole("spinbutton",{name:/Guaranteed daily quantity/}).fill("30");
  await page.getByRole("button",{name:"Save automation settings",exact:true}).click();
  await page.getByText("Schedule saved.",{exact:false}).waitFor();
  assert.deepEqual(saved.templates,[{policyId:7,quantity:30,autoApprove:true}]);
  policies[1]={...policies[1],maximum:5};
  await page.getByRole("button",{name:"Reload saved settings",exact:true}).click();
  await page.getByText(/Maximum: 5 ·/).waitFor();
  await enabled.uncheck();
  await page.getByRole("button",{name:"Save automation settings",exact:true}).click();
  await page.getByRole("status").filter({hasText:"Automation paused. This daily job will not generate"}).waitFor();
  assert.equal(saved.enabled,false,"pause succeeds even after a policy limit becomes stricter");
  assert.equal(saved.templates[0].quantity,30);
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false,"automation form fits viewport");
  await context.close();
 }
 for(const width of [390,1280]) for(const readOnly of [false,true]) {
  const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:"block"});let runs=0;
  const plan={enabled:true,openingTime:"10:00",closingTime:"11:00",slotMinutes:30,capacity:10,maintainDays:14,weekdays:127,version:0,templates:[],usePolicyMaximum:true};
  await context.route("**/api/**",async route=>{
   const request=route.request(),path=new URL(request.url()).pathname;
   const headers={"Access-Control-Allow-Origin":base,"Access-Control-Allow-Credentials":"true","Access-Control-Allow-Headers":"content-type,x-staff-csrf","Access-Control-Allow-Methods":"GET,POST,PUT,OPTIONS","Access-Control-Expose-Headers":"X-Staff-CSRF","X-Staff-CSRF":"test-csrf"};
   if(request.method()==="OPTIONS")return route.fulfill({status:204,headers});
   if(path==="/api/admin/auth/me")return route.fulfill({headers,json:{staffId:77,roleName:readOnly?"MANAGER":"OWNER_ADMIN",username:"owner",fullName:"Owner",branchIds:[1],permissions:readOnly?["BRANCH_MANAGE","INVENTORY_VIEW"]:["BRANCH_MANAGE","INVENTORY_VIEW","INVENTORY_MANAGE","APPROVAL_MANAGE"]}});
   if(path==="/api/branches")return route.fulfill({headers,json:[{id:1,name:"Test branch",active:true}]});
   if(path.includes("/rolling-automation")){
    if(request.method()==="POST"){assert.equal(readOnly,false);assert.equal(request.headers()["x-staff-csrf"],"test-csrf");runs++;}
    const health={status:runs?"COMPLETE":"NEEDS_ATTENTION",overdue:!runs,lastRunFailed:!runs,interrupted:false,missingSlots:runs?0:2,missingAllocations:runs?0:1,expectedAllocations:1,closedDates:0,includedPolicies:1,excludedPolicies:0,drafts:0,readinessPending:1,issues:runs?[]:[{date:"2026-11-07",item:"Samosa",reason:"Daily allocation is missing."}],message:runs?"Expected setup records are present. Stock and readiness checks still apply.":"Review missing coverage and retry."};
    return route.fulfill({headers,json:{plan,policies:[{id:6,name:"Samosa",unit:"PIECE",bookingDays:14,buffer:2,maximum:100,readyStockRequired:false,eligible:true,selectable:true,unavailableReason:null}],lastAttempt:"2026-11-06T00:00:00Z",lastSuccess:runs?"2026-11-06T01:00:00Z":null,message:runs?"Coverage restored":"Generation failed",health,runs:[{id:2,trigger:runs?"MANUAL":"SCHEDULED",actor:runs?"test-manager":"SYSTEM_ROLLING_AUTOMATION",status:runs?"SUCCESS":"FAILED",startedAt:"2026-11-06T00:00:00Z",finishedAt:"2026-11-06T00:00:01Z",durationMs:1000,lockWaitMs:100,lockHeldMs:800,slotsCreated:runs?2:0,allocationsCreated:runs?1:0,summary:runs?"Coverage restored":"Branch must be active and operational."}]}});
   }
   return route.fulfill({headers,json:path.endsWith("/notifications")?{messages:[],unreadCount:0,nextBefore:null}:[]});
  });
  const page=await context.newPage();await page.goto(base+"/admin/rolling-automation");await page.getByText("Saved stock details & run history",{exact:true}).click();await page.getByRole("heading",{name:"Coverage needs attention",exact:true}).waitFor();
  await page.getByText(/Daily generation is overdue/).waitFor();await page.getByText(/The latest run failed/).waitFor();await page.getByText("View missing dates and dishes",{exact:true}).click();await page.getByText("2026-11-07 · Samosa",{exact:true}).waitFor();
  await page.getByText(/Recent automation runs ·/).click();await page.getByText("Branch must be active and operational.",{exact:true}).waitFor();
  assert.equal(await page.getByRole("button",{name:"Retry coverage now",exact:true}).isDisabled(),readOnly);assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false,"health and history fit viewport");
  if(!readOnly){await page.getByRole("spinbutton",{name:"Calendar days to maintain",exact:true}).fill("15");assert.equal(await page.getByRole("button",{name:"Retry coverage now",exact:true}).isDisabled(),true,"unsaved settings cannot be used by retry");await page.getByRole("button",{name:"Reload saved settings",exact:true}).click();await page.getByRole("button",{name:"Retry coverage now",exact:true}).click();await page.getByRole("heading",{name:"Coverage complete",exact:true}).waitFor();assert.equal(runs,1);await page.getByText("Triggered by test-manager",{exact:false}).waitFor();}
  await context.close();console.log(`PASS: coverage alerts, bounded history and ${readOnly?"read-only safety":"manual recovery"} at ${width}px`);
 }
 for(const width of [390,1280]) for(const scenario of ["success","saveFail","runFail","conflict","newSetup","zeroResult"]){
  const context=await browser.newContext({viewport:{width,height:950},serviceWorkers:"block"});
  let plan=scenario==="newSetup"?null:{enabled:true,openingTime:"10:00",closingTime:"21:00",slotMinutes:30,capacity:10,maintainDays:14,weekdays:127,version:0,templates:[],usePolicyMaximum:false};
  const calls=[];let runCount=0;
  const policies=[{id:6,name:"Samosa",unit:"PIECE",bookingDays:14,buffer:2,maximum:100,readyStockRequired:false,eligible:true,selectable:true,unavailableReason:null},{id:7,name:"Rasmalai",unit:"PIECE",bookingDays:7,buffer:1,maximum:40,readyStockRequired:true,eligible:true,selectable:true,unavailableReason:null}];
  function workspace(){return {plan,policies,storefrontBookingDays:scenario==="success"?5:24,lastAttempt:null,lastSuccess:null,message:null,health:{status:runCount?"COMPLETE":"NEEDS_ATTENTION",overdue:false,lastRunFailed:false,interrupted:false,missingSlots:runCount?0:2,missingAllocations:runCount?0:2,expectedAllocations:2,closedDates:0,includedPolicies:scenario==="zeroResult"&&runCount?0:plan?.usePolicyMaximum?2:0,excludedPolicies:0,drafts:0,readinessPending:0,issues:[],message:"Review saved setup"},runs:runCount?[{id:1,trigger:"MANUAL",actor:"owner",status:"SUCCESS",startedAt:new Date().toISOString(),finishedAt:new Date().toISOString(),durationMs:1000,lockWaitMs:0,lockHeldMs:900,slotsCreated:scenario==="zeroResult"?0:28,allocationsCreated:scenario==="zeroResult"?0:22,summary:"Stock prepared"}]:[]};}
  await context.route("**/api/**",async route=>{
   const req=route.request(),url=new URL(req.url()),path=url.pathname,headers={"Access-Control-Allow-Origin":base,"Access-Control-Allow-Credentials":"true","Access-Control-Allow-Headers":"content-type,x-staff-csrf","Access-Control-Allow-Methods":"GET,PUT,POST,OPTIONS","Access-Control-Expose-Headers":"X-Staff-CSRF","X-Staff-CSRF":"test-csrf"};
   if(req.method()==="OPTIONS")return route.fulfill({status:204,headers});
   if(path==="/api/admin/auth/me")return route.fulfill({headers,json:{staffId:77,roleName:"OWNER_ADMIN",username:"owner",fullName:"Owner",branchIds:[1],permissions:["BRANCH_MANAGE","INVENTORY_VIEW","INVENTORY_MANAGE","APPROVAL_MANAGE"]}});
   if(path==="/api/branches")return route.fulfill({headers,json:[{id:1,name:"Test branch",active:true}]});
   if(path.endsWith("/rolling-automation")&&req.method()==="PUT"){
    calls.push("save");assert.equal(req.headers()["x-staff-csrf"],"test-csrf");
    if(scenario==="saveFail")return route.fulfill({status:503,headers,json:{detail:"Saving is unavailable"}});
    const body=req.postDataJSON();assert.equal(body.usePolicyMaximum,true);assert.equal(body.enabled,true);plan={...body,version:plan?plan.version+1:0};
   }
   if(path.endsWith("/rolling-automation/reconcile")){
    calls.push("run");assert.equal(url.searchParams.get("expectedVersion"),String(plan.version),"generate uses exactly the confirmed saved version");
    if(scenario==="conflict")return route.fulfill({status:409,headers,json:{detail:"The saved setup changed. Reload and review it before generating stock."}});
    if(scenario==="runFail"&&calls.filter(call=>call==="run").length===1)return route.fulfill({status:503,headers,json:{detail:"Generator unavailable"}});
    runCount++;
   }
   if(path.includes("/rolling-automation"))return route.fulfill({headers,json:workspace()});
   return route.fulfill({headers,json:path.endsWith("/notifications")?{messages:[],unreadCount:0,nextBefore:null}:[]});
  });
  const page=await context.newPage();await page.goto(base+"/admin/rolling-automation");await page.getByRole("heading",{name:"Automatic slots & inventory",exact:true}).waitFor();
  const source=page.getByRole("combobox",{name:"Inventory quantity source",exact:true});await source.waitFor();
  if(scenario==="newSetup"){assert.equal(await source.inputValue(),"maximum","new plans default to automatic quantities");await page.getByRole("checkbox",{name:"Enable daily setup for this branch",exact:true}).check();}
  else{await page.getByRole("button",{name:"Use policy maximums",exact:true}).click();}
  await page.getByTestId("draft-ready-count").filter({hasText:/^2$/}).waitFor();await page.getByText("You have unsaved changes",{exact:true}).waitFor();
  if(scenario==="success"){
   await page.getByText(/The storefront allows only 5 days ahead/).waitFor();
   await page.getByRole("button",{name:"Review shorter windows",exact:true}).click();
   assert.equal(await page.locator('[data-policy-row]').count(),2,"storefront cap shortens both dishes");
   await page.getByText("Review dishes & quantities",{exact:true}).click();
   await page.screenshot({path:join(evidenceDir,`automation-ux-preview-${width}.png`),fullPage:true});
  }
  const apply=page.getByRole("button",{name:"Save & generate now",exact:true});await apply.click();
  if(scenario==="saveFail"){await page.getByRole("alert").filter({hasText:"Saving is unavailable"}).waitFor();assert.deepEqual(calls,["save"],"failed save never generates");await page.getByText("You have unsaved changes",{exact:true}).waitFor();}
  else if(scenario==="conflict"){await page.getByRole("alert").filter({hasText:"saved setup changed"}).waitFor();assert.deepEqual(calls,["save","run"]);assert.equal(runCount,0,"stale setup never generates");}
  else if(scenario==="runFail"){await page.getByRole("alert").filter({hasText:"Settings saved, but daily stock generation did not finish"}).waitFor();await page.getByText("Your setup is saved",{exact:true}).waitFor();await apply.click();await page.getByRole("status").filter({hasText:"Saved setup checked. Eligible pickup dates have daily stock entries"}).waitFor();assert.deepEqual(calls,["save","run","run"],"retry after a saved setup does not resave");}
  else if(scenario==="zeroResult"){await page.getByRole("alert").filter({hasText:"No daily stock was created"}).waitFor();assert.deepEqual(calls,["save","run"]);}
  else{await page.getByRole("status").filter({hasText:"Saved setup checked. Eligible pickup dates have daily stock entries"}).waitFor();assert.deepEqual(calls,["save","run"]);await page.getByText("Your setup is saved",{exact:true}).waitFor();}
  const feedback=page.getByTestId("action-feedback");
  assert.equal(await feedback.evaluate(element=>{const r=element.getBoundingClientRect();return r.top>=0&&r.bottom<=innerHeight;}),true,"action result stays within the viewport beside generation controls");
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false,"setup workflow fits viewport");
  await context.close();console.log(`PASS: setup-first ${scenario} at ${width}px`);
 }
 console.log("PASS: separate opt-in automation, configured-policy selection, explicit guarantee approval, saved-setting generation, safe pause, removal of unsupported selections, capacity exclusions, authenticated production writes, reload protection/recovery and responsive help text.");
}finally{await browser.close();}
