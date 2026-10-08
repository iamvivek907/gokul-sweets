const assert=require('node:assert/strict'),test=require('node:test'),fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript');
const React=require('react'),{renderToStaticMarkup}=require('react-dom/server');
const exportsOfCard={};
vm.runInNewContext(ts.transpileModule(fs.readFileSync('components/menu/MobilePortionCard.tsx','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022,jsx:ts.JsxEmit.ReactJSX}}).outputText,{
 exports:exportsOfCard,Intl,require:id=>id==='react'?React:id==='react/jsx-runtime'?require(id):id==='@/lib/language'?{T:({text})=>text}:id.endsWith('ProductCard')?{default:({product,purchaseControl})=>React.createElement('article',null,product.description,purchaseControl)}:{default:()=>null}
});
const group={key:'paratha',title:'Paratha',choices:[{productId:1,label:'Half'},{productId:2,label:'Full'}]};
const products=[{id:1,name:'Half Paratha',price:50,available:true},{id:2,name:'Full Paratha',price:90,available:true}];
const props={group,products,quantities:{},ratings:{},loading:false,dateAware:true};
const render=extra=>renderToStaticMarkup(React.createElement(exportsOfCard.default,{...props,...extra}));
test('a completely unavailable group exposes each server service reason without opening its disabled picker',()=>{
 const pickupItems=[{productId:1,available:false,code:'OUTSIDE_SERVICE',reason:'Half service starts at 11 AM.'},{productId:2,available:false,code:'DEPENDENCY_UNAVAILABLE',reason:'Full service starts at noon.'}];
 const html=render({pickupItems});
 assert.match(html,/<strong>Half:<\/strong> Half service starts at 11 AM\./);
 assert.match(html,/<strong>Full:<\/strong> Full service starts at noon\./);
 assert.match(html,/disabled=""/);
 assert.equal((html.match(/role="status"/g)||[]).length,2);
});
test('mixed groups label only the unavailable size; failed previews, manual unavailability and flag-off do not imply bookable hours',()=>{
 const pickupItems=[{productId:1,available:false,code:'OUTSIDE_SERVICE',reason:'Half service starts at 11 AM.'},{productId:2,available:true}];
 const html=render({pickupItems});assert.match(html,/Half service starts at 11 AM/);assert.doesNotMatch(html,/<strong>Full:/);assert.doesNotMatch(html,/disabled=""/);
 for(const extra of [{pickupItems,pickupChecking:true},{pickupItems,dateAware:false},{pickupItems,products:products.map(p=>({...p,available:false}))}])assert.doesNotMatch(render(extra),/Half service starts at 11 AM/);
});
