import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const base=process.env.BROWSER_BASE??'http://127.0.0.1:3311',browser=await chromium.launch({headless:true});
const branch={id:1,name:'Main branch',active:true,operational:true,pickupAvailable:true};
const badge={id:1,code:'REGULAR',name:'Gokul regular',description:'Thank you for coming back',requiredOrders:2,minimumSubtotal:500,bonusPercent:25,appearance:'GOLD',active:true,version:1,qualifyingOrders:2,earned:true};
try{for(const width of [320,390,640,1280]){
 const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:'block'}),page=await context.newPage();page.setDefaultTimeout(15000);
 let shown=false,claims=0,acks=0,orders=0,walletDelay=false,rewardsEnabled=true;const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await context.addInitScript(branch=>{localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));localStorage.setItem('gokul-social-follow-popup-seen','true');},branch);
 await context.route('**/api/**',async route=>{
  const req=route.request(),path=new URL(req.url()).pathname;const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Headers':'content-type,x-staff-csrf','Access-Control-Allow-Methods':'GET,POST,PUT,OPTIONS','Access-Control-Expose-Headers':'X-Staff-CSRF','X-Staff-CSRF':'test-csrf'};
  if(req.method()==='OPTIONS')return route.fulfill({status:204,headers});let json=[];
  if(path==='/api/storefront/features')json={futuristicStorefrontV2:true,checkoutExperienceV2:true,customerAccountHub:true,gokulRewards:rewardsEnabled,truthfulOrderTracking:true};
  else if(path==='/api/storefront/customer-identity')json={enabled:true,guestCheckoutEnabled:false};
  else if(path==='/api/customer/identity/me')json={authenticated:true,name:'Vivek',phone:'+919876543210'};
  else if(path==='/api/customer/identity/account')json={completedOrders:2,paidOrders:2,favouriteProductIds:[],addresses:[],preferences:{dietaryNotes:null,preferredBranchId:null}};
  else if(path==='/api/customer/identity/badges')json={badges:[badge,{...badge,id:2,code:'FAVOURITE',name:'Favourite',requiredOrders:5,qualifyingOrders:2,earned:false,bonusPercent:40}],current:badge};
  else if(path==='/api/customer/identity/badges/celebration'){claims++;if(width===390&&!shown)await page.evaluate(()=>{const d=document.createElement('dialog');d.id='claim-race-dialog';document.body.append(d);d.showModal();});json=shown?null:{awardId:1,claimId:'claim-1',...badge};}
  else if(path==='/api/customer/identity/badges/1/acknowledge'){shown=true;acks++;return route.fulfill({status:204,headers});}
  else if(path==='/api/customer/identity/orders/page')json={orders:[],nextBefore:null};
  else if(path==='/api/customer/identity/orders')json=[];
  else if(path==='/api/customer/identity/rewards'){
   if(walletDelay)await new Promise(r=>setTimeout(r,2500));
   json={policyVersion:'1',balance:30,debt:0,pendingCoins:0,completedOrders:2,rewards:[{code:'SAVE',name:'Sweet saving',coins:30,discount:50,minimumSubtotal:149,eligible:true}],history:[{id:1,kind:'EARNED',coins:30,badgeBonusCoins:5,badgeName:'Gokul regular',reason:'Order completed',createdAt:new Date().toISOString()}],maximumRedemptionPercent:10,terms:'Fees are excluded'};
  }
  else if(path==='/api/branches')json=[branch];else if(path==='/api/branches/1')json=branch;
  else if(path==='/api/admin/auth/me')return route.fulfill({status:401,json:{},headers});
  else if(path==='/api/orders'&&req.method()==='POST')orders++;
  await route.fulfill({json,headers});
 });
 await page.clock.install();
 await page.goto(`${base}/profile/badges`);await page.getByRole('heading',{name:'Your badges',exact:true}).waitFor();
 await page.locator('.configured-badge').first().waitFor();assert.match(await page.locator('.configured-badge').first().innerText(),/subtotal ₹500/);assert.match(await page.locator('.configured-badge').nth(1).innerText(),/2 \/ 5/);
 assert.equal(await page.locator('.configured-badge.is-earned').getByRole('img',{name:'Gokul regular · earned Gokul recognition',exact:true}).count(),1);
 assert.equal(await page.locator('.configured-badge.is-locked').getByRole('img',{name:'Favourite · locked Gokul recognition',exact:true}).count(),1);
 assert.equal(await page.locator('.configured-badge.is-locked').getByRole('img',{name:/earned Gokul recognition/}).count(),0);
 await page.evaluate(()=>{const d=document.createElement('dialog');d.id='onboarding-dialog';document.body.append(d);d.showModal();});
 await page.waitForTimeout(3000);assert.equal(claims,0,'no claim while another dialog is open');
 await page.evaluate(()=>document.getElementById('onboarding-dialog').remove());
 if(width===390){await page.locator('#claim-race-dialog[open]').waitFor({state:'attached'});await page.waitForTimeout(700);assert.equal(acks,0,'a claimed award is not acknowledged behind another dialog');await page.evaluate(()=>document.getElementById('claim-race-dialog').remove());}
 await page.getByRole('dialog',{name:'You’ve earned Gokul regular',exact:true}).waitFor();assert.match(await page.locator('.badge-celebration').innerText(),/\+25% bonus/);
 assert.ok(acks>=1);const medal=page.locator('.badge-celebration .badge-medallion');const r=await medal.boundingBox();assert.ok(Math.abs(r.width-r.height)<1);assert.ok(r.width>=80);
 await page.emulateMedia({reducedMotion:'reduce'});assert.equal(await page.locator('.badge-celebration').evaluate(n=>getComputedStyle(n).animationName),'none');
 await page.locator('.badge-celebration').getByRole('button',{name:'Continue',exact:true}).click();await page.reload();await page.locator('.configured-badge').first().waitFor();await page.waitForTimeout(3000);assert.equal(await page.locator('.badge-celebration[open]').count(),0,'acknowledged achievement never replays on document refresh');
 walletDelay=true;const start=Date.now();await page.goto(`${base}/orders`);await page.getByRole('heading',{name:'My Orders',exact:true}).waitFor();assert.ok(Date.now()-start<2200,'order content does not await optional rewards');await page.locator('.order-reward-guidance').waitFor();
 await page.getByText('No orders yet',{exact:true}).waitFor();
 await page.waitForTimeout(500); // Let the existing responsive Orders shell settle before measuring the optional wallet.
 const before=(await page.getByText('No orders yet',{exact:true}).boundingBox()).y;
 await page.getByRole('heading',{name:'30 coins available',exact:true}).waitFor();
 const after=(await page.getByText('No orders yet',{exact:true}).boundingBox()).y;
 assert.ok(Math.abs(after-before)<1,`late wallet moved order history ${after-before}px`);
 await page.locator('.order-reward-guidance').getByRole('button',{name:'Details',exact:true}).click();assert.match(await page.locator('.order-reward-guidance').innerText(),/25 normal \+ 5 Gokul regular bonus/);assert.match(await page.locator('.order-reward-guidance').innerText(),/₹500 or more/);
 rewardsEnabled=false;await page.clock.fastForward(61000);
 await page.waitForFunction(()=>JSON.parse(sessionStorage.getItem('gokul-storefront-settings')).features.gokulRewards===false);
 assert.equal(await page.locator('.order-reward-guidance').count(),0,'paused rewards discard the displayed wallet');
 rewardsEnabled=true;await page.clock.fastForward(61000);
 await page.getByRole('heading',{name:'Coins & benefits',exact:true}).waitFor();
 assert.equal(await page.getByRole('heading',{name:'30 coins available',exact:true}).count(),0,'re-enabled rewards fetch a fresh wallet');
 await page.getByRole('heading',{name:'30 coins available',exact:true}).waitFor();
 assert.equal(await page.locator('.order-reward-details').count(),0,'old expanded state is discarded');
 assert.equal(orders,0);assert.deepEqual(errors,[]);assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
 await context.close();console.log(`Configured badges, durable celebration and nonblocking rewards passed at ${width}px (${claims} claims).`);
 }
 const response=await fetch(`${base}/downloads/gokul-print-agent.zip`);assert.equal(response.status,200);assert.match(response.headers.get('content-type'),/application\/zip/);assert.match(response.headers.get('content-disposition'),/gokul-print-agent.zip/);const bytes=Buffer.from(await response.arrayBuffer());assert.equal(bytes.readUInt32LE(0),0x04034b50);assert.ok(bytes.includes(Buffer.from('install.ps1')));const names=[];for(let offset=0;bytes.readUInt32LE(offset)===0x04034b50;){const length=bytes.readUInt16LE(offset+26),extra=bytes.readUInt16LE(offset+28),size=bytes.readUInt32LE(offset+18);names.push(bytes.subarray(offset+30,offset+30+length).toString());offset+=30+length+extra+size;}assert.deepEqual(names,['agent.py','background.py','requirements.txt','install.ps1','install.cmd']);console.log('Deployed installer route returns the actual ZIP.');
}finally{await browser.close();}
