const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const ts=require('typescript');
const React=require('react');
const {renderToStaticMarkup}=require('react-dom/server');
function load(file,dependencies={}){const exports={};const code=ts.transpileModule(fs.readFileSync(file,'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,jsx:ts.JsxEmit.ReactJSX}}).outputText;new Function('require','exports',code)(name=>dependencies[name]??require(name),exports);return exports;}
const dictionary=load('lib/hindi.ts');
const {T,translate}=load('lib/language.tsx',{'./hindi':dictionary});
test('translated fragments preserve punctuation and inline values',()=>{
 const fragment=React.createElement(React.Fragment,null,'Choose another time',React.createElement(T,{text:'. Your cart is unchanged.'}));
 assert.equal(renderToStaticMarkup(fragment),'Choose another time. Your cart is unchanged.');
 assert.equal(renderToStaticMarkup(React.createElement(T,{text:'Pickup'})),'Pickup');
 const fee=React.createElement(React.Fragment,null,React.createElement(T,{text:'(including'}),' ₹5 ',React.createElement(T,{text:'fee tax).'}));
 assert.equal(renderToStaticMarkup(fee),'(including ₹5 fee tax).');
});
test('Hindi translates plural items, no-slot CTA and staff preparation actions',()=>{
 for(const text of ['items','Choose time','Needs preparation','Enable kitchen alarm','Your name (optional)'])assert.notEqual(translate(text,'hi'),text);
 assert.equal(translate('items','hi'),'वस्तुएँ');
 assert.equal(translate('Choose time','hi'),'समय चुनें');
});
