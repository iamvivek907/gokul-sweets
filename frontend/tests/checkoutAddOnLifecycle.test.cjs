const assert=require('node:assert/strict'),test=require('node:test'),fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript');
const jsx=require('react/jsx-runtime');
const refreshModule={exports:{}};vm.runInNewContext(ts.transpileModule(fs.readFileSync('lib/checkoutRefresh.ts','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText,refreshModule);
const {CheckoutUpdateUncertainError}=refreshModule.exports;
function fixture(onAdded=()=>{}){
 const tea={id:2,name:'Tea',available:true,saleMode:'UNIT',price:30,imageUrl:null};
 const items=[{product:{...tea,id:1},quantity:1,weightGrams:null}];
 const request=JSON.stringify({serviceDate:'2026-10-05',items:[{productId:1,quantity:1,weightGrams:null}]});
 const suggestion={product:tea,weightGrams:null,portionPrice:30,portionTotal:30,reason:'Pairing'};
 let stateIndex=0,added=0,restored=0,resolveCheck,options;const busy=[],messages=[],cleanups=[],deadline=new AbortController();
 const held=new Promise(resolve=>resolveCheck=resolve);
 const mocks={react:{useRef:x=>({current:x}),useState:x=>{const index=stateIndex++;return [index===0?{key:'/api/menu/pickup-addons?branchId=1:'+request,items:[suggestion]}:x,value=>{if(index===2)messages.push(value)}]},useEffect:fn=>{const cleanup=fn();if(cleanup)cleanups.push(cleanup)}},'react/jsx-runtime':jsx,'next/image':{default:()=>null},'@/lib/language':{T:()=>null,useTranslation:()=>x=>x},'@/lib/checkoutRefresh':{CheckoutUpdateUncertainError},'@/hooks/useCart':{useCart:()=>({items,addItem:()=>{added++;return 'added'}})},'@/services/apiClient':{apiClient:(path,value)=>{if(!path.includes('/check?'))return Promise.resolve([suggestion]);options=value;return Promise.race([held,new Promise((_,reject)=>value.signal?.addEventListener('abort',()=>reject(new DOMException('Aborted','AbortError')),{once:true}))])}},'@/lib/cartStorage':{getCartSnapshot:()=> 'same-cart',parseCart:()=>({items}),saveCart:()=>{restored++}},'@/lib/branchStorage':{getStoredBranchSnapshot:()=> 'same-branch'},'@/lib/checkoutStorage':{getPickupSlotSnapshot:()=> 'same-pickup'},'@/lib/pickupAddOnRebate':{usefulRebateTarget:()=>null},'@/lib/orderQuantity':{formatWeight:String},'./PickupAddOns.module.css':{default:{}}};
 const code=ts.transpileModule(fs.readFileSync('components/checkout/PickupAddOns.tsx','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022,jsx:ts.JsxEmit.ReactJSX}}).outputText;
 const box={exports:{},AbortController,AbortSignal:{any:AbortSignal.any,timeout:()=>deadline.signal},require:id=>{if(!(id in mocks))throw Error(id);return mocks[id]}};vm.runInNewContext(code,box);
 const tree=box.exports.default({branchId:1,date:'2026-10-05',disabled:false,compact:true,onAdded,onBusy:value=>busy.push(value)});
 function find(node){if(!node||typeof node!=='object')return;if(node.props?.['aria-label']==='Add Tea')return node;for(const child of [node.props?.children].flat(Infinity)){const match=find(child);if(match)return match}}
 return {start:()=>find(tree).props.onClick(),unmount:()=>cleanups.forEach(fn=>fn()),changeContext:()=>cleanups[1](),expire:()=>deadline.abort(),resolve:()=>resolveCheck({orderable:true}),busy,messages,get added(){return added},get restored(){return restored},get signal(){return options?.signal}};
}
const flush=()=>new Promise(resolve=>setImmediate(resolve));
test('checkout addition cannot mutate a cart after its component is abandoned',async()=>{
 const f=fixture();f.start();assert.deepEqual(f.busy,[true]);f.unmount();f.resolve();await flush();assert.equal(f.added,0);assert.equal(f.signal.aborted,true);assert.equal(f.busy.at(-1),false,'the parent checkout must unlock even when the add-on unmounts');
});
test('changed checkout context aborts a pending addition and releases busy state',async()=>{
 const f=fixture();f.start();f.changeContext();f.resolve();await flush();assert.equal(f.added,0);assert.deepEqual(f.busy,[true,false]);
});
test('addition deadline preserves the cart and exposes a retryable error',async()=>{
 const f=fixture();f.start();f.expire();await flush();assert.equal(f.added,0);assert.deepEqual(f.busy,[true,false]);assert.match(f.messages.at(-1),/Try again/);
});

test("the addition’s own cart change does not cancel quote-refresh error recovery",async()=>{
 let rejectRefresh;const refresh=new Promise((_,reject)=>rejectRefresh=reject);const f=fixture(()=>refresh);f.start();f.resolve();await flush();assert.equal(f.added,1);f.changeContext();assert.equal(f.signal.aborted,false);rejectRefresh(new Error("Quote failed"));await flush();assert.equal(f.restored,1);assert.equal(f.busy.at(-1),false);
});

test('a stalled quote refresh has its own deadline, rolls back and unlocks checkout',async()=>{
 let refreshSignal;
 const f=fixture(signal=>{refreshSignal=signal;return new Promise((_,reject)=>signal.addEventListener('abort',()=>reject(new DOMException('Timed out','TimeoutError')),{once:true}));});
 f.start();f.resolve();await flush();assert.equal(f.added,1);assert.ok(refreshSignal);f.expire();await flush();assert.equal(f.restored,1);assert.equal(f.busy.at(-1),false);assert.match(f.messages.at(-1),/Try again/);
});
test('an uncertain server update retains the addition and releases busy state for recheck',async()=>{
 const f=fixture(()=>Promise.reject(new CheckoutUpdateUncertainError()));f.start();f.resolve();await flush();assert.equal(f.added,1);assert.equal(f.restored,0);assert.equal(f.busy.at(-1),false);assert.match(f.messages.at(-1),/Recheck the current cart and total/);
});
