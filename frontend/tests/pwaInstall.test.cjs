const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), vm = require('node:vm'), ts = require('typescript');
const source = ts.transpileModule(fs.readFileSync('lib/pwaInstall.ts', 'utf8'), {compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022}}).outputText;
function harness({ios=false, storage=new Map(), locks, standalone=false, width=390, storageBlocked=false}={}) {
 let time=1_800_000_000_000;const exported={};vm.runInNewContext(source,{exports:exported,Date:class extends Date{static now(){return time;}},Set,console});
 const events=new Map(),media=new Map(),on=(name,fn)=>events.set(name,[...(events.get(name)||[]),fn]);
 const browser={navigator:{userAgent:ios?'iPhone Safari':'Android Chrome',maxTouchPoints:1,standalone,...(locks?{locks}:{})},
  localStorage:{getItem(key){if(storageBlocked)throw Error('disabled');return storage.get(key)??null;},setItem(key,value){if(storageBlocked)throw Error('disabled');storage.set(key,value);}},
  addEventListener:on,document:{hasFocus:()=>true,visibilityState:'visible',addEventListener:on},
  matchMedia(query){if(!media.has(query))media.set(query,{matches:query.includes('max-width')?width<=640:false,listeners:[],addEventListener(_,fn){this.listeners.push(fn);}});return media.get(query);}};
 const store=exported.createPwaInstallStore();store.start(browser);const fire=(name,data={})=>{for(const fn of events.get(name)||[])fn(data);};
 function prompt({result='dismissed',pending=false,fails=false,choiceProperty=false}={}) {
  let calls=0,resolve;const choice=new Promise(done=>{resolve=done;});
  fire('beforeinstallprompt',{preventDefault(){},async prompt(){calls++;if(fails)throw Error('native failure');if(choiceProperty)return;return pending?choice:{outcome:result};},...(choiceProperty?{userChoice:choice}:{})});
  return {get calls(){return calls;},resolve:outcome=>resolve({outcome})};
 }
 return {store,browser,storage,events,media,fire,prompt,advance:ms=>{time+=ms;}};
}
function crossTabLocks(){let locked=false;return {async request(_,options,fn){assert.equal(options.ifAvailable,true);if(locked)return fn(null);locked=true;try{return await fn({});}finally{locked=false;}}};}
test('runtime deduplicates listeners and captures events without automatically prompting',async()=>{
 const h=harness();h.store.start(h.browser);assert.equal(h.events.get('beforeinstallprompt').length,1);assert.equal(h.store.getSnapshot().canInstall,false);
 const native=h.prompt();assert.equal(h.store.getSnapshot().canInstall,true);assert.equal(native.calls,0);await h.store.promptInstall();assert.equal(native.calls,1);assert.equal(h.store.getSnapshot().installationState,'cooldown');
});
test('two components consume one event, awaiting userChoice when prompt returns void',async()=>{
 const h=harness({locks:crossTabLocks()}),native=h.prompt({choiceProperty:true});const first=h.store.promptInstall();assert.equal(await h.store.promptInstall(),false);assert.equal(native.calls,1);assert.equal(h.store.getSnapshot().isInstallPromptAvailable,false);
 native.resolve('accepted');assert.equal(await first,true);assert.equal(h.store.getSnapshot().installationState,'accepted');await h.store.promptInstall();assert.equal(native.calls,1);
});
test('tabs share a lock and recheck persisted cooldown before consuming stale events',async()=>{
 const storage=new Map(),locks=crossTabLocks(),a=harness({storage,locks}),b=harness({storage,locks});const first=a.prompt({pending:true}),second=b.prompt();const pending=a.store.promptInstall();await b.store.promptInstall();assert.equal(second.calls,0);first.resolve('dismissed');await pending;
 await b.store.promptInstall();assert.equal(second.calls,0);assert.equal(b.store.getSnapshot().installationState,'cooldown');
});
test('failure consumes the native event without claiming success; blocked storage remains usable',async()=>{
 for(const storageBlocked of [false,true]){const h=harness({storageBlocked}),native=h.prompt({fails:true});assert.equal(await h.store.promptInstall(),false);assert.match(h.store.getSnapshot().error,/keep ordering/);assert.notEqual(h.store.getSnapshot().installationState,'installed');await h.store.promptInstall();assert.equal(native.calls,1);}
});
test('appinstalled during a pending prompt wins over its later result',async()=>{
 const h=harness(),native=h.prompt({pending:true}),pending=h.store.promptInstall();h.fire('appinstalled');native.resolve('dismissed');await pending;assert.equal(h.store.getSnapshot().installationState,'installed');assert.equal(h.store.getSnapshot().canInstall,false);h.prompt();assert.equal(h.store.getSnapshot().isInstallPromptAvailable,false);
});
test('iOS guide requires a tap and never claims installation; real standalone modes hide it',async()=>{
 const h=harness({ios:true});assert.equal(h.store.getSnapshot().guideOpen,false);await h.store.promptInstall();assert.equal(h.store.getSnapshot().guideOpen,true);assert.equal(h.store.getSnapshot().installationState,'available');h.store.closeGuide();assert.equal(h.store.getSnapshot().canInstall,true);
 const ios=harness({ios:true,standalone:true});assert.equal(ios.store.getSnapshot().canInstall,false);
 const android=harness();android.prompt();const mode=android.media.get('(display-mode: standalone)');mode.matches=true;mode.listeners.forEach(fn=>fn());assert.equal(android.store.getSnapshot().isStandalone,true);
});
test('stored installed markers are not proof; dismissal is shared and expires after seven days',()=>{
 const storage=new Map([['gokul-pwa-install-preferences-v1',JSON.stringify({installed:true})],['gokul-pwa-installed','true']]);const a=harness({ios:true,storage});assert.equal(a.store.getSnapshot().canInstall,true);a.store.dismiss();const b=harness({ios:true,storage});assert.equal(b.store.getSnapshot().installationState,'cooldown');b.advance(7*86_400_000+1);b.fire('focus');assert.equal(b.store.getSnapshot().canInstall,true);assert.notEqual(b.store.getSnapshot().installationState,'installed');
});
test('independent verification blockers suppress promotion and close instructions',async()=>{
 const h=harness({ios:true});await h.store.promptInstall();const a={},b={};h.store.blockPromotion(a,true);h.store.blockPromotion(b,true);assert.equal(h.store.getSnapshot().guideOpen,false);h.store.blockPromotion(a,false);assert.equal(h.store.getSnapshot().canInstall,false);h.store.blockPromotion(b,false);assert.equal(h.store.getSnapshot().canInstall,true);
});
test('repeated ignored visits reduce future promotion instead of increasing pressure',()=>{
 const storage=new Map();for(let i=0;i<3;i++){const h=harness({ios:true,storage});h.advance(i*86_400_000);h.fire('focus');h.store.recordBannerView();}const next=harness({ios:true,storage});next.advance(3*86_400_000);next.fire('focus');assert.equal(next.store.getSnapshot().canInstall,false);
});
test('canonical manifest and service-worker identity remain stable without forced reload',()=>{
 const manifest=fs.readFileSync('app/manifest.ts','utf8');for(const name of ['id','start_url','scope'])assert.match(manifest,new RegExp(`${name}: "\\/"`));assert.match(manifest,/display: "standalone"/);
 const registration=fs.readFileSync('components/pwa/ServiceWorkerRegistration.tsx','utf8');assert.match(registration,/"\/sw\.js"/);assert.match(registration,/scope: "\/"/);assert.doesNotMatch(registration,/location\.reload/);assert.match(fs.readFileSync('public/sw.js','utf8'),/"\/api\/"/);
});

test('unavailable cross-tab locking fails gracefully without leaving a dead prompt',async()=>{
 const h=harness({locks:{async request(){throw Error('Lock denied');}}});const native=h.prompt();assert.equal(await h.store.promptInstall(),false);assert.equal(native.calls,0);assert.equal(h.store.getSnapshot().isInstallPromptAvailable,false);assert.match(h.store.getSnapshot().error,/keep ordering/);assert.equal(h.store.getSnapshot().isPromptInProgress,false);
});

test('all declared install and push icons exist at their advertised PNG sizes',()=>{
 const manifest=fs.readFileSync('app/manifest.ts','utf8');
 for(const src of [...manifest.matchAll(/src: "([^"]+\.png[^"]*)"/g)].map(match=>match[1])){
  const path=src.split('?')[0];assert.match(src,/\?v=20261007$/);
  const bytes=fs.readFileSync('public'+path),size=path.includes('192')?192:512;
  assert.equal(bytes.toString('hex',0,8),'89504e470d0a1a0a');assert.equal(bytes.readUInt32BE(16),size);assert.equal(bytes.readUInt32BE(20),size);
 }
 assert.deepEqual(fs.readFileSync('public/icon-192.png'),fs.readFileSync('public/icons/icon-192.png'));
 const apple=fs.readFileSync('app/apple-icon.png');assert.equal(apple.toString('hex',0,8),'89504e470d0a1a0a');assert.equal(apple.readUInt32BE(16),180);assert.equal(apple.readUInt32BE(20),180);
});

test('existing image caches do not hide the versioned corrected install assets',async()=>{
 const handlers=new Map(),cached=new Map([['https://shop.example/icons/icon-192.png',new Response('old-icon')]]),requests=[];
 const cache={async match(request){return cached.get(request.url);},async put(request,response){cached.set(request.url,response);}};
 vm.runInNewContext(fs.readFileSync('public/sw.js','utf8'),{self:{location:{origin:'https://shop.example'},addEventListener(name,fn){handlers.set(name,fn);}},URL,Request,Response,caches:{async open(){return cache;}},async fetch(request){requests.push(request.url);return new Response('corrected-icon');}});
 let result;handlers.get('fetch')({request:{method:'GET',url:'https://shop.example/icons/icon-192.png?v=20261007',destination:'image'},respondWith(promise){result=promise;}});
 assert.equal(await (await result).text(),'corrected-icon');assert.deepEqual(requests,['https://shop.example/icons/icon-192.png?v=20261007']);
 assert.equal(await cached.get('https://shop.example/icons/icon-192.png').text(),'old-icon');
 let intercepted=false;handlers.get('fetch')({request:{method:'GET',url:'https://shop.example/api/orders/1',destination:''},respondWith(){intercepted=true;}});assert.equal(intercepted,false);
});
