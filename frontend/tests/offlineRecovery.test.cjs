const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),ts=require('typescript'),React=require('react'),{renderToStaticMarkup}=require('react-dom/server');
function load(file,dependencies){const exports={};new Function('require','exports',ts.transpileModule(fs.readFileSync(file,'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,jsx:ts.JsxEmit.ReactJSX}}).outputText)(name=>dependencies[name]??require(name),exports);return exports;}
test('cached offline HTML stays honest before hydration even when the online hook has a true server default',()=>{
 const language={T:({text})=>text};
 const recovery=load('components/common/CustomerRecovery.tsx',{'@/lib/language':language,'next/link':{default:({children,...props})=>React.createElement('a',props,children)}});
 const component=load('components/common/OfflineRecovery.tsx',{'./CustomerRecovery':recovery,'@/lib/language':language,'next/navigation':{useRouter:()=>({replace(){}})},'@/hooks/useOnlineStatus':{useOnlineStatus:()=>true},'@/hooks/useSelectedBranch':{useSelectedBranch:()=>({branch:null})}});
 const html=renderToStaticMarkup(React.createElement(component.default));assert.match(html,/You’re offline/);assert.doesNotMatch(html,/You’re connected again/);assert.match(html,/<button[^>]+disabled/);
});
