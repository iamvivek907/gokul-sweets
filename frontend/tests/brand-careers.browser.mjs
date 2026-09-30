import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const browser=await chromium.launch({headless:true});
const context=await browser.newContext({viewport:{width:390,height:844},timezoneId:'America/Los_Angeles'});
const page=await context.newPage();const base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
const job={id:1,branchId:1,branchName:'Test branch',title:'Test role',department:'Test kitchen',requirements:'Test skills and training',minimumExperience:0,active:true,version:0};
const story={title:'Test brand story',subtitle:'Verified test introduction',storyTitle:'Our beginnings',storyBody:'Published test content',imageUrl:null,published:true,version:0};
const applicant={id:'11111111-1111-4111-8111-111111111111',jobId:1,branchId:1,branchName:'Test branch',jobTitle:'Test role',name:'Test applicant',phone:'9876543210',email:null,desiredRole:'Test role',experience:2,qualifications:'Test qualifications',status:'NEW',staffNotes:'',createdAt:'2026-09-30T20:00:00Z',version:0};
let applications=0,updates=0,conflict=false;
await context.route('**/api/**',async route=>{const req=route.request(),u=new URL(req.url()),p=u.pathname;let json=[];
 if(req.method()==='OPTIONS')return route.fulfill({status:204,headers:{'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Methods':'GET,POST,PUT,DELETE,OPTIONS','Access-Control-Allow-Headers':'content-type,x-staff-csrf,if-match'}});
 if(p==='/api/storefront/features')json={brandCareers:true,futuristicStorefrontV2:true,customerHomeV2:true};
 else if(p==='/api/branches')json=[{id:1,name:'Test branch',active:true,address:'Test address'}];
 else if(p==='/api/admin/auth/me')return route.fulfill({json:{staffId:1,username:'test',fullName:'Test manager',roleName:'OWNER_ADMIN',branchIds:[1],permissions:['ABOUT_MANAGE','CAREERS_MANAGE']},headers:{'X-Staff-CSRF':'test-csrf','Access-Control-Expose-Headers':'X-Staff-CSRF','Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true'}});
 else if(p==='/api/storefront/about'||p==='/api/admin/about'){
  if(req.method()==='PUT'){assert.equal(req.headers()['if-match'],'0');assert.equal(req.headers()['x-staff-csrf'],'test-csrf');if(conflict)return route.fulfill({status:409,json:{}});updates++;json={...story,...req.postDataJSON(),version:1};}else json={story,people:[]};
 }
 else if(p==='/api/storefront/careers'||p==='/api/admin/careers/jobs')json=[job];
 else if(p==='/api/storefront/careers/applications'){const body=req.postDataJSON();assert.equal(body.jobId,1);assert.equal(body.branchId,1);assert.equal(body.consent,true);assert.ok(body.requestId);applications++;json={reference:body.requestId,message:'Application received.'};}
 else if(p==='/api/admin/careers/applications'){assert.equal(u.searchParams.get('size'),'25');json={items:[applicant],total:1,page:0,size:25,counts:{NEW:1}};}
 else if(p.endsWith(applicant.id)){if(req.method()==='PUT'){assert.equal(req.headers()['if-match'],'0');updates++;json={...applicant,...req.postDataJSON(),version:1};}else json=applicant;}
 return route.fulfill({json,headers:{'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true'}});
});
try{
 await page.goto(`${base}/about`);await page.getByRole('heading',{name:'Test brand story'}).waitFor();assert.equal(await page.getByRole('link',{name:/Explore opportunities/}).getAttribute('href'),'/careers');
 await page.goto(`${base}/careers`);await page.getByRole('button',{name:'Apply for this role'}).click();await page.getByLabel('Full name').fill('Test applicant');await page.getByLabel('Mobile number').fill('9876543210');await page.getByLabel('Relevant qualifications and experience').fill('Test qualification');await page.getByRole('checkbox').check();await page.getByRole('button',{name:'Submit application'}).click();await page.getByRole('status').filter({hasText:'Application received'}).waitFor();assert.equal(applications,1);
 await page.goto(`${base}/admin/about`);await page.getByLabel('Headline').fill('Updated headline');conflict=true;await page.getByRole('button',{name:'Save story'}).click();await page.getByRole('alert').filter({hasText:'Someone changed'}).waitFor();assert.equal(updates,0);
 await page.goto(`${base}/admin/careers`);await page.getByRole('button',{name:'View details'}).click();await page.getByRole('heading',{name:'Test applicant'}).waitFor();await page.getByLabel('Application status').selectOption('REVIEWING');await page.getByLabel('Private staff notes').fill('Relevant private test note');await page.getByRole('button',{name:'Save review'}).click();await page.getByRole('heading',{name:'Test applicant'}).waitFor({state:'hidden'});assert.equal(updates,1);
 for(const width of [390,1440]){await page.setViewportSize({width,height:900});for(const path of ['/about','/careers','/admin/about','/admin/careers']){await page.goto(`${base}${path}`);await page.waitForTimeout(400);assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false,`${path} ${width} overflow`);if(process.env.SCREENSHOT_DIR)await page.screenshot({path:`${process.env.SCREENSHOT_DIR}/${path.replaceAll('/','-')}-${width}.png`,fullPage:true});}}
 console.log('PASS: desktop/mobile About, consent application, stale About edit, private applicant review and pagination request.');
}finally{await browser.close();}
