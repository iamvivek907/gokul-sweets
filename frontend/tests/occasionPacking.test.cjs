const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript');
const exportsObject={};vm.runInNewContext(ts.transpileModule(fs.readFileSync(require('node:path').join(__dirname,'../lib/occasionPacking.ts'),'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText,{exports:exportsObject,Math,Map,Number});
const {buildOccasionItems,packingError,indicativeFood,inclusiveRate,exceedsOccasionProductLimit}=exportsObject;
const products=[1,2,3,4,5].map(id=>({id,saleMode:id===2||id===3?'UNIT':'WEIGHT',unitPrice:100,taxPercent:5,pieceGrams:id===1?20:null}));
const boxes=[{id:8,capacityPieces:3},{id:9,capacityGrams:1000},{id:10,capacityGrams:500},{id:11,capacityGrams:250}];
const groups=[{kind:'MIXED',boxId:8,boxCount:1000,recipe:[1,2,3].map(productId=>({productId,pieces:1}))},...[1000,500,250].map((size,n)=>({kind:'WEIGHT',boxId:9+n,boxCount:n===2?20:10,packGrams:size,totalGrams:n===0?10000:5000,productId:5,recipe:[]}))];
test('one request calculates mixed pieces, paneer bulk kg and all three pack sizes without duplicate kg entry',()=>{const items=JSON.parse(JSON.stringify(buildOccasionItems(products,{4:100},{},groups)));assert.deepEqual(items,[{productId:1,quantity:1000,unit:'PIECE'},{productId:2,quantity:1000,unit:'PIECE'},{productId:3,quantity:1000,unit:'PIECE'},{productId:4,quantity:100000,unit:'GRAM'},{productId:5,quantity:20000,unit:'GRAM'}]);assert.equal(packingError(groups,items,boxes),null);});
test('same sweet can retain mixed-box pieces and separate kg packs',()=>{const items=buildOccasionItems(products,{}, {},groups.map(g=>g.kind==='WEIGHT'?{...g,productId:1}:g));assert.equal(items[0].quantity,1000);assert.equal(items[0].supplementalGrams,20000);});
test('reject partial packs, mismatched actual packaging and over-capacity mixed assortments',()=>{assert.match(packingError([{...groups[1],boxCount:1.5}],[],boxes),/whole boxes/);assert.match(packingError([{...groups[1],boxId:10}],[],boxes),/matching pack size/);assert.match(packingError([{...groups[0],recipe:[{productId:1,pieces:4}]}],[],boxes),/capacity/);});
test('price uses configured tax and measured piece weight, and leaves unknown sizing unresolved',()=>{assert.equal(inclusiveRate(products[0]),105);assert.equal(indicativeFood({quantity:1000,unit:'PIECE',supplementalGrams:20000},products[0]),4200);assert.equal(indicativeFood({quantity:1000,unit:'PIECE'},products[4]),null);});

test('the request limit counts distinct dishes across packages, packing and bulk quantities',()=>{
 const catalogue=Array.from({length:32},(_,index)=>({id:index+1,saleMode:'UNIT'}));
 const packageWith=(ids)=>({kind:'MIXED',boxCount:1,recipe:ids.map(productId=>({productId,pieces:1}))});
 const first=packageWith(catalogue.slice(0,16).map(p=>p.id));
 const overlapping=packageWith(catalogue.slice(14,30).map(p=>p.id));
 const disjoint=packageWith(catalogue.slice(16).map(p=>p.id));
 assert.equal(exceedsOccasionProductLimit(buildOccasionItems(catalogue,{1:3},{},[first,overlapping])),false);
 assert.equal(exceedsOccasionProductLimit(buildOccasionItems(catalogue,{31:1},{},[first,overlapping])),true);
 assert.equal(exceedsOccasionProductLimit(buildOccasionItems(catalogue,{}, {},[first,disjoint])),true);
});
