import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
const browser=await chromium.launch({headless:true});
try {
 for(const width of [390,1280]) {
  const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:'block'}),page=await context.newPage();
  const errors=[],stationBranches=[];let restricted=false;
  page.on('pageerror',error=>errors.push(error.message));
  await context.route('**/api/**',async route=>{
   const request=route.request(),url=new URL(request.url()),path=url.pathname;
   const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Headers':'content-type,x-staff-csrf','Access-Control-Allow-Methods':'GET,POST,OPTIONS','Access-Control-Expose-Headers':'X-Staff-CSRF','X-Staff-CSRF':'test-csrf'};
   if(request.method()==='OPTIONS')return route.fulfill({status:204,headers});
   let json=[];
   if(path==='/api/admin/auth/me')json={staffId:1,username:'owner',fullName:'Owner',roleName:restricted?'MANAGER':'OWNER_ADMIN',branchIds:[1],permissions:['ORDER_VIEW','BRANCH_MANAGE','ORDER_START_PREPARATION']};
   else if(path==='/api/branches')json=[{id:1,name:'Main shop',active:true},{id:2,name:'Second shop',active:true}];
   else if(path==='/api/admin/printing/station'){stationBranches.push(Number(url.searchParams.get('branchId')));json={configured:false};}
   else if(path==='/api/admin/printing/health')json={station:'KITCHEN',agent:{status:'OFFLINE',heartbeatCount:0},printer:{status:'NOT_CONFIGURED'},queue:{queued:0,claimed:0,failed:0,permanentlyFailed:0},activity:{}};
   else if(path==='/api/admin/print-jobs')json={content:[],totalElements:0,totalPages:0,number:0,size:20};
   else if(path==='/api/admin/print-jobs/counts')json={};
   return route.fulfill({json,headers});
  });
  await page.goto(`${base}/admin/printing`);
  await page.locator('#printing-branch').selectOption('2');
  const setup=page.getByRole('link',{name:'Set up printer',exact:true});await setup.waitFor();
  assert.equal(await setup.evaluate(node=>getComputedStyle(node).color),'rgb(255, 255, 255)');
  await page.getByText(/Open printer setup to configure.*Second shop/).waitFor();
  await setup.click();
  const branch=page.locator('main select').first();await page.waitForFunction(()=>document.querySelector('main select')?.value==='2');
  const installer=page.getByRole('link',{name:'Download Windows installer',exact:true});await installer.waitFor();
  assert.equal(await installer.evaluate(node=>getComputedStyle(node).color),'rgb(255, 255, 255)');
  assert.equal(await installer.getAttribute('download'),'');
  await page.reload();await page.waitForFunction(()=>document.querySelector('main select')?.value==='2');
  await branch.selectOption('1');await page.reload();await page.waitForFunction(()=>document.querySelector('main select')?.value==='1');
  await page.getByRole('link',{name:'Back to printer queue',exact:true}).click();await page.waitForFunction(()=>document.querySelector('#printing-branch')?.value==='1');
  // A stale preference cannot select a branch removed from the staff member's access.
  await page.evaluate(()=>localStorage.setItem('gokul-admin-branch:1','2'));restricted=true;stationBranches.length=0;
  await page.goto(`${base}/admin/printing/setup`);await page.waitForFunction(()=>document.querySelector('main select')?.value==='1');
  await page.waitForFunction(()=>document.body.textContent.includes('Not installed'));
  assert.equal(await branch.locator('option[value="2"]').count(),0);
  assert.ok(stationBranches.every(id=>id===1));
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
  assert.deepEqual(errors,[]);await context.close();console.log(`Printer setup guidance, contrast, branch reload and access checks passed at ${width}px`);
 }
} finally {await browser.close();}
