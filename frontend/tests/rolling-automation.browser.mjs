import assert from "node:assert/strict";
import {createRequire} from "node:module";
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??"playwright");
const browser=await chromium.launch({headless:true,...(process.env.BROWSER_EXECUTABLE?{executablePath:process.env.BROWSER_EXECUTABLE,args:["--no-sandbox"]}:{})});
const base=process.env.BROWSER_BASE??"http://127.0.0.1:3311";
try{
 for(const width of [390,1280]){
  const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:"block"});
  let plan=null,saved=null,runs=0,delayReload=false,releaseReload=null,reloadEntered=null,failReload=false;
  const policies=[{id:6,name:"Samosa",unit:"PIECE",bookingDays:14,buffer:2,maximum:100,readyStockRequired:false,eligible:true,selectable:true,unavailableReason:null},
   {id:7,name:"Rasmalai",unit:"PIECE",bookingDays:3,buffer:1,maximum:40,readyStockRequired:true,eligible:true,selectable:true,unavailableReason:null},
   {id:8,name:"Counter capacity",unit:"CAPACITY_POINT",bookingDays:14,buffer:0,maximum:100,readyStockRequired:false,eligible:false,selectable:false,unavailableReason:"Capacity-only policies do not support physical inventory automation."}];
  await context.route("**/api/**",async route=>{
   const request=route.request(),path=new URL(request.url()).pathname;
   const headers={"Access-Control-Allow-Origin":base,"Access-Control-Allow-Credentials":"true",
    "Access-Control-Allow-Headers":"content-type,x-staff-csrf","Access-Control-Allow-Methods":"GET,POST,PUT,OPTIONS",
    "Access-Control-Expose-Headers":"X-Staff-CSRF","X-Staff-CSRF":"test-csrf"};
   if(request.method()==="OPTIONS")return route.fulfill({status:204,headers});
   if(path==="/api/admin/auth/me")return route.fulfill({headers,json:{staffId:77,roleName:"OWNER_ADMIN",username:"owner",fullName:"Owner",branchIds:[1],permissions:["BRANCH_MANAGE","INVENTORY_VIEW","INVENTORY_MANAGE","APPROVAL_MANAGE"]}});
   if(path==="/api/branches")return route.fulfill({headers,json:[{id:1,name:"Test branch",active:true}]});
   if(path.endsWith("/rolling-automation")&&request.method()==="PUT"){
    assert.equal(request.headers()["x-staff-csrf"],"test-csrf","writes carry the exposed CSRF token");
    saved=request.postDataJSON();plan={...saved,version:plan?plan.version+1:0};
   }
   if(path.endsWith("/rolling-automation/reconcile"))runs++;
   if(path.includes("/rolling-automation")){
    if(request.method()==="GET"&&failReload){failReload=false;return route.fulfill({status:503,headers,json:{message:"Temporary failure"}});}
    const json=JSON.parse(JSON.stringify({plan,policies,lastAttempt:runs?"2026-11-05T01:15:00Z":null,lastSuccess:runs?"2026-11-05T01:15:00Z":null,message:runs?"2 slots created; 1 allocations created":null}));
    if(request.method()==="GET"&&delayReload){delayReload=false;await new Promise(resolve=>{releaseReload=resolve;reloadEntered?.();});}
    return route.fulfill({headers,json});
   }
   return route.fulfill({headers,json:path.endsWith("/notifications")?{messages:[],unreadCount:0,nextBefore:null}:[]});
  });
  const page=await context.newPage();
  await page.goto(base+"/admin/rolling-automation");
  await page.getByRole("heading",{name:"Automatic slots & inventory",exact:true}).waitFor();
  const enabled=page.getByRole("checkbox",{name:"Enable rolling automation for this branch",exact:true});
  await enabled.waitFor();
  assert.equal(await enabled.isChecked?.()??await enabled.evaluate(e=>e.checked),false);
  const run=page.getByRole("button",{name:"Generate missing coverage now",exact:true});
  assert.equal(await run.isDisabled(),true);
  assert.equal(await page.getByRole("checkbox",{name:"Counter capacity",exact:true}).isDisabled(),true);
  await page.getByText("Capacity-only policies do not support physical inventory automation.",{exact:true}).waitFor();
  await enabled.check();
  await page.getByRole("checkbox",{name:"Samosa",exact:true}).check();
  await page.getByLabel(/Guaranteed daily quantity/).fill("80");
  await page.getByLabel("Approval mode",{exact:false}).selectOption("approved");
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
  await page.getByLabel(/Guaranteed daily quantity/).fill("81");
  await save.click();
  await page.getByText("Schedule saved.",{exact:false}).waitFor();
  assert.equal(saved.templates[0].quantity,81,"new edits after reload remain authoritative");
  failReload=true;
  await reload.click();
  await page.getByRole("alert").filter({hasText:"Could not load this branch"}).waitFor();
  await page.getByText("Loading saved settings…",{exact:true}).waitFor({state:"hidden"});
  assert.equal(await reload.isDisabled(),false,"a failed reload can be retried");
  assert.equal(await page.getByLabel(/Guaranteed daily quantity/).inputValue(),"81");
  await reload.click();
  await page.getByRole("alert").filter({hasText:"Could not load this branch"}).waitFor({state:"hidden"});
  await page.getByText("Loading saved settings…",{exact:true}).waitFor({state:"hidden"});
  await run.click();
  await page.getByText("2 slots created; 1 allocations created",{exact:true}).waitFor();
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
  await page.getByText("Schedule saved.",{exact:false}).waitFor();
  assert.equal(saved.enabled,false);
  assert.equal(await run.isDisabled(),true);
  await page.getByRole("checkbox",{name:"Samosa",exact:true}).uncheck();
  await enabled.check();
  await page.getByRole("checkbox",{name:"Rasmalai",exact:true}).check();
  await page.getByLabel(/Guaranteed daily quantity/).fill("30");
  await page.getByRole("button",{name:"Save automation settings",exact:true}).click();
  await page.getByText("Schedule saved.",{exact:false}).waitFor();
  assert.deepEqual(saved.templates,[{policyId:7,quantity:30,autoApprove:true}]);
  policies[1]={...policies[1],maximum:5};
  await page.getByRole("button",{name:"Reload saved settings",exact:true}).click();
  await page.getByText(/Maximum: 5 ·/).waitFor();
  await enabled.uncheck();
  await page.getByRole("button",{name:"Save automation settings",exact:true}).click();
  await page.getByText("Schedule saved.",{exact:false}).waitFor();
  assert.equal(saved.enabled,false,"pause succeeds even after a policy limit becomes stricter");
  assert.equal(saved.templates[0].quantity,30);
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false,"automation form fits viewport");
  await context.close();
 }
 console.log("PASS: separate opt-in automation, configured-policy selection, explicit guarantee approval, saved-setting generation, safe pause, removal of unsupported selections, capacity exclusions, authenticated production writes, reload protection/recovery and responsive help text.");
}finally{await browser.close();}
