import assert from "node:assert/strict";
import {createRequire} from "node:module";
import {mkdirSync} from "node:fs";
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??"playwright");
const browser=await chromium.launch({headless:true,executablePath:process.env.CHROMIUM_PATH});
const base=process.env.BROWSER_BASE??"http://127.0.0.1:3311";
const shots=process.env.SCREENSHOT_DIR??"/tmp/gokul-layouts";mkdirSync(shots,{recursive:true});
try{
 for(const width of [320,390,1280]){
  const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:"block"});
  const page=await context.newPage();page.setDefaultTimeout(15000);const errors=[];page.on("pageerror",e=>errors.push(e.message));
  const items=[1,2,3].map(id=>({productId:id,branchProductId:id,branchVersion:0,policyVersion:null,allocationVersion:null,code:`SKU-${id}`,name:["Small samosa","Large samosa","Kaju Katli"][id-1],categoryName:"Fresh items",saleMode:id===3?"WEIGHT":"UNIT",available:false,allocation:null}));
  let job=null,polls=0,failOne=false;const bodies=[];let loseAcknowledgement=true;
  await context.route("**/api/**",async route=>{
   const req=route.request(),path=new URL(req.url()).pathname;
   const headers={"Access-Control-Allow-Origin":base,"Access-Control-Allow-Credentials":"true","Access-Control-Allow-Headers":"content-type,x-staff-csrf","Access-Control-Allow-Methods":"GET,POST,OPTIONS","Access-Control-Expose-Headers":"X-Staff-CSRF","X-Staff-CSRF":"test-csrf"};
   if(req.method()==="OPTIONS")return route.fulfill({status:204,headers});
   let json=[];
   if(path==="/api/admin/auth/me")json={staffId:77,username:"manager",fullName:"Manager",roleName:"OWNER_ADMIN",branchIds:[1],permissions:["MENU_MANAGE","INVENTORY_VIEW","INVENTORY_MANAGE"]};
   else if(path==="/api/branches")json=[{id:1,name:"Tamkuhi Road",active:true}];
   else if(path.endsWith("/portion-groups"))json={version:0,groups:[{key:"samosa",title:"Samosa portions",choices:[{productId:1,label:"Small"},{productId:2,label:"Large"}]}]};
   else if(path.endsWith("/workspace"))json={content:items,totalElements:3,totalPages:1,page:0};
   else if(path.endsWith("/centre/jobs")&&req.method()==="POST"){
    const body=req.postDataJSON();bodies.push(body);failOne=bodies.length===3;job={id:body.submissionId,total:body.items.length,succeeded:0,failed:0};polls=0;
    if(loseAcknowledgement){loseAcknowledgement=false;return route.fulfill({status:503,headers,json:{message:"Acknowledgement lost. Retry this plan."}});}
    json=job;
   }else if(path.endsWith("/centre/jobs"))json=job?[job]:[];
   else if(path.endsWith("/results"))json=items.slice(0,job.total).map(item=>({product_id:item.productId,name:item.name,status:job.succeeded?(job.failed&&item.productId===2?"FAILED":"SUCCEEDED"):"QUEUED",error:job.failed&&item.productId===2?"Policy changed. Reload.":null}));
   else if(path.includes("/centre/jobs/")){polls++;if(polls>=3)job={...job,succeeded:job.total-(failOne?1:0),failed:failOne?1:0};json=job;}
   await route.fulfill({headers,json});
  });
  await page.goto(`${base}/admin/inventory/centre`);
  await page.getByRole("button",{name:"Load branch items",exact:true}).click();
  await page.getByText("Kaju Katli",{exact:true}).waitFor();
  await page.getByRole("button",{name:"Select all matching",exact:true}).click();
  for(const name of ["Small samosa","Large samosa"]){await page.getByLabel(`${name} allocation`,{exact:true}).fill("30");await page.getByLabel(`${name} ready`,{exact:true}).fill("25");}
  await page.getByLabel("Kaju Katli allocation",{exact:true}).fill("1.25");await page.getByLabel("Kaju Katli ready",{exact:true}).fill("0.8");
  await page.waitForFunction(()=>JSON.parse(localStorage.getItem("inventory-centre:77")||"{}").rows?.[2]?.ready==="0.8");
  await page.reload();assert.equal(await page.getByLabel("Kaju Katli allocation",{exact:true}).inputValue(),"1.25");assert.equal(await page.getByLabel("Kaju Katli ready",{exact:true}).inputValue(),"0.8");
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
  await page.screenshot({path:`${shots}/inventory-centre-${width}.png`,fullPage:true});
  await page.getByLabel("I reviewed quantities, service hours and any physical-ready confirmations.").check();
  const run=page.getByRole("button",{name:"Apply configuration, allocation and readiness",exact:true});await run.click();await page.getByRole("alert").filter({hasText:"Acknowledgement lost"}).waitFor();await run.click();
  await page.getByRole("heading",{name:"Backend job progress"}).waitFor();assert.equal(bodies.length,2);assert.deepEqual(bodies[0],bodies[1]);assert.equal(bodies[1].options.openPurchases,false);assert.equal(bodies[1].items[2].quantity,1250);assert.equal(bodies[1].items[2].readyQuantity,800);
  await page.reload();await page.getByRole("heading",{name:"Backend job progress"}).waitFor();await page.getByText("3 succeeded · 0 failed · 0 remaining",{exact:true}).waitFor();assert.equal(bodies.length,2);
  await page.getByRole("button",{name:"Start a fresh plan"}).click();await page.getByRole("button",{name:"Load branch items",exact:true}).click();await page.getByText("Kaju Katli",{exact:true}).waitFor();
  await page.getByLabel("Apply inventory quantities, configuration and readiness",{exact:true}).uncheck();await page.getByLabel("Apply common service hours to selected items",{exact:true}).check();await page.getByLabel("Enable service-hour enforcement for the whole branch",{exact:true}).check();
  await page.getByText("Select an existing portion group",{exact:true}).click();await page.getByRole("button",{name:"Samosa portions · 2 variants",exact:true}).click();
  await page.getByLabel("I reviewed quantities, service hours and any physical-ready confirmations.").check();await page.getByRole("button",{name:"Apply service hours",exact:true}).click();await page.getByRole("heading",{name:"Backend job progress"}).waitFor();
  assert.equal(bodies.length,3);assert.equal(bodies[2].options.applyInventory,false);assert.equal(bodies[2].options.applyHours,true);assert.equal(bodies[2].items.length,2);assert.ok(bodies[2].items.every(i=>i.quantity===null&&i.readyQuantity===null));await page.getByText("1 succeeded · 1 failed · 0 remaining",{exact:true}).waitFor();await page.getByRole("button",{name:"Reload failed items only",exact:true}).click();await page.getByRole("button",{name:"Apply service hours",exact:true}).waitFor();
  assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem("inventory-centre:77")).rows.filter(r=>r.selected).map(r=>r.item.productId)),[2]);
  await page.getByLabel("I reviewed quantities, service hours and any physical-ready confirmations.").check();await page.getByRole("button",{name:"Apply service hours",exact:true}).click();await page.getByRole("heading",{name:"Backend job progress"}).waitFor();assert.equal(bodies.length,4);assert.equal(bodies[3].items.length,1);assert.equal(bodies[3].items[0].productId,2);assert.deepEqual(errors,[]);
  await context.close();
 }
 console.log("Inventory centre: drafts, recovery, idempotent retries, stock units, grouping and hours-only plans passed at 320/390/1280px.");
}finally{await browser.close();}
