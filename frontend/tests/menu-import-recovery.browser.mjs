import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const browser=await chromium.launch({headless:true}),base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
const id='12345678-1234-1234-1234-123456789abc',key='gokul-menu-import-job:1';
try {for(const width of [390,1280]) {
 const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:'block'}),page=await context.newPage();
 const errors=[];page.on('pageerror',error=>errors.push(error.message));
 let operation='IMPORT',status='PROCESSING',uploads=0,reads=0,loseAck=false,submissionId=null;
 const validation={valid:true,totalRows:500,errors:[]};
 const imported={success:true,rowsProcessed:500,categoriesCreated:1,categoriesUpdated:0,productsCreated:500,productsUpdated:0,branchProductsCreated:500,branchProductsUpdated:0};
 await context.addInitScript(({key,id})=>{
  if(!sessionStorage.getItem('menu-job-test-initialized')) {
   sessionStorage.setItem('menu-job-test-initialized','true');sessionStorage.setItem(key,JSON.stringify({id,operation:'IMPORT'}));
  }
 },{key,id});
 await context.route('**/api/**',async route=>{
  const request=route.request(),path=new URL(request.url()).pathname;
  const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Headers':'content-type,x-staff-csrf','Access-Control-Allow-Methods':'GET,POST,OPTIONS','Access-Control-Expose-Headers':'X-Staff-CSRF'};
  if(request.method()==='OPTIONS')return route.fulfill({status:204,headers});
  let json=[];
  if(path==='/api/admin/auth/me')json={staffId:77,username:'owner',fullName:'Owner',roleName:'OWNER_ADMIN',branchIds:[1],permissions:['MENU_MANAGE']};
  else if(path==='/api/branches')json=[{id:1,code:'TEST',name:'Test branch',active:true}];
  else if(path.includes(`/jobs/${id}`)){reads++;json={id,status,result:status==='SUCCEEDED'?JSON.stringify(operation==='IMPORT'?imported:validation):null,error:null};}
  else if(submissionId&&path.endsWith(`/submissions/${submissionId}`)){reads++;json={id,status:'SUCCEEDED',result:JSON.stringify(validation),error:null};}
  else if(path.endsWith('/menu/import/validate')) {
   uploads++;operation='VALIDATE';status='SUCCEEDED';
   if(loseAck) {
    submissionId=request.postData()?.match(/name="submissionId"\r\n\r\n([^\r]+)/)?.[1];
    assert.ok(submissionId,'submission ID must be sent before an acknowledgement exists');
    return; // Accepted by the mock backend; the initial HTTP request never resolves.
   }
   return route.fulfill({status:202,json:{id,status:'QUEUED'},headers});
  }
  else if(request.method()==='POST')uploads++;
  else if(path==='/api/storefront/features')json={};
  return route.fulfill({json,headers:{...headers,'X-Staff-CSRF':'test-csrf'}});
 });
 await page.goto(`${base}/admin/menu/import`);
 await page.getByRole('button',{name:'Resume status check',exact:true}).click();
 await page.waitForFunction(()=>document.querySelector('select')?.disabled);
 await page.waitForFunction(()=>document.querySelector('button')!==null);await page.waitForTimeout(2200);
 await page.reload();
 await page.getByRole('button',{name:'Resume status check',exact:true}).waitFor();
 status='SUCCEEDED';await page.getByRole('button',{name:'Resume status check',exact:true}).click();
 await page.getByRole('heading',{name:'Import completed',exact:false}).waitFor();
 assert.equal(uploads,0,'refresh/recovery must not upload again');assert.ok(reads>=1);
 assert.equal(await page.evaluate(key=>sessionStorage.getItem(key),key),null);
 operation='VALIDATE';await page.evaluate(({key,id})=>sessionStorage.setItem(key,JSON.stringify({id,operation:'VALIDATE'})),{key,id});
 await page.reload();await page.getByRole('button',{name:'Resume status check',exact:true}).waitFor();
 await page.locator('input[type=file]').setInputFiles({name:'different-menu.xlsx',mimeType:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',buffer:Buffer.from('synthetic workbook')});
 await page.getByRole('button',{name:'Resume status check',exact:true}).click();
 await page.getByText('Validation passed',{exact:true}).waitFor();
 assert.equal(await page.getByRole('button',{name:'Confirm Import',exact:true}).isDisabled(),true,'recovered validation cannot authorize a different file');
 await page.getByRole('button',{name:'Validate File',exact:true}).click();
 await page.waitForFunction(()=>[...document.querySelectorAll('button')].some(b=>b.textContent?.trim()==='Confirm Import'&&!b.disabled));
 assert.equal(uploads,1);
 await page.clock.install();loseAck=true;
 await page.getByRole('button',{name:'Validate File',exact:true}).click();
 await page.waitForFunction(()=>document.querySelector('select')?.disabled);
 await page.waitForFunction(key=>JSON.parse(sessionStorage.getItem(key)??'null')?.submission===true,key);
 await page.waitForTimeout(100); // Let the route capture the submitted identifier.
 await page.clock.fastForward(60000);
 await page.getByText(/Menu upload timed out after one minute/).waitFor();
 assert.equal(await page.locator('select').isDisabled(),false,'upload timeout releases the busy UI');
 const uploadsBeforeRecovery=uploads;
 await page.getByRole('button',{name:'Resume status check',exact:true}).click();
 await page.clock.fastForward(2100);
 await page.getByText('Validation passed',{exact:true}).waitFor();
 assert.equal(uploads,uploadsBeforeRecovery,'lost acknowledgement recovery must not upload again');
 assert.equal(await page.getByRole('button',{name:'Confirm Import',exact:true}).isDisabled(),true);
 assert.equal(await page.evaluate(key=>sessionStorage.getItem(key),key),null);
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);assert.deepEqual(errors,[]);
 console.log(`Menu import recovery ${width}px passed`);await context.close();
}} finally {await browser.close();}
