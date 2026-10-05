import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const browser=await chromium.launch({headless:true}),base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
const date='2026-10-05',branch={id:1,code:'DESK',name:'Test branch',active:true,pickupAvailable:true};
try{for(const [width,role] of [[1280,'MANAGER'],[390,'KITCHEN_STAFF'],[320,'COUNTER_STAFF']]){
 const context=await browser.newContext({viewport:{width,height:900}}),page=await context.newPage();await page.clock.install({time:new Date('2026-10-05T06:30:00Z')});let starts=0,exports=0,fail=false;const errors=[];page.on('pageerror',e=>errors.push(e.message));
 const rows=Array.from({length:24},(_,i)=>({orderNumber:`DESK-${i}`,customerOrderNumber:501+i,customerName:'Customer',fulfillmentType:'PICKUP',orderStatus:i<starts?'PREPARING':'CONFIRMED',bucket:'ELIGIBLE',date,start:'12:30:00',end:'13:00:00',preparationAt:`${date}T11:30:00`,earlyPreparation:false,items:[{productId:1,productName:'Kaju Barfi',saleMode:'WEIGHT',quantity:1,weightGrams:500},{productId:2,productName:'Samosa',saleMode:'UNIT',quantity:4,weightGrams:null}]}));
 const ready={...rows[0],orderNumber:'DESK-READY',customerOrderNumber:600,orderStatus:'READY_FOR_PICKUP',bucket:'READY'};
 const preparing={...rows[0],orderNumber:'DESK-PREPARING',customerOrderNumber:601,orderStatus:'PREPARING',bucket:'PREPARING'};
 await context.route('**/api/**',async route=>{const req=route.request(),u=new URL(req.url()),p=u.pathname,headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Methods':'GET,POST,PUT,PATCH,OPTIONS','Access-Control-Allow-Headers':'content-type,x-staff-csrf','Access-Control-Expose-Headers':'X-Staff-CSRF'};if(req.method()==='OPTIONS')return route.fulfill({status:204,headers});let json=[];
  if(p==='/api/admin/auth/me')return route.fulfill({json:{staffId:1,username:'test',fullName:'Test staff',roleName:role,branchIds:[1],permissions:['ORDER_VIEW',...(role==='COUNTER_STAFF'?['ORDER_MARK_PICKED_UP']:['ORDER_START_PREPARATION','ORDER_MARK_READY']),...(role==='MANAGER'?['ORDER_MARK_PICKED_UP','REPORT_VIEW','MENU_MANAGE','ORDER_CANCEL']:[])]},headers:{...headers,'X-Staff-CSRF':'test-csrf'}});
  if(p==='/api/storefront/features')json={adminPreparationBoard:true,bilingualStorefront:true,today:date};
  else if(p==='/api/branches')json=[branch,{...branch,id:2,name:'Other branch'}];
  else if(p==='/api/admin/orders/planning'){if(fail)return route.fulfill({status:503,json:{message:'Service temporarily unavailable'},headers});const f=u.searchParams.get('filter'),n=Number(u.searchParams.get('page'));const list=f==='WAITING'?rows.filter(r=>r.customerOrderNumber>=501+starts):f==='IN_PROGRESS'?[preparing,...rows.slice(0,starts).map(r=>({...r,orderStatus:'PREPARING',bucket:'PREPARING'}))]:f==='HANDOVER'?[ready]:[{...rows[0],bucket:'SCHEDULED',preparationAt:`${date}T19:30:00`,start:'20:00:00'}];json={orders:list.slice(n*20,n*20+20),slots:[{date,start:'12:30:00',end:'13:00:00',fulfillmentType:'PICKUP',total:24}],counts:{WAITING:24-starts,IN_PROGRESS:starts+1,HANDOVER:1},page:n,total:list.length,generatedAt:`${date}T12:00:00`};}
  else if(p==='/api/admin/orders/queue/start-selected'){assert.equal(req.postDataJSON().orderNumbers.length,20);starts=20;json={started:20,skipped:0,results:req.postDataJSON().orderNumbers.map(orderNumber=>({orderNumber,result:'STARTED',message:'Started'}))};}
  else if(p==='/api/admin/orders/planning/alerts')json={needsPreparation:0,readyOverdue:0};
  else if(p==='/api/admin/orders/number/601')json={...preparing,branchId:1,pickupDate:date,pickupStartTime:'12:30:00',pickupEndTime:'13:00:00'};
  else if(p==='/api/admin/orders/number/999')json={...preparing,branchId:2};
  else if(p==='/api/admin/orders/planning/demand/export'){exports++;return route.fulfill({body:Buffer.from('export-test'),contentType:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',headers});}
  else if(p==='/api/admin/orders/planning/demand')json=[{date,productId:1,productName:'Kaju Barfi',saleMode:'WEIGHT',ordered:12500,waiting:10000,preparing:2000,ready:500,completed:0,orderCount:24},{date:'2026-10-06',productId:2,productName:'Samosa',saleMode:'UNIT',ordered:80,waiting:80,preparing:0,ready:0,completed:0,orderCount:20}];
  return route.fulfill({json,headers});
 });
 await page.goto(`${base}/admin/order-desk`);await page.getByRole('heading',{name:'Order desk',exact:true}).waitFor();
 if(role==='COUNTER_STAFF'){
  await page.getByText('#600',{exact:true}).waitFor();assert.equal(await page.getByRole('button',{name:'Prepare / KOT',exact:true}).count(),0);
  await page.getByRole('searchbox',{name:'Find an order number'}).fill('601');await page.getByText('This order is being prepared.',{exact:true}).waitFor();assert.equal(await page.getByRole('button',{name:'Confirm pickup',exact:true}).count(),0);
  await page.getByRole('button',{name:'Language / भाषा',exact:true}).click();await page.getByRole('button',{name:/हिन्दी/}).click();await page.getByText('यह ऑर्डर अभी तैयार हो रहा है।',{exact:true}).waitFor();
 }else{
  await page.getByRole('button',{name:'Select 20 eligible on this page'}).click();await page.getByRole('button',{name:'Start selected + KOT',exact:true}).click();const dialog=page.getByRole('dialog');assert.match(await dialog.innerText(),/10 kg/);assert.match(await dialog.innerText(),/80 pcs/);await dialog.getByRole('button',{name:'Confirm start',exact:true}).click();await page.getByText(/20 started · 0 skipped/).waitFor();assert.equal(starts,20);
  assert.equal(await page.getByRole('button',{name:'Demand & exports',exact:true}).count(),role==='MANAGER'?1:0);
  if(role==='MANAGER'){await page.getByRole('button',{name:'Demand & exports',exact:true}).click();await page.getByRole('button',{name:/2026-10-06/}).click();await page.getByRole('heading',{name:'Samosa',exact:true}).waitFor();const download=page.waitForEvent('download');await page.getByRole('button',{name:'Download Excel',exact:true}).click();assert.match((await download).suggestedFilename(),/^gokul-demand-1-/);assert.equal(exports,1);}
  await page.getByRole('button',{name:'Scheduled',exact:true}).click();await page.getByText(/Preparation opens/).waitFor();assert.equal(await page.getByRole('checkbox').count(),0);
  fail=true;await page.getByRole('button',{name:'Prepare / KOT',exact:true}).click();await page.getByRole('alert').filter({hasText:'Service temporarily unavailable'}).waitFor();assert.equal(await page.getByRole('button',{name:'Start selected + KOT',exact:true}).isDisabled(),true);fail=false;await page.getByRole('button',{name:'Retry',exact:true}).click();await page.getByRole('checkbox',{name:'Select #521'}).waitFor();
 }
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);assert.deepEqual(errors,[]);assert.equal(exports,role==='MANAGER'?1:0);await context.close();console.log(`${role} ${width}px passed`);
}}finally{await browser.close();}
