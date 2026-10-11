import {recoveryContext,recoveryFailure} from './recoveryEvidence.mjs';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const browser=await chromium.launch({headless:true}),base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
try {
 for(const width of [390,1280]) for(const configure of [true,false]) {
  const context=await recoveryContext(browser,{viewport:{width,height:900},serviceWorkers:'block'});
  let alarm={enabled:false,online:true,status:'DISABLED',lastSeenAt:'2026-10-11T06:00:00Z',testSequence:0,testReceipt:0};
  const state=()=>({configured:true,enabled:false,online:true,profile:{branchId:1,station:'KITCHEN',agentId:'shop',printerCode:'KITCHEN_MAIN',protocol:'ESC_POS_USB',target:'Test Queue',port:9100,baudRate:9600,paperWidthMm:80,autoCut:false},runtime:{status:'READY',devices:{usb:[],bluetooth:[]}},alarm});
  await context.route('**/api/**',async route=>{
   const req=route.request(),url=new URL(req.url()),path=url.pathname;
   const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Headers':'content-type,x-staff-csrf','Access-Control-Allow-Methods':'GET,POST,OPTIONS','Access-Control-Expose-Headers':'X-Staff-CSRF','X-Staff-CSRF':'test'};
   if(req.method()==='OPTIONS')return route.fulfill({status:204,headers});
   let json=[];
   if(path==='/api/admin/auth/me')json={staffId:1,username:'staff',fullName:'Staff',roleName:configure?'OWNER_ADMIN':'KITCHEN_STAFF',branchIds:[1],permissions:['ORDER_VIEW',...(configure?['BRANCH_MANAGE']:[])]};
   else if(path==='/api/branches')json=[{id:1,name:'Test shop',code:'TEST',active:true}];
   else if(path==='/api/admin/printing/station')json=state();
   else if(path.endsWith('/alarm-mode')||path.endsWith('/alarm-test')) {
    assert.ok(configure);assert.equal(req.headers()['x-staff-csrf'],'test');
    assert.match(url.searchParams.get('requestId'),/^[0-9a-f-]{36}$/);
    alarm=path.endsWith('/alarm-mode')?{...alarm,enabled:req.postDataJSON().enabled,status:req.postDataJSON().enabled?'IDLE':'DISABLED'}:{...alarm,status:'TESTING',testSequence:1,testReceipt:1};
    json={...state(),actionReceipt:{requestId:url.searchParams.get('requestId'),outcome:'ACCEPTED'}};
   }
   await route.fulfill({json,headers});
  });
  const page=await context.newPage();await page.goto(base+'/admin/printing/setup');
  const enable=page.getByRole('button',{name:'Enable computer alarm',exact:true});await enable.waitFor();
  assert.equal(await enable.isEnabled(),configure);
  const mac=page.getByRole("link",{name:"Download macOS alarm installer",exact:true});
  assert.equal(await mac.getAttribute("href"),"/downloads/gokul-kitchen-alarm-macos.zip");
  if(width===390 && configure) {
   const download=await context.request.get(base+"/downloads/gokul-kitchen-alarm-macos.zip");
   assert.equal(download.status(),200);
   assert.match(download.headers()["content-disposition"],/gokul-kitchen-alarm-macos.zip/);
   assert.ok((await download.body()).includes(Buffer.from("install-macos.command")));
  }
  if(configure) {
   await enable.click();await page.getByText('Alarm enabled',{exact:true}).waitFor();
   await page.getByRole('button',{name:'Test computer sound',exact:true}).click();
   await page.getByText('Playing test sound',{exact:true}).waitFor();
   await page.getByText('The computer accepted the latest sound test. Confirm that you heard it at the shop.',{exact:true}).waitFor();
   await page.reload();await page.getByRole('button',{name:'Disable computer alarm',exact:true}).waitFor();
   alarm={...alarm,online:false};await page.getByText('Alarm agent offline',{exact:true}).waitFor();
   assert.equal(await page.getByRole('button',{name:'Test computer sound',exact:true}).isEnabled(),false);
   await page.getByRole('button',{name:'Disable computer alarm',exact:true}).click();
   await enable.waitFor();
  }
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
  await context.close();console.log(`Native alarm controls, permissions and health pass at ${width}px (configure=${configure})`);
 }
} catch(error) {await recoveryFailure(browser,'native-alarm');throw error;} finally {await browser.close();}
