const test=require("node:test"),assert=require("node:assert/strict"),vm=require("node:vm"),fs=require("node:fs");
const worker=fs.readFileSync("public/sw.js","utf8");
const origin="https://gokulsweets.in";
function fixture(fetch,caches={}) {
 const handlers=new Map(),timers=new Map();
 let next=0;
 vm.runInNewContext(worker,{
  self:{location:{origin},addEventListener:(name,handler)=>handlers.set(name,handler)},
  fetch,caches,URL,Response,Request,AbortController,
  setTimeout:(callback,ms)=>{const id=++next;timers.set(id,{callback,ms});return id;},
  clearTimeout:id=>timers.delete(id)
 });
 return {timers,navigate(path,language="en-IN",method="GET"){
  let response;
  handlers.get("fetch")({request:{url:new URL(path,origin).href,method,mode:"navigate",headers:new Headers({"Accept-Language":language})},
   respondWith:value=>{response=value;}});
  return response;
 }};
}
test("fast storefront launch returns the current network document without waiting or using cached HTML",async()=>{
 const latest=new Response("<h1>New Gokul menu</h1>");
 const f=fixture(async()=>latest);
 for(const path of ["/","/menu","/branches","/branches/42?source=installed"]){
  assert.equal(await f.navigate(path),latest);
  assert.equal(f.timers.size,0);
 }
});
test("a stalled launch recovers by the deadline even without cached files",async()=>{
 let signal;
 const f=fixture((_request,options)=>{signal=options.signal;return new Promise(()=>{});});
 const pending=f.navigate("/menu?from=home&label=%22Gokul%22");
 assert.equal(f.timers.size,1);
 const [{callback,ms}]=f.timers.values();
 assert.equal(ms,4000);
 callback();
 const response=await pending;
 assert.equal(signal.aborted,true);
 assert.equal(response.status,503);
 assert.equal(response.headers.get("cache-control"),"no-store");
 const html=await response.text();
 assert.match(html,/Let’s get you back to Gokul/);
 assert.match(html,/href="https:\/\/gokulsweets\.in\/menu\?from=home&amp;label=%22Gokul%22">Try again/);
 assert.doesNotMatch(html,/<script|<link|<img/i);
 assert.equal(f.timers.size,0);
});
test("offline and server errors return usable recovery immediately, while expected HTTP statuses pass through",async()=>{
 const offline=fixture(async()=>{throw new TypeError("Offline");});
 const html=await (await offline.navigate("/","hi-IN,en;q=0.9")).text();
 assert.match(html,/<html lang="hi">/);
 assert.match(html,/फिर कोशिश करें/);
 assert.equal(offline.timers.size,0);
 const outage=fixture(async()=>new Response("Unavailable",{status:502}));
 assert.equal((await outage.navigate("/branches/2")).status,503);
 const missing=new Response("Not found",{status:404});
 assert.equal(await fixture(async()=>missing).navigate("/branches/2"),missing);
});
test("checkout and staff navigations keep their existing response behavior",async()=>{
 const original=new Response("Service unavailable",{status:503});
 const f=fixture(async()=>original,{open:async()=>({})});
 for(const path of ["/checkout/mobile","/orders","/admin","/offline"]){
  assert.equal(await f.navigate(path),original);
  assert.equal(f.timers.size,0);
 }
 assert.equal(f.navigate("/menu","en","POST"),undefined);
});
