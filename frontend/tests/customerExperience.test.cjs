const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript');
function load(path){const exports={};vm.runInNewContext(ts.transpileModule(fs.readFileSync(path,'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText,{exports,Intl,URL,Number,JSON});return exports;}
const presentation=load('lib/menuPresentationDetails.ts'),seo=load('lib/branchSeo.ts'),{safeVital}=load('lib/vitals.ts');
test('group ratings are weighted by review count and independent of photo, order and availability',()=>{
 const products=[{id:10,imageUrl:null,available:false},{id:11,imageUrl:'/full.webp',available:true}];
 const ratings={10:{productId:10,averageRating:5,ratingCount:100},11:{productId:11,averageRating:1,ratingCount:1},12:{productId:12,averageRating:1,ratingCount:900}};
 const result=presentation.groupedRatingSummary(products,ratings);
 assert.equal(result.ratingCount,101);assert.equal(result.averageRating,501/101);
 const changed=presentation.groupedRatingSummary([{...products[1],imageUrl:null,available:false},{...products[0],imageUrl:'/half.webp',available:true}],ratings);
 assert.equal(changed.ratingCount,result.ratingCount);assert.equal(changed.averageRating,result.averageRating);
 assert.equal(presentation.groupedRatingSummary([...products,products[0]],ratings).ratingCount,101);
 const precise=presentation.groupedRatingSummary(products,{10:{productId:10,averageRating:10/3,ratingCount:3},11:{productId:11,averageRating:5,ratingCount:1}});
 assert.equal(precise.averageRating,3.75);assert.equal(precise.averageRating.toFixed(1),'3.8');
});
test('group ratings tolerate unrated sizes and exclude malformed or unrelated summaries',()=>{
 const products=[{id:10},{id:11}];
 for(const ratings of [{},{10:{productId:10,averageRating:0,ratingCount:0}},{10:{productId:11,averageRating:5,ratingCount:1}}])assert.equal(presentation.groupedRatingSummary(products,ratings),null);
 assert.equal(presentation.groupedRatingSummary([],{}),null);
 for(const invalid of [{averageRating:NaN},{averageRating:Infinity},{averageRating:0},{averageRating:6},{ratingCount:0},{ratingCount:-1},{ratingCount:1.5},{ratingCount:NaN},{ratingCount:Infinity}]){
  const result=presentation.groupedRatingSummary(products,{10:{productId:10,averageRating:4,ratingCount:2},11:{productId:11,averageRating:5,ratingCount:1,...invalid}});
  assert.equal(result.ratingCount,2);assert.equal(result.averageRating,4);
 }
});
test('prices retain paise and only valid higher comparison prices claim a reduction',()=>{
 assert.equal(presentation.menuMoney(15),'₹15');assert.equal(presentation.menuMoney(15.5),'₹15.5');assert.equal(presentation.menuMoney(1234.56),'₹1,234.56');
 for(const value of [null,undefined,0,10,15,NaN,Infinity,'30'])assert.equal(presentation.comparisonPrice({price:15,comparisonPrice:value}),null);
 assert.equal(presentation.comparisonPrice({price:15,comparisonPrice:30}),30);assert.equal(presentation.comparisonPrice({price:NaN,comparisonPrice:30}),null);
});
test('product copy suppresses placeholders without replacing real ingredients or serving details',()=>{
 for(const description of [null,'',' Rasgulla ','Rasgulla. Served per piece.'])assert.equal(presentation.menuDescription({name:'Rasgulla',description}),null);
 assert.equal(presentation.menuDescription({name:'Rasgulla',description:' Soft chhena, lightly sweet syrup. '}),'Soft chhena, lightly sweet syrup.');
});
const branch={id:1,name:'Gokul Main',active:true,address:'Market Road',city:'Bhilai',state:'Chhattisgarh',pincode:'490001',phone:null,description:null,latitude:21.2,longitude:81.3};
test('branch structured data is specific, truthful and safe inside an inline script',()=>{
 const schema=seo.branchSchema(branch);assert.equal(schema['@type'],'Restaurant');assert.equal(schema.url,'https://gokulsweets.in/branches/1');assert.equal(schema.address.streetAddress,'Market Road');assert.equal(schema.geo.latitude,21.2);
 for(const key of ['aggregateRating','review','openingHours','telephone','image'])assert.equal(key in schema,false,key);
 const malicious=seo.branchSchema({...branch,name:'</script><script>alert(1)</script>',coverImageUrl:'javascript:alert(1)',latitude:Infinity});const serialized=seo.safeStructuredData(malicious);assert.ok(!serialized.includes('<'));assert.equal(JSON.parse(serialized).name,'</script><script>alert(1)</script>');assert.equal('image' in malicious,false);assert.equal('geo' in malicious,false);
 for(const change of [{active:false},{address:null},{name:''},{id:-1},{id:1.5},{id:Infinity}])assert.equal(seo.branchSchema({...branch,...change}),null);
 assert.equal(seo.branchPath(0),null);assert.match(seo.branchDescription(branch),/Bhilai/);
});
test('funnel events carry only allowlisted counters and coarse page names',()=>{
 for(const name of ['FUNNEL_MENU','FUNNEL_ADD','FUNNEL_CHECKOUT','FUNNEL_PAYMENT','FUNNEL_PAID','FUNNEL_FAILED','FUNNEL_EXPIRED']){
  const event=safeVital(name,1,'measured','/checkout/payment/PRIVATE-ORDER');assert.deepEqual(Object.keys(event).sort(),['name','page','rating','value']);assert.equal(event.page,'checkout');assert.ok(!JSON.stringify(event).includes('PRIVATE'));
  for(const value of [0,-1,2,NaN,Infinity])assert.equal(safeVital(name,value,'measured','/menu'),null);
  assert.equal(safeVital(name,1,'good','/menu'),null);
 }
 assert.equal(safeVital('FUNNEL_EMAIL_PRIVATE',1,'measured','/menu'),null);assert.equal(safeVital('MENU_RENDER',400,'measured','/menu').value,400);
});
