import assert from "node:assert/strict";
import {createRequire} from "node:module";
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??"playwright");
const browser=await chromium.launch({headless:true});
const context=await browser.newContext({viewport:{width:390,height:844}});
let box=null,branding=null,uploads=0;
const csrf="occasion-csrf";
const branches=[{id:1,name:"First branch",active:true},{id:2,name:"Gifting branch",active:true}];
await context.route("**/api/**",async route=>{
 const request=route.request(),path=new URL(request.url()).pathname;let json=[];
 if(request.method()==="OPTIONS")return route.fulfill({status:204,headers:{"Access-Control-Allow-Origin":base,"Access-Control-Allow-Credentials":"true","Access-Control-Allow-Methods":"GET,POST,PUT,OPTIONS","Access-Control-Allow-Headers":"content-type,x-staff-csrf"}});
 if(path==="/api/admin/auth/me")return route.fulfill({json:{staffId:77,username:"owner",fullName:"Owner",roleName:"OWNER_ADMIN",branchIds:[1,2],permissions:["MENU_MANAGE","ORDER_VIEW"]},headers:{"X-Staff-CSRF":csrf,"Access-Control-Expose-Headers":"X-Staff-CSRF"}});
 if(path==="/api/branches")json=branches;
 else if(path.includes("occasion-catalogue")) {
  if(request.method()!=="GET") {assert.equal(request.headers()["x-staff-csrf"],csrf);assert.ok(path.includes("/branches/2/"));}
  if(path.endsWith("/photos")){uploads++;return route.fulfill({json:{url:`https://images.example.invalid/upload-${uploads}.png`}});}
  if(path.endsWith("/boxes")){box={...request.postDataJSON(),id:8};return route.fulfill({json:box});}
  if(path.endsWith("/branding")){branding=request.postDataJSON();return route.fulfill({status:204});}
  json={sweets:[],boxes:box?[box]:[],branding};
 }
 return route.fulfill({json});
});
const page=await context.newPage(),base=process.env.BROWSER_BASE??"http://127.0.0.1:3311";
page.on("console",message=>{if(message.type()==="error")console.error("ADMIN",message.text());});
try {
 await page.goto(`${base}/admin/occasion-enquiries`);
 await page.getByLabel("Branch",{exact:true}).selectOption("2");
 await page.reload();
 await page.getByLabel("Branch",{exact:true}).waitFor();
 await page.waitForFunction(()=>document.querySelector('select').value==="2");
 await page.getByText("Occasion sweets & packaging catalogue",{exact:true}).click();
 await page.getByLabel("Box name").fill("Celebration collection");
 await page.getByLabel("Dimensions",{exact:false}).fill("18 × 12 × 4 cm");
 await page.getByLabel("Material",{exact:false}).fill("Food-safe card");
 const photo={name:"photo.png",mimeType:"image/png",buffer:Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aWo0AAAAASUVORK5CYII=","base64")};
 await page.getByLabel("Upload packaging photos",{exact:true}).setInputFiles([photo,{...photo,name:"inside.png"}]);
 await page.getByText("Photos uploaded. Save to publish your changes.",{exact:true}).waitFor();
 await page.getByLabel("Publish packaging",{exact:false}).check();
 await page.getByRole("button",{name:"Save packaging",exact:true}).click();
 await page.getByText("Catalogue saved.",{exact:false}).waitFor();
 assert.equal(box.imageUrls.length,2);assert.equal(box.imageUrl,box.imageUrls[0]);assert.equal(box.published,true);
 await page.getByLabel("Campaign headline",{exact:false}).fill("Celebrate with Gokul");
 await page.getByLabel("Campaign description",{exact:false}).fill("A thoughtful gift for every guest");
 await page.getByLabel("Upload campaign photo",{exact:false}).setInputFiles(photo);
 await page.getByText("Photos uploaded. Save to publish your changes.",{exact:true}).waitFor();
 await page.getByLabel("Publish occasion campaign",{exact:false}).check();
 await page.getByRole("button",{name:"Save occasion campaign"}).click();
 await page.getByText("Catalogue saved.",{exact:false}).waitFor();
 assert.equal(branding.published,true);assert.match(branding.imageUrl,/upload-3/);
 assert.equal(await page.getByLabel("Real photo URL",{exact:false}).count(),0);
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
 await page.reload();await page.getByText("Occasion sweets & packaging catalogue",{exact:true}).click();
 await page.getByRole("button",{name:"Edit Celebration collection"}).click();
 await page.getByRole("button",{name:"Remove photo 2"}).click();
 await page.getByRole("button",{name:"Save packaging",exact:true}).click();
 await page.getByText("Catalogue saved.",{exact:false}).waitFor();assert.equal(box.imageUrls.length,1);
 console.log("PASS: per-staff branch retention, multi-photo uploads/preview/removal, CSRF/branch scope, campaign publication and mobile fit.");
}catch(error){console.error("ADMIN BODY",await page.locator("body").innerText());throw error;}finally{await browser.close();}
