const test=require('node:test'), assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
function runtime(){
 let now=1000000,calls=0,failStorage=false,failNetwork=false,size=100;
 const listeners={},stores=new Map();
 const bucket=name=>{if(!stores.has(name))stores.set(name,new Map());return stores.get(name);};
 const cache=name=>({async match(key){return bucket(name).get(key.url)?.clone();},async put(key,response){if(failStorage===true||failStorage==='metadata'&&name.includes('meta'))throw Error('quota');bucket(name).set(key.url,response.clone());},async delete(key){return bucket(name).delete(key.url);},async keys(){return [...bucket(name).keys()].map(url=>new Request(url));}});
 const caches={async open(name){return cache(name);},async keys(){return [...stores.keys()];},async delete(name){return stores.delete(name);}};
 class Clock extends Date{static now(){return now;}}
 const sandbox={self:{addEventListener(name,cb){listeners[name]=cb;},location:{origin:'https://gokul.test'},clients:{claim:async()=>{}}},caches,fetch:async()=>{calls++;if(failNetwork)throw Error('offline');return new Response(new Uint8Array(size),{headers:{'content-type':'image/webp'}});},URL,Request,Response,Headers,Promise,Date:Clock,Uint8Array,Set,Number,JSON};
 vm.runInNewContext(fs.readFileSync(require.resolve('../public/sw.js'),'utf8'),sandbox);
 async function image(name){const jobs=[];let value;const request=new Request('https://gokul.test/'+name);Object.defineProperty(request,'destination',{value:'image'});listeners.fetch({request,respondWith(p){value=p;},waitUntil(p){jobs.push(p);}});const response=await value;await response.arrayBuffer();await Promise.all(jobs);return response;}
 return {image,stores,listeners,get calls(){return calls;},set size(value){size=value;},set now(value){now=value;},set failStorage(value){failStorage=value;},set failNetwork(value){failNetwork=value;}};
}
test('image cache reuses fresh entries, refreshes expiry and respects entry bound',async()=>{
 const r=runtime();await r.image('one.webp');await r.image('one.webp');assert.equal(r.calls,1);
 r.now=1000000+7*86400000;await r.image('one.webp');assert.equal(r.calls,2);
 for(let i=0;i<130;i++)await r.image(i+'.webp');
 assert.equal(r.stores.get('gokul-images-v3').size,120);assert.equal(r.stores.get('gokul-image-meta-v3').size,120);
 assert.equal(r.stores.get('gokul-images-v3').has('https://gokul.test/one.webp'),false);
});
test('response byte and individual image limits hold, including missing content length',async()=>{
 const r=runtime();r.size=2*1024*1024;
 for(let i=0;i<14;i++)await r.image(i+'.webp');
 assert.equal(r.stores.get('gokul-images-v3').size,12);
 r.size=2*1024*1024+1;await r.image('huge.webp');assert.equal(r.stores.get('gokul-images-v3').has('https://gokul.test/huge.webp'),false);
});
test('concurrent writes and quota failures preserve network rendering and storage limits',async()=>{
 const r=runtime();await Promise.all(Array.from({length:30},(_,i)=>r.image(i+'.webp')));
 assert.ok(r.stores.get('gokul-images-v3').size<=4,'write queue is bounded');
 r.failStorage=true;const response=await r.image('quota.webp');assert.equal(response.status,200);
 assert.equal(r.stores.get('gokul-images-v3').has('https://gokul.test/quota.webp'),false);
});
test('transactional API image requests bypass caching and activation removes old unbounded cache',async()=>{
 const r=runtime();let intercepted=false;
 r.listeners.fetch({request:{url:'https://gokul.test/api/private/image',method:'GET',destination:'image'},respondWith(){intercepted=true;}});
 assert.equal(intercepted,false);
 r.stores.set('gokul-images-v2',new Map());let pending;r.listeners.activate({waitUntil(p){pending=p;}});
 await pending;assert.equal(r.stores.has('gokul-images-v2'),false);
});

test('expired public artwork remains available offline and partial metadata writes roll back',async()=>{
 const r=runtime();await r.image('offline.webp');r.now=1000000+8*86400000;r.failNetwork=true;
 assert.equal((await r.image('offline.webp')).status,200);assert.equal(r.calls,2);
 r.failNetwork=false;r.failStorage='metadata';await r.image('partial.webp');
 assert.equal(r.stores.get('gokul-images-v3').has('https://gokul.test/partial.webp'),false);
 assert.equal(r.stores.get('gokul-image-meta-v3').has('https://gokul.test/partial.webp'),false);
});
