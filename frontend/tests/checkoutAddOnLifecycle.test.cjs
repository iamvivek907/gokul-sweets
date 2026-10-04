const assert=require('node:assert/strict'),test=require('node:test'),fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript');
const jsx=require('react/jsx-runtime');
function fixture(){
 const tea={id:2,name:'Tea',available:true,saleMode:'UNIT',price:30,imageUrl:null};
 const items=[{product:{...tea,id:1},quantity:1,weightGrams:null}];
 const request=JSON.stringify({serviceDate:'2026-10-05',items:[{productId:1,quantity:1,weightGrams:null}]});
 const suggestion={product:tea,weightGrams:null,portionPrice:30,portionTotal:30,reason:'Pairing'};
 let stateIndex=0,added=0,resolveCheck,options;const busy=[],messages=[],cleanups=[],deadline=new AbortController();
 const held=new Promise(resolve=>resolveCheck=resolve);
 const mocks={react:{useRef:x=>({current:x}),useState:x=>{const index=stateIndex++;return [index===0?{key:'/api/menu/pickup-addons?branchId=1:'+request,items:[suggestion]}:x,value=>{if(index===2)messages.push(value)}]},useEffect:fn=>{const cleanup=fn();if(cleanup)cleanups.push(cleanup)}},'react/jsx-runtime':jsx,'next/image':{default:()=>null},'@/lib/language':{T:()=>null,useTranslation:()=>x=>x},'@/lib/addOnRanking':{rankAddOns:x=>x},'@/hooks/useCart':{useCart:()=>({items,addItem:()=>{added++;return 'added'}})},'@/services/apiClient':{apiClient:(path,value)=>{if(!path.includes('/check?'))return Promise.resolve([suggestion]);options=value;return Promise.race([held,new Promise((_,reject)=>value.signal?.addEventListener('abort',()=>reject(new DOMException('Aborted','AbortError')),{once:true}))])}},'@/lib/cartStorage':{getCartSnapshot:()=> 'same-cart',parseCart:()=>({items}),saveCart:()=>{}},'@/lib/branchStorage':{getStoredBranchSnapshot:()=> 'same-branch'},'@/lib/checkoutStorage':{getPickupSlotSnapshot:()=> 'same-pickup'},'@/lib/pickupAddOnRebate':{usefulRebateTarget:()=>null},'@/lib/orderQuantity':{formatWeight:String},'./PickupAddOns.module.css':{default:{}}};
 const code=ts.transpileModule(fs.readFileSync('components/checkout/PickupAddOns.tsx','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022,jsx:ts.JsxEmit.ReactJSX}}).outputText;
 const box={exports:{},AbortController,AbortSignal:{any:AbortSignal.any,timeout:()=>deadline.signal},require:id=>{if(!(id in mocks))throw Error(id);return mocks[id]}};vm.runInNewContext(code,box);
 const tree=box.exports.default({branchId:1,date:'2026-10-05',disabled:false,compact:true,onAdded:()=>{},onBusy:value=>busy.push(value)});
 function find(node){if(!node||typeof node!=='object')return;if(node.props?.['aria-label']==='Add Tea')return node;for(const child of [node.props?.children].flat(Infinity)){const match=find(child);if(match)return match}}
 return {start:()=>find(tree).props.onClick(),unmount:()=>cleanups.forEach(fn=>fn()),changeContext:()=>cleanups[1](),expire:()=>deadline.abort(),resolve:()=>resolveCheck({orderable:true}),busy,messages,get added(){return added},get signal(){return options?.signal}};
}
const flush=()=>new Promise(resolve=>setImmediate(resolve));
test('checkout addition cannot mutate a cart after its component is abandoned',async()=>{
 const f=fixture();f.start();assert.deepEqual(f.busy,[true]);f.unmount();f.resolve();await flush();assert.equal(f.added,0);assert.equal(f.signal.aborted,true);
});
test('changed checkout context aborts a pending addition and releases busy state',async()=>{
 const f=fixture();f.start();f.changeContext();f.resolve();await flush();assert.equal(f.added,0);assert.deepEqual(f.busy,[true,false]);
});
test('addition deadline preserves the cart and exposes a retryable error',async()=>{
 const f=fixture();f.start();f.expire();await flush();assert.equal(f.added,0);assert.deepEqual(f.busy,[true,false]);assert.match(f.messages.at(-1),/Try again/);
});
