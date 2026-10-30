const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript'),jsx=require('react/jsx-runtime');
function fixture({defer=false,explicit=true,prefer=true,connected=true}={}){
 const effects=[],frames=[],calls=[];const previous={isConnected:connected,focus:()=>{calls.push('previous');document.activeElement=previous;}};
 let blocked=defer;const target={isConnected:true,focus:()=>{calls.push('target');if(!blocked)document.activeElement=target;}};
 const document={activeElement:previous,body:{style:{}},documentElement:{style:{}},getElementById:()=>target};
 const surface={showModal:()=>{},close:()=>{}},exports={};const mocks={react:{useEffect:callback=>effects.push(callback),useId:()=> 'sheet',useRef:()=>({current:surface})},'react/jsx-runtime':jsx,'@/hooks/useStorefrontFeatures':{useStorefrontFeatures:()=>({futuristicStorefrontV2:true})},'@/hooks/useMenuSheetDismiss':{useMenuSheetDismiss:()=>()=>{}},'@/lib/language':{T:()=>null,useTranslation:()=>text=>text},'./MenuDiscoverySheet.module.css':{default:{}}};
 vm.runInNewContext(ts.transpileModule(fs.readFileSync('components/menu/MenuDiscoverySheet.tsx','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022,jsx:ts.JsxEmit.ReactJSX}}).outputText,{exports,require:id=>mocks[id],document,window:{scrollY:100,scrollX:0,scrollTo:()=>{}},requestAnimationFrame:fn=>frames.push(()=>{blocked=false;fn();})});
 exports.default({title:'Sizes',onClose:()=>{},restoreFocus:prefer?target:undefined,returnFocusId:explicit?'paired-size-trigger':undefined});const cleanup=effects[0]();cleanup();return {calls,frames,document,target,previous};
}
test('an explicit return target wins over an earlier connected overlay trigger',()=>{const f=fixture();assert.equal(f.calls[0],'target');assert.equal(f.document.activeElement,f.target);});
test('explicit target restoration retries after the closing modal releases focus',()=>{const f=fixture({defer:true});assert.equal(f.document.activeElement,f.previous);while(f.frames.length)f.frames.shift()();assert.equal(f.document.activeElement,f.target);assert.ok(f.calls.every(value=>value==='target'));});
test('sheets without an explicit return target preserve their previous focus',()=>{const f=fixture({explicit:false,prefer:false});assert.equal(f.document.activeElement,f.previous);assert.equal(f.calls[0],'previous');});

test('fallback IDs preserve an existing close trigger, including clear-cart cancellation',()=>{const f=fixture({prefer:false});assert.equal(f.document.activeElement,f.previous);assert.equal(f.calls[0],'previous');});
test('a fallback ID is used when clearing the cart removes its original trigger',()=>{const f=fixture({prefer:false,connected:false});assert.equal(f.document.activeElement,f.target);});
