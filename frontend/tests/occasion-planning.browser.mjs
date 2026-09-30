import assert from "node:assert/strict";import {createRequire} from "node:module";
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??"playwright"),browser=await chromium.launch({headless:true}),context=await browser.newContext({viewport:{width:1440,height:1000}});
const base=process.env.BROWSER_BASE??"http://127.0.0.1:3311";let refreshes=0,approved=false,changed=false;
const ids=["11111111-1111-4111-8111-111111111111","22222222-2222-4222-8222-222222222222"];
const product=(productId,name,grams)=>({productId,name,requestedPieces:0,requestedGrams:0,committedPieces:0,committedGrams:grams,approvedPieces:0,approvedGrams:approved&&productId===1?grams:0,readyPieces:0,readyGrams:0,approvalToken:approved?null:`token-${productId}`});
let calendarCount=3,failCalendar=false;
const enquiries=ids.map((id,index)=>({id,branchId:1,occasionType:index?"Wedding gifts":"Family celebration",serviceDate:`2026-10-0${index+1}`,guestCount:100,fulfilment:"PICKUP",status:"PAID",customerPhone:"+919876543210",paidAmount:1000,quotedAmount:2000,depositAmount:1000,items:[{productId:1,productName:"Gulab Jamun",quantity:100000,unit:"GRAM"}],pricedLines:[],nextStep:"Plan dedicated production",productionPlan:[],packingGroups:[]}));
enquiries.push({...enquiries[0],id:"33333333-3333-4333-8333-333333333333",serviceDate:"2026-11-15",occasionType:"November celebration"});
await context.route("**/api/**",async route=>{const req=route.request(),url=new URL(req.url()),path=url.pathname;let json=[];
if(req.method()==="OPTIONS")return route.fulfill({status:204,headers:{"Access-Control-Allow-Origin":base,"Access-Control-Allow-Credentials":"true","Access-Control-Allow-Methods":"GET,POST,PUT,OPTIONS","Access-Control-Allow-Headers":"content-type,x-staff-csrf"}});
if(path==="/api/admin/auth/me")return route.fulfill({json:{staffId:77,username:"manager",fullName:"Manager",roleName:"OWNER_ADMIN",branchIds:[1],permissions:["ORDER_VIEW","APPROVAL_MANAGE"]},headers:{"X-Staff-CSRF":"test-csrf","Access-Control-Expose-Headers":"X-Staff-CSRF"}});
if(path==="/api/branches")json=[{id:1,name:"Planning branch",active:true}];
else if(path.endsWith("/occasion-catalogue"))json={sweets:[],boxes:[],branding:null};
else if(path.endsWith("/occasion-enquiries/calendar")){
 if(failCalendar)return route.fulfill({status:503,json:{}});
 const month=url.searchParams.get("month"),length=new Date(`${month}-01T12:00:00Z`);length.setUTCMonth(length.getUTCMonth()+1);length.setUTCDate(0);
 json={month,days:Array.from({length:length.getUTCDate()},(_,n)=>({date:`${month}-${String(n+1).padStart(2,"0")}`,orderCount:month==="2026-11"&&n===14?calendarCount:0,needsReview:month==="2026-11"&&n===14?1:0,committedOrders:month==="2026-11"&&n===14?2:0}))};
}
else if(path.endsWith("/occasion-enquiries/planning")){refreshes++;const from=url.searchParams.get("from")||"2026-10-01";json={today:"2026-10-01",days:Array.from({length:7},(_,n)=>{const d=new Date(`${from}T12:00:00Z`);d.setUTCDate(d.getUTCDate()+n);const date=d.toISOString().slice(0,10);return {date,orderCount:date==="2026-11-15"?calendarCount:n<2?changed?2:1:0,needsReview:0,committedOrders:n<2?1:0,products:n===0?[product(1,"Gulab Jamun",changed?110000:100000),product(2,"Paneer",20000)]:n===1?[product(2,"Paneer",200000),product(3,"Kaju Barfi",10000)]:[]};})};}
else if(path.endsWith("/approve")){assert.equal(req.headers()["x-staff-csrf"],"test-csrf");assert.equal(req.postDataJSON().token,"token-1");approved=true;json={updatedCount:1};}
else if(path.endsWith("/occasion-enquiries"))json=enquiries.filter(e=>e.serviceDate===url.searchParams.get("serviceDate"));
else if(path.includes("/occasion-enquiries/"))json=enquiries.find(e=>path.endsWith(e.id));
await route.fulfill({json});});
const page=await context.newPage();page.on("pageerror",error=>console.error("PLANNING PAGE ERROR",error.stack));try{await page.clock.install({time:new Date("2026-10-01T05:00:00Z")});await page.goto(`${base}/admin/occasion-enquiries`);await page.getByRole("heading",{name:"Plan the kitchen by date"}).waitFor();
assert.equal(await page.locator("details[id^=occasion-admin][open]").count(),0);
await page.getByRole("button",{name:/Fri, 2 Oct/}).click();await page.getByRole("heading",{name:/2 October.*2026 · product totals/}).waitFor();await page.locator("details[id^=occasion-admin] > summary").filter({hasText:"Wedding gifts"}).waitFor();assert.equal(await page.locator("details[id^=occasion-admin] > summary").filter({hasText:"Family celebration"}).count(),0);
await page.getByRole("button",{name:/Thu, 1 Oct/}).click();await page.getByRole("heading",{name:/1 October.*2026 · product totals/}).waitFor();
const row=page.getByRole("heading",{name:"Gulab Jamun",exact:true}).locator("..");await row.getByRole("button",{name:"Approve pending paid production"}).click();await page.getByText("booking allocations approved",{exact:false}).waitFor();await page.waitForFunction(()=>document.body.textContent.includes("updated"));
changed=true;const previous=refreshes;await page.clock.runFor(31000);await page.waitForFunction(()=>document.body.textContent.includes("110 kg"));assert.ok(refreshes>previous);
if(process.env.ALERT_SCREENSHOT_DIR)await page.screenshot({path:`${process.env.ALERT_SCREENSHOT_DIR}/admin-planning-desktop.png`,fullPage:true});
await page.setViewportSize({width:390,height:844});assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);if(process.env.ALERT_SCREENSHOT_DIR)await page.screenshot({path:`${process.env.ALERT_SCREENSHOT_DIR}/admin-planning-mobile.png`,fullPage:true});
await page.getByRole("button",{name:"Open calendar",exact:true}).click();
await page.getByRole("combobox",{name:"Month",exact:true}).selectOption("11");
const calendar=page.getByRole("region",{name:"Booking calendar"});
await calendar.getByRole("button",{name:/15 November.*3 orders/}).waitFor();
assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
calendarCount=4;await page.clock.runFor(31000);await calendar.getByRole("button",{name:/15 November.*4 orders/}).waitFor();
await calendar.getByRole("button",{name:/15 November.*4 orders/}).click();
await page.getByRole("heading",{name:/15 November.*2026 · product totals/}).waitFor();
await page.locator("details[id^=occasion-admin] > summary").filter({hasText:"November celebration"}).click();
await page.getByRole("heading",{name:/November celebration ·/}).waitFor();
assert.equal(await calendar.count(),0);
await page.getByRole("button",{name:"Open calendar",exact:true}).click();
failCalendar=true;await page.getByRole("button",{name:"Next calendar month",exact:true}).click();
await page.getByRole("alert").filter({hasText:"Could not refresh calendar counts"}).waitFor();
failCalendar=false;await page.getByRole("button",{name:"Retry",exact:true}).click();
await calendar.getByRole("button",{name:/\b1 December.*0 orders/}).waitFor();
await calendar.press("Escape");assert.equal(await calendar.count(),0);
await page.goto(`${base}/admin/occasion-enquiries?branch=1&enquiry=${ids[1]}`);await page.locator(`#occasion-admin-${ids[1]}[open]`).waitFor();await page.getByRole("heading",{name:/2 October.*2026 · product totals/}).waitFor();
console.log("PASS: date strip, segregated cards, daily product totals, dedicated approval, 30-second refresh, notification deep link, monthly counts, future-date order details, count refresh, retry and mobile fit.");}catch(error){console.error(await page.locator("body").innerText());throw error;}finally{await browser.close();}
