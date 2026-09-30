import assert from "node:assert/strict";
import {createRequire} from "node:module";
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??"playwright");
const browser=await chromium.launch({headless:true});
const context=await browser.newContext({viewport:{width:390,height:844}});
const base=process.env.BROWSER_BASE??"http://127.0.0.1:3311";
const id="11111111-1111-4111-8111-111111111111";
const box={id:8,name:"400 ml plastic box",dimensions:"400 ml",material:"Food-safe plastic",compartments:1,capacityPieces:3,price:10,branding:"",leadDays:1,published:true};
let enquiry={id,branchId:1,occasionType:"Family gathering",serviceDate:"2026-10-10",guestCount:600,fulfilment:"PICKUP",status:"REQUESTED",customerPhone:"+919876543210",paidAmount:0,quoteTerms:null,pricedLines:[],items:["Kaju katli","Mathari","Chena roll"].map((productName,index)=>({productId:index+1,productName,quantity:600,unit:"PIECE",productionUnit:"GRAM",suggestedProductionQuantity:null})),gift:{box,boxCount:600,recipe:[{productId:1,pieces:1},{productId:2,pieces:1},{productId:3,pieces:1}],packagingEstimate:6000,approvedPackagingTotal:null,includeSpoons:true},nextStep:"Under branch review",productionPlan:[]};
let previewBody=null,depositBody=null,packedBody=null,acknowledged=0;
const calculation={lines:enquiry.items.map((item,index)=>({productId:item.productId,name:item.productName,unit:"GRAM",unitPrice:300,pieceGrams:null,productionQuantity:[10000,15000,20000][index],cgstRate:2.5,sgstRate:2.5,foodBase:[3000,4500,6000][index],foodTax:[150,225,300][index],packagingAmount:2200,grossAmount:[5350,6925,8500][index]})),foodBase:13500,foodTax:675,packagingTotal:6600,total:20775,deposit:10387.5,balance:10387.5,expiresAt:"2026-10-01T12:00:00Z",balanceDueAt:"2026-10-10T18:29:59Z",expectedReadyAt:"2026-10-10T10:00:00",estimated:true,extras:[{name:"Plastic spoons",quantity:600,priceIncludingTax:1}]};
await context.route("**/api/**",async route=>{
 const request=route.request(),path=new URL(request.url()).pathname;let json=[];
 if(request.method()==="OPTIONS")return route.fulfill({status:204,headers:{"Access-Control-Allow-Origin":base,"Access-Control-Allow-Credentials":"true","Access-Control-Allow-Methods":"GET,POST,PUT,OPTIONS","Access-Control-Allow-Headers":"content-type,x-staff-csrf"}});
 if(path==="/api/admin/auth/me")return route.fulfill({json:{staffId:77,username:"manager",fullName:"Manager",roleName:"OWNER_ADMIN",branchIds:[1],permissions:["ORDER_VIEW","APPROVAL_MANAGE"]},headers:{"X-Staff-CSRF":"test-csrf","Access-Control-Expose-Headers":"X-Staff-CSRF"}});
 if(path==="/api/branches")json=[{id:1,name:"Celebration branch",active:true}];
 else if(path==="/api/storefront/features")json={occasionEnquiries:true,occasionPayments:true,notificationInbox:true,futuristicStorefrontV2:true,today:"2026-09-30"};
 else if(path==="/api/storefront/customer-identity")json={enabled:true};
 else if(path==="/api/customer/identity/me")json={authenticated:true,name:"Customer",phone:"+919876543210"};
 else if(path.endsWith("/occasion-catalogue"))json={sweets:[],boxes:[],branding:null};
 else if(path.endsWith("/dashboard/today"))json={today:"2026-09-30",orders:7,preparing:2,ready:3,completed:2,updatedAt:"2026-09-30T12:00:00Z"};
 else if(path==="/api/occasion-enquiries"||path.endsWith("/branches/1/occasion-enquiries"))json=[enquiry];
 else if(path.endsWith("/quote-preview")){previewBody=request.postDataJSON();json=calculation;}
 else if(path.endsWith("/quote-from-rates")){assert.equal(request.postDataJSON().expectedTotal,20775);enquiry={...enquiry,status:"QUOTED",estimated:true,originalEstimate:20775,quotedAmount:20775,depositAmount:10387.5,calculation,extraCharges:calculation.extras,packingRevision:0,quoteTerms:"Actual packed weights at agreed rates",nextStep:"Review your estimate",productionPlan:[{productId:1,quantity:10000,unit:"GRAM",expectedReadyAt:"2026-10-10T10:00:00",state:"PLANNED",readyQuantity:0,readinessRevision:0}]};json=enquiry;}
 else if(path.endsWith("/pickup-slots"))json=[{id:4,active:true,remainingCapacity:5,startTime:"09:00:00",endTime:"09:30:00"},{id:5,active:true,remainingCapacity:5,startTime:"11:00:00",endTime:"11:30:00"}];
 else if(path.endsWith("/deposit")){depositBody=request.postDataJSON();json={status:"PENDING",attemptId:"test-payment",paymentUrl:null};}
 else if(path.endsWith("/finalize-packing")){packedBody=request.postDataJSON();enquiry={...enquiry,packingFinalizedAt:"2026-10-10T06:00:00Z",packingRevision:1,quotedAmount:21090,balancePaymentOpen:true};json=enquiry;}
 else if(path.endsWith("/notifications/read-target")){acknowledged++;return route.fulfill({status:204});}
 else if(path.endsWith("/notifications"))json={messages:[],unreadCount:0,nextBefore:null};
 return route.fulfill({json});
});
const page=await context.newPage();
try {
 await page.goto(`${base}/admin`);
 await page.getByText("Orders due today",{exact:true}).waitFor();
 const order=await page.evaluate(()=>[...document.querySelectorAll("h2")].map(x=>x.textContent));
 assert.ok(order.indexOf("Today at a glance")<order.indexOf("Quick access"));
 await page.goto(`${base}/admin/occasion-enquiries`);
 await page.getByRole("checkbox",{name:/Final weight measured/}).check();
 for(const [index,value]of ["10","15","20"].entries())await page.getByLabel("Estimated total kg",{exact:false}).nth(index).fill(value);
 await page.getByLabel("₹ each including tax").fill("1");
 await page.getByRole("checkbox",{name:/I checked actual sweet sizes/}).check();
 await page.getByLabel("Customer-visible terms or decline reason").fill("Actual packed weights at agreed rates");
 assert.equal(await page.locator('input[type="datetime-local"]').count(),0);
 await page.getByRole("button",{name:"Calculate and review quote"}).click();
 await page.getByRole("heading",{name:"Review before sending"}).waitFor();
 assert.deepEqual(previewBody.rates.map(x=>x.estimatedKg),[10,15,20]);assert.equal(previewBody.extras[0].quantity,600);assert.equal(previewBody.extras[0].priceIncludingTax,1);
 await page.getByRole("button",{name:"Send reviewed quote"}).click();
 await page.getByText("Request updated.",{exact:false}).waitFor();
 await page.goto(`${base}/occasions/requests?enquiry=${id}`);
 await page.getByText("Booking estimate — final weight after packing",{exact:true}).waitFor();
 await page.getByRole("button",{name:"Choose a live pickup time"}).click();
 assert.equal(await page.getByRole("button",{name:"09:00–09:30"}).count(),0);
 await page.getByRole("button",{name:"11:00–11:30"}).click();
 const deposit=page.getByRole("button",{name:/Pay advance/});assert.equal(await deposit.isDisabled(),true);
 await page.getByRole("checkbox",{name:/I understand this is an estimate/}).check();
 await deposit.click();await page.getByText("Checkout is being prepared.",{exact:false}).waitFor();
 assert.equal(depositBody.estimateAccepted,true);assert.equal(depositBody.pickupSlotId,5);assert.ok(acknowledged>0);
 enquiry={...enquiry,status:"PAID",paidAmount:10387.5,balancePaymentOpen:false};
 await page.reload();await page.getByText("Advance received. The branch will finalize",{exact:false}).waitFor();
 assert.equal(await page.getByRole("button",{name:/Pay balance/}).count(),0);
 await page.goto(`${base}/admin/occasion-enquiries`);
 await page.getByRole("heading",{name:"Weigh packed food & finalize invoice"}).waitFor();
 for(const [index,value]of ["10.2","15.3","20.5"].entries())await page.getByLabel(/actual kg/).nth(index).fill(value);
 await page.getByRole("checkbox",{name:/All requested piece counts/}).check();await page.getByRole("checkbox",{name:/I reviewed the measured weights/}).check();
 await page.getByRole("button",{name:"Finalize packed invoice"}).click();await page.getByText("Final packed invoice saved.",{exact:false}).waitFor();
 assert.deepEqual(packedBody.lines.map(x=>x.quantity),[10200,15300,20500]);assert.equal(packedBody.requestedPiecesPacked,true);
 await page.goto(`${base}/occasions/requests?enquiry=${id}`);await page.getByText("Packing complete — final measured invoice",{exact:true}).waitFor();await page.getByRole("button",{name:/Pay balance/}).waitFor();
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
 if(process.env.ALERT_SCREENSHOT_DIR)await page.screenshot({path:`${process.env.ALERT_SCREENSHOT_DIR}/final-invoice-mobile.png`,fullPage:true});
 console.log("PASS: live dashboard above quick links, rate-based estimate, 600 boxes/spoons, immutable requested date, estimate consent, ready-time slot filtering, packing gate, measured final invoice and notification acknowledgement.");
}catch(error){console.error(await page.locator("body").innerText());throw error;}finally{await browser.close();}
