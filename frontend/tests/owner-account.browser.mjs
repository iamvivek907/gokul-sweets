import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const browser=await chromium.launch({headless:true}),base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
const setupKey='S'.repeat(43),recoveryKey='R'.repeat(43),replacement='N'.repeat(43),password='Owner-password-2026';
const owner={staffId:7,username:'owner',fullName:'Owner',roleName:'OWNER_ADMIN',branchIds:[1],permissions:['STAFF_MANAGE']};
function headers(){return {'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Headers':'content-type,x-staff-csrf','Access-Control-Allow-Methods':'GET,POST,OPTIONS','Access-Control-Expose-Headers':'X-Staff-CSRF','X-Staff-CSRF':'test-csrf'};}
async function mock(context,handle,profile=null){
 await context.route('**/api/**',async route=>{
  const request=route.request(),path=new URL(request.url()).pathname;
  if(request.method()==='OPTIONS')return route.fulfill({status:204,headers:headers()});
  if(path==='/api/admin/auth/me'){const current=typeof profile==='function'?profile():profile;return route.fulfill({status:current?200:401,json:current??{},headers:headers()});}
  if(await handle(route,path,request))return;
  return route.fulfill({json:[],headers:headers()});
 });
}
async function fillPublic(page,mode){
 await page.getByLabel(mode==='setup'?'Setup key':'Saved recovery key',{exact:true}).fill(mode==='setup'?setupKey:recoveryKey);
 if(mode==='setup')await page.getByLabel('Owner name',{exact:true}).fill('First Owner');
 await page.getByLabel(mode==='setup'?'Username':'New username',{exact:true}).fill('first.owner');
 await page.getByLabel('New password',{exact:true}).fill(password);
 await page.getByLabel('Confirm new password',{exact:true}).fill(password);
}
async function noPersistedSecret(page,key){
 const persisted=await page.evaluate(()=>JSON.stringify({local:{...localStorage},session:{...sessionStorage},url:location.href}));
 assert.equal(persisted.includes(key),false,'secrets must stay out of browser storage and URLs');
}
try {
 for(const width of [390,1280]){
  for(const mode of ['setup','recover']){
   const context=await browser.newContext({viewport:{width,height:950},serviceWorkers:'block'}),page=await context.newPage();
   let calls=0,fail=true,profileLoads=0;
   // Even when a staff cookie exists, public credential recovery must not send it.
   await context.addCookies([{name:'gokul_staff',value:'existing-cookie',domain:'api-ci.example.invalid',path:'/',secure:true,httpOnly:true}]);
   await mock(context,async(route,path,request)=>{
    if(path==='/api/admin/auth/owner-setup'&&request.method()==='GET'){await route.fulfill({json:{available:true},headers:headers()});return true;}
    if(path===`/api/admin/auth/owner-${mode==='setup'?'setup':'recovery'}`&&request.method()==='POST'){
     calls++;const input=request.postDataJSON();assert.equal(input.username,'first.owner');assert.equal(input.password,password);
     assert.equal(input[mode==='setup'?'setupKey':'recoveryKey'],mode==='setup'?setupKey:recoveryKey);
     assert.equal(request.headers().cookie,undefined);assert.equal(request.headers().authorization,undefined);
     await route.fulfill({status:fail?403:200,json:fail?{message:'Account verification failed.'}:{username:'first.owner',recoveryKey:replacement},headers:headers()});return true;
    }
    return false;
   },()=>{profileLoads++;return mode==='recover'&&calls<2?owner:null;});
   await page.goto(`${base}/admin/${mode}`);await page.getByLabel(mode==='setup'?'Setup key':'Saved recovery key',{exact:true}).waitFor();
   assert.equal(new URL(page.url()).pathname,`/admin/${mode}`,'unauthenticated setup/recovery must not redirect to login');
   await fillPublic(page,mode);await page.getByLabel('Confirm new password',{exact:true}).fill('Different-password-2026');
   const submit=page.getByRole('button',{name:mode==='setup'?'Create first owner':'Recover credentials',exact:true});
   await submit.click();await page.getByRole('alert').filter({hasText:'Passwords do not match.'}).waitFor();assert.equal(calls,0);
   await page.getByLabel('Confirm new password',{exact:true}).fill(password);await submit.click();
   await page.getByRole('alert').filter({hasText:'Account verification failed.'}).waitFor();assert.equal(calls,1);
   fail=false;await submit.click();await page.getByRole('status').filter({hasText:'Account ready for'}).waitFor();assert.equal(calls,2);
   assert.equal(await page.getByRole('button',{name:'Create first owner',exact:true}).count(),0);
   const key=page.getByLabel('Recovery key',{exact:true});assert.equal(await key.inputValue(),replacement);assert.equal(await key.getAttribute('type'),'password');
   await page.getByRole('button',{name:'Show key',exact:true}).click();assert.equal(await key.getAttribute('type'),'text');
   assert.equal(await page.getByRole('link',{name:'Go to admin login',exact:true}).count(),0);
   await page.getByLabel('I saved the recovery key offline').check();await page.getByRole('link',{name:'Go to admin login',exact:true}).waitFor();
   await noPersistedSecret(page,replacement);await noPersistedSecret(page,mode==='setup'?setupKey:recoveryKey);
   assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
   const beforeLogin=profileLoads;const refreshed=page.waitForResponse(response=>response.url().endsWith('/api/admin/auth/me'));
   await page.getByRole('link',{name:'Go to admin login',exact:true}).click();await refreshed;await page.waitForURL('**/admin/login');
   assert.equal(profileLoads>beforeLogin,true,'login navigation reloads auth after credential recovery revoked the old session');
   assert.equal(await page.getByLabel('Recovery key',{exact:true}).count(),0,'raw recovery key is not retained after leaving');
   await context.close();console.log(`Owner ${mode} passed at ${width}px`);
  }
  const context=await browser.newContext({viewport:{width,height:950},serviceWorkers:'block'}),page=await context.newPage();let mutations=0,fail=true,renames=0,logouts=0;
  await mock(context,async(route,path,request)=>{
   if(path==='/api/admin/account-security'){await route.fulfill({json:{username:'owner',hasRecoveryKey:false},headers:headers()});return true;}
   if(path==='/api/admin/account-security/recovery-key'){
    mutations++;const input=request.postDataJSON();assert.equal(input.password,password);assert.equal(input.code,'123456');assert.equal(request.headers()['x-staff-csrf'],'test-csrf');
    await route.fulfill({status:fail?403:200,json:fail?{message:'Account verification failed.'}:{username:'owner',recoveryKey:replacement},headers:headers()});return true;
   }
   if(path==='/api/admin/account-security/username'){
    renames++;assert.equal(request.postDataJSON().username,'new-owner');await route.fulfill({status:204,headers:headers()});return true;
   }
   if(path==='/api/admin/auth/logout'){logouts++;await route.fulfill({status:204,headers:headers()});return true;}
   return false;
  },()=>renames?null:owner);
  await page.goto(`${base}/admin/account-security`);await page.getByText('No account recovery key has been generated yet.').waitFor();
  const generate=page.getByRole('button',{name:'Generate replacement recovery key',exact:true});assert.equal(await generate.isDisabled(),true);
  await page.getByLabel('Current password',{exact:true}).fill(password);await page.getByLabel('Authenticator or MFA recovery code',{exact:true}).fill('123456');
  await generate.click();await page.getByRole('alert').filter({hasText:'Account verification failed.'}).waitFor();assert.equal(mutations,1);
  fail=false;await generate.click();await page.getByLabel('Recovery key',{exact:true}).waitFor();assert.equal(mutations,2);
  assert.equal(await page.getByLabel('Current password',{exact:true}).inputValue(),'');assert.equal(await page.getByLabel('Authenticator or MFA recovery code',{exact:true}).inputValue(),'');
  await noPersistedSecret(page,replacement);assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
  await page.getByLabel('Current password',{exact:true}).fill(password);await page.getByLabel('Authenticator or MFA recovery code',{exact:true}).fill('654321');await page.getByLabel('New username',{exact:true}).fill('new-owner');
  await page.getByRole('button',{name:'Change username and sign out',exact:true}).click();await page.waitForURL('**/admin/login');assert.equal(renames,1);assert.equal(logouts,0);
  await context.close();console.log(`Owner account security passed at ${width}px`);
 }
 for(const available of [false,'failure']){
  const context=await browser.newContext({serviceWorkers:'block'}),page=await context.newPage();
  await mock(context,async(route,path)=>{
   if(path==='/api/admin/auth/owner-setup'){await route.fulfill({status:available==='failure'?503:200,json:{available:false},headers:headers()});return true;}return false;
  });
  await page.goto(`${base}/admin/setup`);await page.getByText(available===false?'Setup is unavailable or already completed. Use admin login.':'Unable to check setup. Reload this page to try again.').waitFor();
  assert.equal(await page.getByLabel('Setup key',{exact:true}).count(),0);await context.close();
 }
 {
  const context=await browser.newContext({serviceWorkers:'block'}),page=await context.newPage();
  await mock(context,async()=>false,{...owner,roleName:'BRANCH_MANAGER'});await page.goto(`${base}/admin/account-security`);
  await page.getByText('Only owners can manage account recovery.').waitFor();assert.equal(await page.getByLabel('Current password',{exact:true}).count(),0);await context.close();
 }
 // Lost POST responses time out, release controls, and never automatically retry a credential mutation.
 for(const mode of ['setup','recover']){
  const context=await browser.newContext({serviceWorkers:'block'}),page=await context.newPage();page.setDefaultTimeout(10000);await page.clock.install();
  await context.addInitScript(()=>{AbortSignal.timeout=milliseconds=>{const controller=new AbortController();setTimeout(()=>controller.abort(new DOMException('Account request timed out','TimeoutError')),milliseconds);return controller.signal;};});
  let calls=0,release,started;const gate=new Promise(resolve=>{release=resolve;}),requestStarted=new Promise(resolve=>{started=resolve;});
  await mock(context,async(route,path,request)=>{
   if(path==='/api/admin/auth/owner-setup'&&request.method()==='GET'){await route.fulfill({json:{available:true},headers:headers()});return true;}
   if(path.startsWith('/api/admin/auth/owner-')&&request.method()==='POST'){calls++;started();await gate;await route.fulfill({json:{username:'first.owner',recoveryKey:replacement},headers:headers()}).catch(()=>{});return true;}return false;
  });
  await page.goto(`${base}/admin/${mode}`);await fillPublic(page,mode);const submit=page.getByRole('button',{name:mode==='setup'?'Create first owner':'Recover credentials',exact:true});await submit.click();await requestStarted;
  assert.equal(await page.getByLabel('New password',{exact:true}).isDisabled(),true);await page.clock.fastForward(30001);
  await page.getByRole('alert').filter({hasText:'try admin login'}).waitFor();assert.equal(calls,1);assert.equal(await submit.isEnabled(),true);assert.equal(await page.getByLabel('Recovery key',{exact:true}).count(),0);
  release();await context.close();console.log(`Owner ${mode} lost-response timeout passed`);
 }
} finally {await browser.close();}
