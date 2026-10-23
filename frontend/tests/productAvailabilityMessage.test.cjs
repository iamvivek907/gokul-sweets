const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),ts=require('typescript'),React=require('react'),{renderToStaticMarkup}=require('react-dom/server');
const exportsForCard={};new Function('require','exports',ts.transpileModule(fs.readFileSync('components/menu/ProductCard.tsx','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,jsx:ts.JsxEmit.ReactJSX}}).outputText)(name=>name==='@/lib/language'?{T:({text})=>text}:name==='@/lib/menuPickupRecovery'?{requestMenuPickup(){}}:name==='./DietaryLabel'?{default:()=>null}:name==='next/image'?{default:()=>null}:require(name),exportsForCard);
const base={refined:true,unavailableForPickup:true,product:{id:1,name:'Rasgulla',price:40,available:true,saleMode:'UNIT'},ratingSummary:null,ratingLoading:false,quantity:0,weightGrams:null,onIncrease(){},onDecrease(){},onAdd(){}};
test('failed pickup checks remain visible once outside artwork without suggesting an unverified time',()=>{
 const html=renderToStaticMarkup(React.createElement(exportsForCard.default,{...base,pickupChecking:true,pickupMessage:'Pickup check failed. Please retry.'}));
 assert.equal(html.split('Pickup check failed. Please retry.').length-1,1);assert.match(html,/menu-availability-note/);assert.doesNotMatch(html,/Choose another pickup/);assert.match(html,/disabled/);
});
test('manual sold-out status takes precedence over dated lookup and is displayed only once',()=>{
 const html=renderToStaticMarkup(React.createElement(exportsForCard.default,{...base,product:{...base.product,available:false,serviceAvailability:{code:'SOLD_OUT',message:'Sold out'}},pickupMessage:'Not at this time'}));
 assert.equal(html.split('Sold out').length-1,1);assert.doesNotMatch(html,/Choose another pickup/);
});
