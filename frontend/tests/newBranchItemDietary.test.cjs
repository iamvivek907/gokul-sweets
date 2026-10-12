const assert=require('node:assert/strict'),test=require('node:test'),fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript');
const jsx=require('react/jsx-runtime');
function load(path,mocks={}){
 const box={exports:{},require:id=>{if(!(id in mocks))throw new Error(`Unexpected import: ${id}`);return mocks[id]}};
 vm.runInNewContext(ts.transpileModule(fs.readFileSync(path,'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022,jsx:ts.JsxEmit.ReactJSX}}).outputText,box);
 return box.exports;
}
const mobile=load('lib/mobileMenu.ts');
const GroupDietaryLabel=()=>null;
function groupOptions(products,highlightIds){
 let state=0;
 const component=load('components/menu/NewBranchItems.tsx',{
  react:{useState:initial=>[state++===0?{branchId:1,ids:highlightIds}:initial,()=>{}],useEffect:()=>{},useRef:()=>({current:null})},
  'react/jsx-runtime':jsx,'next/image':{default:()=>null},
  './DietaryLabel':{default:()=>null,GroupDietaryLabel},
  '@/lib/mobileMenu':mobile,'@/services/apiClient':{apiClient:()=>Promise.resolve({})},
  './NewBranchItems.module.css':{default:{}}
 }).default;
 const group={key:'packs',title:'Mixed packs',choices:[{productId:1,label:'Small'},{productId:9,label:'Large egg pack'}]};
 const tree=component({branch:{id:1,name:'Test'},products,portionGroups:[group],onSelect:()=>{}});
 function find(node){
  if(!node||typeof node!=='object')return null;
  if(node.type===GroupDietaryLabel)return node.props.products;
  for(const child of [node.props?.children].flat(Infinity)){const result=find(child);if(result)return result;}
  return null;
 }
 return Array.from(find(tree),p=>({id:p.id,vegetarian:p.vegetarian}));
}
const products=Array.from({length:9},(_,index)=>({id:index+1,name:`Pack ${index+1}`,available:true,vegetarian:index!==8}));
test('a highlighted Veg option retains its non-highlighted Non-veg group alternative',()=>{
 assert.deepEqual(groupOptions(products,[1]),[{id:1,vegetarian:true},{id:9,vegetarian:false}]);
});
test('the first-eight fallback retains a mixed option outside the highlighted subset',()=>{
 assert.deepEqual(groupOptions(products,[]),[{id:1,vegetarian:true},{id:9,vegetarian:false}]);
});
