const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript');
const React=require('react'),{renderToStaticMarkup}=require('react-dom/server');
function load(path,requireFn){const exports={};vm.runInNewContext(ts.transpileModule(fs.readFileSync(path,'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022,jsx:ts.JsxEmit.ReactJSX}}).outputText,{exports,require:requireFn});return exports;}
const menu=load('lib/mobileMenu.ts',()=>{throw Error('Unexpected import');});
const Grid=load('components/menu/ProductGrid.tsx',id=>{
 if(id==='react')return React;if(id==='react/jsx-runtime')return require(id);
 if(id==='@/lib/language')return{T:({text})=>text};if(id==='@/lib/mobileMenu')return menu;
 if(id.endsWith('ProductCard')||id.endsWith('MobilePortionCard'))return{default:()=>React.createElement('article',null,'menu card')};
 if(id.endsWith('.module.css'))return{default:{}};
 if(id==='@/lib/storefrontPresentation')return{describePickupAvailability:()=>''};throw Error(id);
}).default;
const products=[{id:10,name:'Paneer Half',price:100,available:true},{id:11,name:'Paneer Full',price:180,available:false},{id:12,name:'Drink',price:40,available:true}];
const group={key:'paneer',title:'Paneer',choices:[{productId:10,label:'Half'},{productId:11,label:'Full'}]};
const props={products,catalogProducts:products,ratingSummaries:{},ratingsLoading:false,quantities:{},weights:{},onIncrease(){},onDecrease(){},onAdd(){}};
function ids(extra){const html=renderToStaticMarkup(React.createElement(Grid,{...props,...extra}));return [...html.matchAll(/id="(gokul-product-\d+)"/g)].map(m=>m[1]);}
test('phone and desktop grouping retain one anchor for every member, including unavailable sizes',()=>{
 for(const compactGroups of [true,false])assert.deepEqual(ids({portionGroups:[group],compactGroups}),['gokul-product-10','gokul-product-11','gokul-product-12']);
});
test('searching for a non-first size keeps both group destinations and omits unrelated products',()=>{
 assert.deepEqual(ids({products:[products[1]],portionGroups:[group],compactGroups:false}),['gokul-product-10','gokul-product-11']);
});
test('ungrouped and reversed size orders keep unique product anchors',()=>{
 assert.deepEqual(ids({}),['gokul-product-10','gokul-product-11','gokul-product-12']);
 assert.deepEqual(ids({portionGroups:[{...group,choices:[...group.choices].reverse()}]}),['gokul-product-11','gokul-product-10','gokul-product-12']);
});
