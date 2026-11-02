const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript');
function load(file,require,extras={}){const exports={};vm.runInNewContext(ts.transpileModule(fs.readFileSync(file,'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText,{exports,require,...extras});return exports;}
const helpers=load('lib/orderReminder.ts',p=>p.includes('businessTime')?{businessDateOffset:(days,now)=>new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata'}).format(new Date(now.getTime()+days*86400000))}:{formatOrderDate:d=>d,formatOrderTime:t=>t});
test('cross-branch reminders group by date and time without changing the source queue',()=>{
 const orders=[{orderNumber:'LATE',branchId:1,pickupDate:'2026-11-04',pickupStartTime:'18:00'},{orderNumber:'DELIVERY',branchId:3,fulfillmentType:'DELIVERY',deliveryDate:'2026-11-03',deliveryStartTime:'15:00'},{orderNumber:'EARLY',branchId:2,pickupDate:'2026-11-03',pickupStartTime:'12:00'},{orderNumber:'UNKNOWN',branchId:1}];
 const grouped=helpers.groupUpcomingOrders(orders);assert.deepEqual(Array.from(grouped,g=>g.date),['2026-11-03','2026-11-04','']);assert.deepEqual(Array.from(grouped[0].orders,o=>o.orderNumber),['EARLY','DELIVERY']);assert.equal(orders[0].orderNumber,'LATE');
});
test('today and tomorrow use IST across UTC midnight; full delivery window is retained',()=>{
 const now=new Date('2026-11-02T20:00:00Z');assert.equal(helpers.reminderDay('2026-11-03','en',now),'Today');assert.equal(helpers.reminderDay('2026-11-04','hi',now),'कल');assert.equal(helpers.reminderDay(undefined,'en',now),'Open order for time');assert.match(helpers.reminderTime({fulfillmentType:'DELIVERY',deliveryDate:'2030-01-02',deliveryStartTime:'14:00',deliveryEndTime:'14:30'},'en'),/2030-01-02 · 14:00–14:30/);
});
test('dismissal and introduction survive remounts and reset for a different identity',()=>{
 let identity,revision=null;const session=load('lib/orderReminderSession.ts',()=>({subscribeCustomerIdentityChanges:fn=>{identity=fn;return()=>{};}}),{localStorage:{getItem:()=>revision}});
 let updates=0;let stop=session.subscribeOrderReminder(()=>updates++);assert.equal(session.claimOrderIntroduction(),true);assert.equal(session.claimOrderIntroduction(),false);session.dismissOrderReminder();assert.equal(session.reminderDismissed(),true);stop();stop=session.subscribeOrderReminder(()=>updates++);assert.equal(session.reminderDismissed(),true);revision='new';identity();assert.equal(session.reminderDismissed(),false);assert.equal(session.claimOrderIntroduction(),true);session.dismissOrderReminder();stop();revision='changed-away';stop=session.subscribeOrderReminder(()=>{});assert.equal(session.reminderDismissed(),false);assert.ok(updates>0);stop();
});
for(const blockedStorage of [false,true])test(`real login notification preserves dismissal across navigation (storage blocked: ${blockedStorage})`,()=>{
 const window=new EventTarget(),document=new EventTarget(),values=new Map();let revision=0;
 const localStorage={getItem:key=>values.get(key)??null,setItem:(key,value)=>{if(blockedStorage)throw new Error('Storage blocked');values.set(key,value);}};
 const runtime={window,document,localStorage,Event,crypto:{randomUUID:()=>`identity-${++revision}`}};
 const events=load('lib/customerIdentityEvents.ts',()=>{},runtime);
 const session=load('lib/orderReminderSession.ts',()=>events,runtime);
 let stop=session.subscribeOrderReminder(()=>{});
 events.notifyCustomerIdentityChanged();
 assert.equal(session.claimOrderIntroduction(),true);
 session.dismissOrderReminder();assert.equal(session.reminderDismissed(),true);stop();
 // Home/Menu remount after login must not reinterpret the same login as a new user.
 stop=session.subscribeOrderReminder(()=>{});
 assert.equal(session.reminderDismissed(),true);
 assert.equal(session.claimOrderIntroduction(),false);
 // A subsequent real identity change must still clear suppression.
 events.notifyCustomerIdentityChanged();
 assert.equal(session.reminderDismissed(),false);
 assert.equal(session.claimOrderIntroduction(),true);stop();
 assert.ok([...values.keys()].every(key=>key==='gokul-customer-identity-revision'));
});
