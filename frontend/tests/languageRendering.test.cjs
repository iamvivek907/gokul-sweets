const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const ts=require('typescript');
const React=require('react');
const {renderToStaticMarkup}=require('react-dom/server');
function load(file,dependencies={}){const exports={};const code=ts.transpileModule(fs.readFileSync(file,'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,jsx:ts.JsxEmit.ReactJSX}}).outputText;new Function('require','exports',code)(name=>dependencies[name]??require(name),exports);return exports;}
const dictionary=load('lib/hindi.ts');
const customerIcon=load('components/customer/CustomerIcon.tsx');
const {T,translate}=load('lib/language.tsx',{'./hindi':dictionary,'@/components/customer/CustomerIcon':customerIcon});
test('translated fragments preserve punctuation and inline values',()=>{
 const fragment=React.createElement(React.Fragment,null,'Choose another time',React.createElement(T,{text:'. Your cart is unchanged.'}));
 assert.equal(renderToStaticMarkup(fragment),'Choose another time. Your cart is unchanged.');
 assert.equal(renderToStaticMarkup(React.createElement(T,{text:'Pickup'})),'Pickup');
 const fee=React.createElement(React.Fragment,null,React.createElement(T,{text:'(including'}),' ₹5 ',React.createElement(T,{text:'fee tax).'}));
 assert.equal(renderToStaticMarkup(fee),'(including ₹5 fee tax).');
});
test('Hindi translates plural items, no-slot CTA and staff preparation actions',()=>{
 for(const text of ['items','Choose time','Needs preparation','Enable kitchen alarm','Your name (optional)','All your Gokul moments, together.','The best available offer is applied automatically. Add a little extra below if you like, then continue to payment.'])assert.notEqual(translate(text,'hi'),text);
 assert.equal(translate('items','hi'),'वस्तुएँ');
 assert.equal(translate('Choose time','hi'),'समय चुनें');
 assert.match(translate('All your Gokul moments, together.','hi'),/ऑर्डर/);
 assert.match(translate('The best available offer is applied automatically. Add a little extra below if you like, then continue to payment.','hi'),/छूट/);
});

test('Hindi localizes dynamic add-on and kitchen copy while preserving the product name',()=>{
 assert.equal(translate('Often ordered with Kaju Katli','hi'),'Kaju Katli के साथ खूब पसंद किया जाता है');
 assert.match(translate('Laddu added. Review your updated price and offer before payment.','hi'),/^Laddu जोड़/);
 assert.equal(translate('2 started · 1 skipped. ','hi'),'2 ऑर्डर की तैयारी शुरू हुई · 1 ऑर्डर छोड़ दिए गए। ');
 assert.equal(translate('Often ordered with Kaju Katli','en'),'Often ordered with Kaju Katli');
});

test('link completion feedback leaves the link geometry unchanged',()=>{
 const {default:LinkFeedback}=load('components/common/LinkFeedback.tsx',{
  'next/link':{useLinkStatus:()=>({pending:true})},
  '@/lib/language':{useTranslation:()=>text=>text}
 });
 const markup=renderToStaticMarkup(React.createElement(LinkFeedback));
 assert.equal(markup,'');
});

test('menu pickup recovery instructions are available in Hindi',()=>{
 for(const text of ['Choose pickup time to add','Pickup check failed. Please retry.','Checking pickup','Not at this time','Choose a time, then confirm. Your cart is kept.']){
  assert.notEqual(translate(text,'hi'),text);
  assert.match(translate(text,'hi'),/[\u0900-\u097F]/);
  assert.equal(translate(text,'en'),text);
 }
});
