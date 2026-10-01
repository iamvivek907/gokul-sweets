const assert=require('node:assert/strict'),test=require('node:test'),fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript');
function load(name){const exports={};vm.runInNewContext(ts.transpileModule(fs.readFileSync(`lib/${name}.ts`,'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText,{exports});return exports;}
const {groupMenuProducts}=load('menuGroups'),{rankAddOns}=load('addOnRanking');
test('category sections retain backend order, descriptions and product order after filtering',()=>{
 const categories=[{id:2,name:'Meals',description:'Lunch',products:[]},{id:1,name:'Sweets',description:'Mithai',products:[]},{id:3,name:'Drinks',products:[]}];
 const products=[{id:20,categoryId:2},{id:10,categoryId:1},{id:21,categoryId:2}];
 const groups=groupMenuProducts(categories,products);assert.deepEqual(Array.from(groups,g=>g.name),['Meals','Sweets']);assert.equal(groups[0].description,'Lunch');assert.deepEqual(Array.from(groups[0].products,p=>p.id),[20,21]);assert.equal(groupMenuProducts(categories,[products[1]]).length,1);assert.equal(products.length,3);
});
test('add-on ranking uses genuine portions, closest reaching addition first, then closest below',()=>{
 const items=[{id:1,portionTotal:36.75},{id:2,portionTotal:315},{id:3,portionTotal:147},{id:4,portionTotal:78.75}];
 const ranked=rankAddOns(items,143);assert.deepEqual(Array.from(ranked,x=>x.id),[3,2,4,1]);assert.equal(ranked[0],items[2]);assert.equal(items[0].id,1);assert.equal(rankAddOns(items,null),items);assert.equal(rankAddOns(items,0),items);
});
