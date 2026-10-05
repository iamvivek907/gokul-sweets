import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {mkdir} from 'node:fs/promises';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const base=process.env.BROWSER_BASE??'http://127.0.0.1:3311',browser=await chromium.launch({headless:true});
const today=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata'}).format(new Date());
const shift=n=>{const d=new Date(`${today}T00:00:00Z`);d.setUTCDate(d.getUTCDate()+n);return d.toISOString().slice(0,10);};
const tomorrow=shift(1),later=shift(2),phone='+919876543210';
const branch={id:1,code:'ONE',name:'Original branch',active:true,operational:true,pickupAvailable:true},other={...branch,id:2,name:'Other branch'};
const products=[{id:1,categoryId:1,categoryName:'Sweets',name:'Peda',description:null,price:20,imageUrl:null,available:true,saleMode:'UNIT',minimumWeightGrams:null,weightStepGrams:null},{id:2,categoryId:1,categoryName:'Sweets',name:'Kaju Barfi',description:null,price:1000,imageUrl:null,available:true,saleMode:'WEIGHT',minimumWeightGrams:250,weightStepGrams:50}];
const history=Array.from({length:13},(_,i)=>({orderNumber:`HISTORY-${13-i}`,customerOrderNumber:13-i,orderStatus:'PICKED_UP',branchId:1,branchName:branch.name,pickupDate:today,totalAmount:1040,fulfillmentType:'PICKUP',createdAt:`${today}T09:00:00`,updatedAt:`${today}T09:00:00`}));
try{for(const width of [320,390,1280]){
 let pageFailure=false,availabilityFailure=false,slotLost=false,priceChanged=false,postCount=0,pages=0,checks=[];
 const context=await browser.newContext({viewport:{width,height:844},isMobile:width<=640,hasTouch:width<=640,serviceWorkers:'block'}),page=await context.newPage();
 await context.addInitScript(branch=>{localStorage.setItem('gokul-selected-branch',JSON.stringify(branch));localStorage.setItem('gokul-social-follow-popup-seen','true');},other);
 await context.route('**/api/**',async route=>{
  const request=route.request(),url=new URL(request.url()),path=url.pathname;let json=[];
  if(path==='/api/storefront/features')json={today,futureOrderingDays:30,smartAvailability:true,futuristicStorefrontV2:true,checkoutExperienceV2:true,simplifiedCheckout:true,acceptedCheckoutQuote:true,customerAccountHub:true,branchExperience:true};
  else if(path==='/api/storefront/customer-identity')json={enabled:true};
  else if(path==='/api/customer/identity/me')json={authenticated:true,name:'Test customer',phone};
  else if(path==='/api/customer/identity/account')json={completedOrders:13,paidOrders:13,favouriteProductIds:[],addresses:[],preferences:{dietaryNotes:null,preferredBranchId:null}};
  else if(path==='/api/customer/identity/orders/page'){pages++;if(url.searchParams.has('before')&&pageFailure)return route.fulfill({status:503,json:{message:'Unavailable'}});json=url.searchParams.has('before')?{orders:history.slice(10),nextBefore:null}:{orders:history.slice(0,10),nextBefore:history[9].orderNumber};}
  else if(path.startsWith('/api/customer/identity/orders/HISTORY-'))json={...history[0],items:products.map(product=>({id:product.id,productId:product.id,productName:product.name,saleMode:product.saleMode,quantity:product.saleMode==='UNIT'?2:1,weightGrams:product.saleMode==='WEIGHT'?1000:null,unitPrice:product.price,lineTotal:product.saleMode==='WEIGHT'?1000:40}))};
  else if(path==='/api/branches')json=[branch,other];
  else if(path==='/api/branches/1')json=branch;else if(path==='/api/branches/2')json=other;
  else if(path==='/api/menu')json=[{id:1,name:'Sweets',products:products.map(p=>({...p,price:p.id===1&&priceChanged?25:p.price}))}];
  else if(path==='/api/branches/1/availability'){
   const body=request.postDataJSON();checks.push(body);if(availabilityFailure)return route.fulfill({status:503,json:{message:'Unavailable'}});
   const day=date=>({date,available:date!==today,slots:date===today?[]:[{slot:{id:date===tomorrow?1:2,branchId:1,slotDate:date,startTime:'09:00:00',endTime:'09:30:00',active:true,remainingCapacity:5,priorityEnabled:false,priorityCharge:0},normalAvailable:!(slotLost&&body.days===1)&&body.items.every(item=>item.quantity==null||item.quantity<=3),priorityAvailable:false}],reason:'No pickup time can fulfil these quantities.'});
   json={today,maximumDate:later,dates:(body.days===1?[body.startDate]:[today,tomorrow,later]).map(day)};
  }else if(request.method()==='POST'&&['/api/orders','/api/payments'].includes(path))postCount++;
  return route.fulfill({json});
 });
 await page.goto(`${base}/profile/orders`);await page.locator('.profile-order-card').first().waitFor();await page.locator('.gokul-mobile-launch').waitFor({state:'hidden'});
 const nav=page.getByRole('navigation',{name:'Order history pages'});
 assert.equal(await page.locator('.profile-order-card').count(),10);pageFailure=true;
 await nav.getByRole('button',{name:'Next',exact:true}).click();await page.getByText('The next page could not load.',{exact:false}).waitFor();assert.equal(await page.locator('.profile-order-card').count(),10);
 pageFailure=false;await nav.getByRole('button',{name:'Next',exact:true}).click();await nav.getByText('Page 2',{exact:true}).waitFor();assert.equal(await page.locator('.profile-order-card').count(),3);assert.equal(await nav.getByRole('button',{name:'Next',exact:true}).isDisabled(),true);
 const pageReads=pages;await nav.getByRole('button',{name:'Previous',exact:true}).click();await nav.getByText('Page 1',{exact:true}).waitFor();assert.equal(pages,pageReads,'previous uses its cached bounded page');
 const open=async()=>{await page.locator('.profile-order-card').first().getByRole('button',{name:'Reorder',exact:true}).click();await page.getByRole('dialog',{name:'Reorder for pickup'}).waitFor();};
 const dialog=page.getByRole('dialog',{name:'Reorder for pickup'}),continueButton=dialog.getByRole('button',{name:'Continue to checkout',exact:true});
 await open();await dialog.locator('select').waitFor();assert.equal(await dialog.locator('select').inputValue(),tomorrow,'earliest whole-cart slot skips today');
 await dialog.getByLabel('Peda quantity',{exact:true}).fill('3');await dialog.getByLabel('Kaju Barfi weight in grams',{exact:true}).fill('1500');
 await page.waitForFunction(()=>!document.querySelector('.reorder-dialog footer button:last-child').disabled);
 await dialog.locator('select').selectOption(later);
 assert.equal(await page.evaluate(()=>document.body.style.overflow),'hidden');
 assert.equal(await page.evaluate(()=>localStorage.getItem('gokul-cart')),null,'editing leaves the cart untouched');
 await dialog.getByRole('button',{name:'Cancel',exact:true}).click();await dialog.waitFor({state:'detached'});assert.equal(await page.evaluate(()=>document.body.style.overflow),'');assert.equal(await page.evaluate(()=>localStorage.getItem('gokul-cart')),null);
 availabilityFailure=true;await open();await dialog.getByText('Pickup availability could not load.',{exact:false}).waitFor();assert.equal(await continueButton.isDisabled(),true);
 availabilityFailure=false;await dialog.getByRole('button',{name:'Try again',exact:true}).click();await dialog.locator('select').waitFor();
 // An unfinished payment blocks replacing the cart.
 await page.evaluate(()=>localStorage.setItem('gokul-pending-order','saved-payment'));await continueButton.click();await dialog.getByText('Resolve your unfinished checkout',{exact:false}).waitFor();assert.equal(await page.evaluate(()=>localStorage.getItem('gokul-cart')),null);await page.evaluate(()=>localStorage.removeItem('gokul-pending-order'));
 slotLost=true;await continueButton.click();await dialog.getByText('That pickup is no longer available.',{exact:false}).waitFor();assert.equal(await page.evaluate(()=>localStorage.getItem('gokul-cart')),null);slotLost=false;
 await dialog.getByRole('button',{name:'Try again',exact:true}).click();await dialog.getByRole('button',{name:/9:00.*9:30/i}).click();
 priceChanged=true;await continueButton.click();await dialog.getByText('Prices changed.',{exact:false}).waitFor();assert.equal(await page.evaluate(()=>localStorage.getItem('gokul-cart')),null);await dialog.getByRole('button',{name:'Cancel',exact:true}).click();
 // Explicit replacement consent; cross-tab edits reject stale continuation.
 const existing={branchId:2,items:[{product:products[0],quantity:1,weightGrams:null}]};await page.evaluate(cart=>localStorage.setItem('gokul-cart',JSON.stringify(cart)),existing);
 await open();await dialog.locator('select').waitFor();assert.equal(await continueButton.isDisabled(),true);await dialog.getByRole('checkbox').check();
 await page.evaluate(cart=>{localStorage.setItem('gokul-cart',JSON.stringify({...cart,items:[{...cart.items[0],quantity:2}]}));window.dispatchEvent(new Event('storage'));},existing);
 await continueButton.click();await dialog.getByText('Your cart or pickup changed',{exact:false}).waitFor();assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('gokul-cart')).items[0].quantity),2);await dialog.getByRole('button',{name:'Cancel',exact:true}).click();
 await open();await dialog.locator('select').waitFor();await dialog.getByRole('checkbox').check();await dialog.getByLabel('Peda quantity',{exact:true}).fill('3');await dialog.getByLabel('Kaju Barfi weight in grams',{exact:true}).fill('1500');await page.waitForFunction(()=>!document.querySelector('.reorder-dialog footer button:last-child').disabled);await dialog.locator('select').selectOption(later);
 if(process.env.SCREENSHOT_DIR){await mkdir(process.env.SCREENSHOT_DIR,{recursive:true});await page.screenshot({path:`${process.env.SCREENSHOT_DIR}/reorder-${width}.png`});}
 await continueButton.click();await page.waitForURL(width<=640?'**/checkout/mobile':'**/checkout/pickup');
 const saved=await page.evaluate(()=>({cart:JSON.parse(localStorage.getItem('gokul-cart')),pickup:JSON.parse(localStorage.getItem('gokul-selected-pickup-slot')),branch:JSON.parse(localStorage.getItem('gokul-selected-branch'))}));
 assert.equal(saved.branch.id,1);assert.equal(saved.cart.branchId,1);assert.equal(saved.cart.items[0].quantity,3);assert.equal(saved.cart.items[0].product.price,25);assert.equal(saved.cart.items[1].weightGrams,1500);assert.equal(saved.pickup.date,later);assert.equal(saved.pickup.slot.id,2);assert.equal(postCount,0,'reorder creates neither orders nor payments');assert.ok(checks.some(body=>body.days===1&&body.items[1].weightGrams===1500));
 await context.close();console.log(`Profile pagination/reorder ${width}px passed`);
}}finally{await browser.close();}
