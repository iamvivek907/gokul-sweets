const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),ts=require('typescript');
function load(path){const out={};new Function('exports','require',ts.transpileModule(fs.readFileSync(path,'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText)(out,require);return out;}
const preferences=load('lib/menuPreferences.ts'),search=load('lib/menuSearch.ts'),details=load('types/menuDetails.ts'),menu=load('lib/mobileMenu.ts');
const storage=()=>{const data={};return {data,getItem:k=>data[k]??null,setItem:(k,v)=>{data[k]=v;}};};
test('favourites stay branch scoped, group changes are atomic, and capacity rejects without eviction',()=>{
 const s=storage(),key=preferences.favouriteKey;
 for(let i=1;i<=49;i++)assert.equal(preferences.toggleFavourites(s,1,[i]),'saved');
 assert.equal(preferences.toggleFavourites(s,1,[50,51]),'limit');assert.equal(preferences.favouriteIds(s.data[key],1).length,49);
 assert.equal(preferences.toggleFavourites(s,2,[99]),'saved');assert.equal(preferences.toggleFavourites(s,1,[49,50]),'saved');assert.equal(preferences.favouriteIds(s.data[key],1).length,50);
 assert.equal(preferences.toggleFavourites(s,1,[49,50]),'saved');assert.equal(preferences.favouriteIds(s.data[key],1).length,48);
 assert.equal(preferences.clearFavourites(s,1),true);assert.deepEqual(preferences.favouriteIds(s.data[key],2),[99]);
});
test('malformed and inaccessible preference storage cannot break browsing',()=>{
 for(const raw of ['null','[]','broken','{"1":[-1,0,2,2,"3",null]}'])assert.ok(preferences.favouriteIds(raw,1).length<=1);
 const blocked={getItem(){throw Error('Denied');},setItem(){throw Error('Denied');}};
 assert.equal(preferences.toggleFavourites(blocked,1,[1]),'unavailable');assert.equal(preferences.clearFavourites(blocked,1),false);assert.equal(preferences.rememberMenuSearch(blocked,1,'Paneer'),false);
 assert.equal(preferences.toggleFavourites(storage(),1,[]),'unavailable');assert.equal(preferences.toggleFavourites(storage(),0,[1]),'unavailable');
});
test('recent searches bound branches and entries, deduplicate case and clear only the selected branch',()=>{
 const s=storage();for(let branch=1;branch<=12;branch++)for(let n=0;n<10;n++)assert.equal(preferences.rememberMenuSearch(s,branch,`Dish ${n}`),true);
 assert.equal(Object.keys(JSON.parse(s.data[preferences.recentSearchKey])).length,10);assert.equal(preferences.recentSearches(s.data[preferences.recentSearchKey],12).length,8);
 preferences.rememberMenuSearch(s,12,'DISH 9');assert.equal(preferences.recentSearches(s.data[preferences.recentSearchKey],12).filter(x=>x.toLowerCase()==='dish 9').length,1);
 preferences.clearRecentSearches(s,12);assert.equal(preferences.recentSearches(s.data[preferences.recentSearchKey],12).length,0);assert.ok(preferences.recentSearches(s.data[preferences.recentSearchKey],11).length);
});
test('search handles Hindi, spelling and transpositions locally without broadening short unrelated words',()=>{
 const p={name:'Paneer Samosa',categoryName:'Snacks'};for(const q of ['पनीर','समोसा','panner','pnaeer','paneer samsoa'])assert.equal(search.matchesMenuSearch(p,q),true,q);
 for(const q of ['tea','fish','x'.repeat(10000)])assert.equal(search.matchesMenuSearch(p,q),false,q.slice(0,10));
 assert.deepEqual(search.menuSearchSuggestions([p,{name:'Paneer Roll'}],'pnaeer'),['Paneer Roll','Paneer Samosa']);
});
test('optional facts require bounded truthful field values and safe focal points',()=>{
 const valid=details.emptyMenuDetails(1);assert.equal(details.isMenuDetails(valid),true);assert.equal(details.dishPhotoPosition(valid),'50% 50%');
 for(const change of [{photoFocusX:101},{photoFocusY:NaN},{ingredients:'x'.repeat(601)},{spiceLevel:'INVENTED'},{version:-1},{productId:0}])assert.equal(details.isMenuDetails({...valid,...change}),false);
 assert.equal(details.dishPhotoPosition({...valid,photoFocusX:NaN,photoFocusY:-1}),'50% 50%');
});
test('campaign placement never splits a size group or duplicates a SKU',()=>{
 const products=Array.from({length:8},(_,i)=>({id:i+1,name:`Dish ${i+1}`,available:true})),groups=[{key:'one',title:'One',choices:[{productId:1,label:'Small'},{productId:8,label:'Large'}]}];
 const split=menu.splitMenuLead(products,groups);assert.ok(split.lead.some(p=>p.id===8));assert.deepEqual([...split.lead,...split.rest].map(p=>p.id).sort((a,b)=>a-b),products.map(p=>p.id));
});
