const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),ts=require('typescript'),vm=require('node:vm');
function setup({path='/menu',reduced=false,vibrate}={}){
 const calls=[],exports={};
 const sandbox={exports,window:{location:{pathname:path},matchMedia:()=>({matches:reduced})},navigator:{vibrate:vibrate===undefined?value=>{calls.push(value);return true;}:vibrate}};
 vm.runInNewContext(ts.transpileModule(fs.readFileSync('lib/menuHaptics.ts','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS}}).outputText,sandbox);
 return {tap:exports.menuTapFeedback,calls,sandbox};
}
test('menu taps emit one short pulse; other routes and reduced motion remain quiet',()=>{
 const menu=setup();menu.tap();assert.deepEqual(menu.calls,[12]);
 for(const options of [{path:'/'},{path:'/cart'},{path:'/checkout'},{reduced:true}]){const t=setup(options);t.tap();assert.deepEqual(t.calls,[]);}
});
test('unsupported, rejected and server-side feedback never breaks the action',()=>{
 for(const vibrate of [null,()=>false,()=>{throw Error('denied');}])assert.doesNotThrow(setup({vibrate}).tap);
 const t=setup();delete t.sandbox.window;assert.doesNotThrow(t.tap);assert.deepEqual(t.calls,[]);
});
