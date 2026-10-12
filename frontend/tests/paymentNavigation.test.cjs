const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const ts=require('typescript');
function harness(){
    const listeners={},navigationListeners={},storage=new Map(),session=new Map(),replacements=[];
    let url=new URL('https://shop.example/orders/PAID-1');
    const location={get origin(){return url.origin;},get href(){return url.href;},get pathname(){return url.pathname;}};
    const history={state:{keep:true},replaceState(state,_title,path){assert.equal(state,history.state);url=new URL(path,url);replacements.push(url.pathname);}};
    const navigation={addEventListener(name,callback){navigationListeners[name]=callback;},removeEventListener(name,callback){if(navigationListeners[name]===callback)delete navigationListeners[name];}};
    const exports={};
    vm.runInNewContext(ts.transpileModule(fs.readFileSync('lib/paymentNavigation.ts','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText,{exports,URL,Map,location,history,localStorage:{getItem:key=>storage.get(key)??null},sessionStorage:{getItem:key=>session.get(key)??null},window:{navigation,addEventListener(name,callback){listeners[name]=callback;}}});
    return {api:exports,storage,session,replacements,navigationListeners,setPath(path){url=new URL(path,url);},back(path){url=new URL(path,url);let stopped=false;listeners.popstate({stopImmediatePropagation(){stopped=true;}});return stopped;}};
}
test('confirmed checkout stays guarded while cached page effects are deactivated',()=>{
    const h=harness();let leaves=0;
    const cleanup=h.api.guardCompletedCheckout('PAID-1',()=>leaves++);cleanup();
    assert.equal(h.back('/checkout/payment/PAID-1'),true);
    assert.equal(leaves,1);assert.deepEqual(h.replacements,['/orders']);
    assert.equal(h.back('/checkout/offers/PAID-1'),true);assert.equal(leaves,2);
    assert.equal(h.back('/menu'),false);assert.equal(h.back('/checkout/payment/UNPAID-2'),false);
});
test('a later cart or uncertain checkout retains ordinary Back recovery',()=>{
    const h=harness();let leaves=0;
    h.api.guardCompletedCheckout('PAID-1',()=>leaves++);
    for(const [store,key,value] of [[h.storage,'gokul-cart','{"items":[{"quantity":1}]}'],[h.storage,'gokul-pending-order','NEW'],[h.storage,'gokul-pending-payment','NEW'],[h.session,'gokul-mobile-order-attempt','NEW']]){
        store.set(key,value);assert.equal(h.back('/checkout/mobile'),false);store.delete(key);
    }
    assert.equal(leaves,0);assert.equal(h.back('/checkout/mobile'),true);assert.equal(leaves,1);
});
test('modern Back commits its previous entry before replacement instead of cancelling traversal',async()=>{
    const h=harness();let leaves=0,handler;
    h.api.guardCompletedCheckout('PAID-1',()=>leaves++);
    h.navigationListeners.navigate({navigationType:'traverse',canIntercept:true,destination:{url:'https://shop.example/checkout/payment/PAID-1'},intercept(options){handler=options.handler;},preventDefault(){throw new Error('Traversal must not be cancelled');}});
    assert.equal(leaves,0);assert.equal(typeof handler,'function');
    h.setPath('/checkout/payment/PAID-1');await handler();
    assert.equal(leaves,1);assert.deepEqual(h.replacements,['/orders']);
});
test('unconfirmed checkout continues to use its existing leave guard',()=>{
    const h=harness();let calls=0;
    const cleanup=h.api.guardPaymentBack(()=>calls++);
    assert.equal(h.back('/checkout/payment/UNPAID-2'),false);assert.equal(calls,1);
    cleanup();h.back('/menu');assert.equal(calls,1);
});
