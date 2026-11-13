const test=require("node:test"),assert=require("node:assert/strict"),vm=require("node:vm"),fs=require("node:fs");
const worker=fs.readFileSync("public/sw.js","utf8");
const origin="https://gokulsweets.in";
const boot='<html><body><div data-gokul-storefront-boot=""><span>Opening Gokul</span><a href="">Try again</a><span data-gokul-launch-ready="" hidden=""></span></div>';
const htmlResponse=body=>new Response(body,{headers:{"content-type":"text/html"}});
function fixture(fetch,caches={},onMessage) {
 const handlers=new Map(),timers=new Map();
 let next=0,now=0;
 vm.runInNewContext(worker,{
  self:{location:{origin},skipWaiting(){},clients:{get:async id=>({postMessage:message=>onMessage?.(id,message)})},addEventListener:(name,handler)=>handlers.set(name,handler)},
  fetch,caches,URL,Response,Request,AbortController,crypto,TextDecoder,
  setTimeout:(callback,ms)=>{const id=++next;timers.set(id,{callback,ms,at:now+ms});return id;},
  clearTimeout:id=>timers.delete(id)
 });
 return {timers,advance(ms){
  now+=ms;
  for(const [id,timer] of timers)if(timer.at<=now){timers.delete(id);timer.callback();}
 },install(){
  let done;
  handlers.get("install")({waitUntil:promise=>{done=promise;}});
  return done;
 },navigate(path,language="en-IN",method="GET"){
  let response;
  handlers.get("fetch")({resultingClientId:onMessage?"new-navigation":undefined,request:{url:new URL(path,origin).href,method,mode:"navigate",headers:new Headers({"Accept-Language":language})},
   respondWith:value=>{response=value;},waitUntil:promise=>{this.background=promise;}});
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
 assert.equal(ms,8000);
 callback();
 const response=await pending;
 assert.equal(signal.aborted,true);
 assert.equal(response.status,503);
 assert.equal(response.headers.get("cache-control"),"no-store");
 const html=await response.text();
 assert.match(html,/Let’s get you back to Gokul/);
 assert.match(html,/href="https:\/\/gokulsweets\.in\/menu\?from=home&amp;label=%22Gokul%22">Try again/);
 const nonce=html.match(/<script nonce="([^"]+)">/)[1];
 assert.match(response.headers.get("content-security-policy"),new RegExp("nonce-"+nonce));
 assert.doesNotMatch(html,/<script[^>]+src=|<link|<img/i);
 assert.match(html,/Reconnecting to Gokul/);
 assert.match(html,/prefers-reduced-motion:reduce/);
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

test("a page responding after five seconds still gets its normal launch",async()=>{
 let deliver;
 const f=fixture(()=>new Promise(resolve=>{deliver=resolve;}));
 const pending=f.navigate("/menu");
 assert.ok([...f.timers.values()].every(({ms})=>ms>5000));
 const app=new Response("Normal launch animation");
 deliver(app);
 assert.equal(await pending,app);
 assert.equal(f.timers.size,0);
});

test("headers without HTML do not clear the launch deadline",async()=>{
 let signal;
 const f=fixture((_request,options)=>{
  signal=options.signal;
  const body=new ReadableStream({start(controller){
   signal.addEventListener("abort",()=>controller.error(signal.reason),{once:true});
  }});
  return Promise.resolve(new Response(body));
 });
 const pending=f.navigate("/menu");
 await new Promise(resolve=>setImmediate(resolve));
 const [{callback,ms}]=f.timers.values();
 assert.equal(ms,8000);
 callback();
 assert.equal((await pending).status,503);
 assert.equal(signal.aborted,true);
 assert.equal(f.timers.size,0);
});

test("HTML head, scripts, and hidden streamed segments do not release a blank document",async()=>{
 let signal;
 const f=fixture((_request,options)=>{
  signal=options.signal;
  return Promise.resolve(new Response(new ReadableStream({start(controller){
   controller.enqueue(new TextEncoder().encode('<!doctype html><html><head><title>Gokul</title><script>const fake="<h1>Loaded</h1>";</script></head><body><!-- <span data-gokul-launch-ready="" hidden=""></span> --><script src="/app.js"></script><div hidden id="S:0"><h1>Streamed menu</h1></div>'));
   signal.addEventListener("abort",()=>controller.error(signal.reason),{once:true});
  }}),{headers:{"content-type":"text/html"}}));
 });
 const pending=f.navigate("/menu");
 await new Promise(resolve=>setImmediate(resolve));
 const [{callback,ms}]=f.timers.values();
 assert.equal(ms,8000);
 callback();
 assert.equal((await pending).status,503);
 assert.equal(signal.aborted,true);
});

test("a stalled stream cannot be released by the opening marker in a hidden ancestor",async()=>{
 const marker='<span data-gokul-launch-ready="" hidden=""></span>';
 const f=fixture(async(_request,{signal})=>new Response(new ReadableStream({start(controller){
  controller.enqueue(new TextEncoder().encode('<html><body><div hidden><section>'+marker+'</section></div>'));
  signal.addEventListener("abort",()=>controller.error(signal.reason),{once:true});
 }}),{headers:{"content-type":"text/html"}}));
 const pending=f.navigate("/menu");
 await new Promise(resolve=>setImmediate(resolve));
 const [{callback,ms}]=f.timers.values();
 assert.equal(ms,8000);
 callback();
 assert.equal((await pending).status,503);
});

test("completed hidden-only documents do not bypass recovery through the rollout fallback",async()=>{
 for(const body of [
  '<body><div hidden><h1>Streamed menu</h1></div></body>',
  '<body style="display:none"><h1>Previous menu</h1></body>',
  '<body><div><dialog><h1>Closed launch</h1></dialog></div></body>'
 ]){
  const f=fixture(async()=>new Response('<html>'+body+'</html>',{headers:{"content-type":"text/html"}}));
  assert.equal((await f.navigate("/menu")).status,503);
 }
});

test("an opening label without the rest of the boot screen keeps the deadline active",async()=>{
 const f=fixture(async(_request,{signal})=>new Response(new ReadableStream({start(controller){
  controller.enqueue(new TextEncoder().encode('<html><body><div class="gokul-mobile-boot"><span>Opening Gokul</span>'));
  signal.addEventListener("abort",()=>controller.error(signal.reason),{once:true});
 }}),{headers:{"content-type":"text/html"}}));
 const pending=f.navigate("/menu");
 await new Promise(resolve=>setImmediate(resolve));
 const [{callback,ms}]=f.timers.values();
 assert.equal(ms,8000);
 callback();
 const response=await pending;
 assert.equal(response.status,503);
 assert.match(await response.text(),/Reconnecting to Gokul/);
});

test("split HTML markup releases the original stream as soon as the opening screen arrives",async()=>{
 let stream;
 const app=new Response(new ReadableStream({start(controller){stream=controller;}}),{headers:{"content-type":"text/html","cache-control":"no-store"}});
 const f=fixture(async()=>app);
 const pending=f.navigate("/menu");
 const parts=['<!doctype html><html><head><ti','tle>Gokul</title></head><body><!','-- <h1>Hidden</h1> --','><script>const fake="<body>";</scr','ipt><div hidden><div><span data-gokul-launch-ready="" hidden=""></span></div></div><div class="gokul-mobile-boot"><span>Opening Gokul</span><div class="gokul-boot-recovery"><a href="">Try again</a></div><span data-gokul-launch-','ready="" hidden=""></span></div>'];
 for(const part of parts.slice(0,-1))stream.enqueue(new TextEncoder().encode(part));
 await new Promise(resolve=>setImmediate(resolve));
 assert.equal(f.timers.size,1);
 stream.enqueue(new TextEncoder().encode(parts.at(-1)));
 assert.equal(await pending,app);
 assert.equal(f.timers.size,0);
 stream.enqueue(new TextEncoder().encode("<p>Ready</p>"));
 stream.close();
 assert.equal(await app.text(),parts.join("")+"<p>Ready</p>");
});

test("a clean EOF before the retry link rejects the new opening screen and never caches it",async()=>{
 const incomplete='<html><body><div data-gokul-storefront-boot=""><span>Opening Gokul</span>';
 const html=()=>new Response(incomplete,{headers:{"content-type":"text/html"}});
 let writes=0,requests=0;
 const f=fixture(async()=>++requests===1?html():new Response("Unavailable",{status:503}),{
  open:async()=>({put:async()=>{writes++;}}),
  match:async(_request,{cacheName})=>cacheName==="gokul-pages-v3"&&requests>1?html():undefined
 });
 assert.equal((await f.navigate("/menu")).status,503);
 await f.background;
 assert.equal(writes,0);
 assert.equal((await f.navigate("/menu")).status,503,"reject a truncated page cached by an earlier worker");
});

test("opening markup renders immediately, but clean truncation cannot replace a complete offline page",async()=>{
 let stream,stored=boot+'<main>Saved menu</main></body></html>',offline=false,writes=0;
 const original=htmlResponse(new ReadableStream({start(controller){stream=controller;}}));
 const f=fixture(async()=>offline?new Response("Unavailable",{status:503}):original,{
  open:async()=>({put:async(_request,copy)=>{stored=await copy.text();writes++;}}),
  match:async()=>htmlResponse(stored)
 });
 const pending=f.navigate("/menu");
 stream.enqueue(new TextEncoder().encode(boot));
 assert.equal(await pending,original,"the live page must still stream before EOF");
 stream.enqueue(new TextEncoder().encode('<script>const ignored = "</body></html>";</script>'));
 stream.close();
 await f.background;
 assert.equal(writes,0);
 offline=true;
 assert.match(await (await f.navigate("/menu")).text(),/Saved menu/);
});

test("installation keeps the offline page and only precaches a complete Home document",async()=>{
 const complete=boot+"<main>Home</main></body></html>";
 for(const html of [boot,complete]){
  const added=[],stored=[];
  const f=fixture(async()=>htmlResponse(html),{
   open:async()=>({add:async path=>{added.push(path);},put:async(path,body)=>{stored.push([path,await body.text()]);}})
  });
  await f.install();
  assert.deepEqual(added,["/offline"]);
  assert.deepEqual(stored,html===complete?[["/",complete]]:[]);
 }
});

test("old partial cache entries fall back to branded recovery",async()=>{
 for(const markup of [boot,boot+'<main>Menu</main></body>',boot+'<!-- </body></html> -->']){
  const f=fixture(async()=>new Response("Unavailable",{status:503}),{
   match:async()=>htmlResponse(markup)
  });
  const response=await f.navigate("/menu");
  assert.equal(response.status,503);
  assert.match(await response.text(),/Reconnecting to Gokul/);
 }
});

test("complete streamed pages can be cached and replayed after an outage",async()=>{
 let stream,stored,offline=false;
 const original=htmlResponse(new ReadableStream({start(controller){stream=controller;}}));
 const f=fixture(async()=>offline?new Response("Unavailable",{status:503}):original,{
  open:async()=>({put:async(_request,copy)=>{stored=await copy.text();}}),
  match:async()=>htmlResponse(stored)
 });
 const pending=f.navigate("/menu");
 stream.enqueue(new TextEncoder().encode(boot));
 assert.equal(await pending,original);
 assert.equal(stored,undefined);
 for(const chunk of ["<main>Menu</main></bo","dy></ht","ml>"])stream.enqueue(new TextEncoder().encode(chunk));
 stream.close();
 await f.background;
 assert.equal(stored,boot+"<main>Menu</main></body></html>");
 assert.equal(f.timers.size,0);
 offline=true;
 assert.equal(await (await f.navigate("/menu")).text(),stored);
});

test("a complete HTML document without a launch marker still opens during rollout",async()=>{
 const app=new Response("<!doctype html><html><body><h1>Previous app build</h1></body></html>",{headers:{"content-type":"text/html"}});
 const f=fixture(async()=>app);
 assert.equal(await f.navigate("/menu"),app);
 await f.background;
 assert.equal(f.timers.size,0);
});

test("exact cached storefront pages remain usable during outages",async()=>{
 const cached=htmlResponse("<html><body><h1>Saved menu</h1></body></html>");
 const reads=[];
 const f=fixture(async()=>new Response("Unavailable",{status:503}),{match:async(request,{cacheName})=>{
  reads.push([request.url,cacheName]);
  return cached;
 }});
 assert.equal(await f.navigate("/menu?from=home"),cached);
 assert.deepEqual(reads,[[origin+"/menu?from=home","gokul-pages-v3"]]);
});

test("cache writes are opportunistic and a stalled cache read cannot hide recovery",async()=>{
 const html="<html><body><h1>New menu</h1></body></html>";
 const app=htmlResponse(html);
 const f=fixture(async()=>app,{open:async()=>({put:async(_request,copy)=>{
  assert.equal(await copy.text(),html);
  throw Error("Cache quota");
 }})});
 assert.equal(await f.navigate("/menu"),app);
 await f.background;

 const stalled=fixture(async()=>{throw TypeError("Offline");},{match:()=>new Promise(()=>{})});
 const pending=stalled.navigate("/menu");
 await new Promise(resolve=>setImmediate(resolve));
 const [{callback,ms}]=stalled.timers.values();
 assert.equal(ms,350);
 callback();
 assert.equal((await pending).status,503);
});

test("only the affected live navigation is notified of a clean truncation, body error or stall",async()=>{
 for(const failure of ["eof","error","stall","complete","oversize"]){
  let stream;
  const messages=[];
  const original=new Response(new ReadableStream({start(controller){stream=controller;}}),{
   headers:{"content-type":"text/html","cache-control":"no-store"}
  });
  const f=fixture(async()=>original,{},(id,message)=>messages.push([id,message.type]));
  const pending=f.navigate("/menu");
  stream.enqueue(new TextEncoder().encode(boot));
  assert.equal(await pending,original);
  if(failure==="error")stream.error(new Error("connection lost"));
  else if(failure==="stall"){
   const [{callback,ms}]=f.timers.values();
   assert.equal(ms,8000);
   callback();
  }else{
   if(failure==="oversize")stream.enqueue(new Uint8Array(2*1024*1024).fill(32));
   if(failure==="complete"||failure==="oversize")stream.enqueue(new TextEncoder().encode("<main>Menu</main></body></html>"));
   stream.close();
  }
  await f.background;
  assert.deepEqual(messages,["complete","oversize"].includes(failure)?[]:[["new-navigation","gokul-launch-stream-incomplete"]],failure);
  assert.equal(f.timers.size,0);
 }
});

test("live recovery waits for inactivity rather than elapsed load time",async()=>{
 for(const outcome of ["complete","stall"]){
  let stream,stored;
  const messages=[];
  const original=htmlResponse(new ReadableStream({start(controller){stream=controller;}}));
  const f=fixture(async()=>original,{open:async()=>({put:async(_request,page)=>{stored=await page.text();}})},
   (_id,message)=>messages.push(message.type));
  const pending=f.navigate("/menu");
  stream.enqueue(new TextEncoder().encode(boot));
  assert.equal(await pending,original);
  const flush=()=>new Promise(resolve=>setImmediate(resolve));
  await flush();
  for(let i=0;i<3;i++){
   f.advance(4000);
   stream.enqueue(new TextEncoder().encode("<p>More menu content</p>"));
   await flush();
   assert.deepEqual(messages,[],"a progressing response must not trigger recovery");
  }
  if(outcome==="complete"){
   stream.enqueue(new TextEncoder().encode("</body></html>"));
   stream.close();
  }else{
   f.advance(7900);
   stream.enqueue(new Uint8Array());
   await flush();
   assert.deepEqual(messages,[]);
   f.advance(100);
  }
  await f.background;
  if(outcome==="complete"){
   assert.deepEqual(messages,[]);
   assert.match(stored,/<p>More menu content<\/p><\/body><\/html>$/);
  }else{
   assert.deepEqual(messages,["gokul-launch-stream-incomplete"]);
   assert.equal(stored,undefined);
  }
  assert.equal(f.timers.size,0);
 }
});

function retryDocument(script,{state=null,type="navigate",denyState=false}={}){
 const timers=[],listeners={},classes=[];
 const history={state,replaceState(next){if(denyState)throw Error("Unavailable");this.state=next;}};
 const location={href:origin+"/menu?from=home",reload(){location.reloads++;},reloads:0};
 const document={addEventListener:(name,handler)=>{listeners[name]=handler;},documentElement:{classList:{add:value=>classes.push(value),remove:value=>classes.splice(classes.indexOf(value),1)}}};
 vm.runInNewContext(script,{history,location,document,performance:{getEntriesByType:()=>[{type}]},
  setTimeout:(handler,ms)=>{timers.push({handler,ms});return timers.length;},clearTimeout:id=>{timers[id-1].cleared=true;},
  addEventListener:(name,handler)=>{listeners[name]=handler;}});
 return {history,location,timers,classes,listeners};
}

test("two failed automatic reloads end in a manual retry without a reload loop",async()=>{
 const f=fixture(async()=>new Response("Unavailable",{status:503}));
 const html=await(await f.navigate("/menu?from=home")).text();
 const script=html.match(/<script nonce="[^"]+">([\s\S]+)<\/script>/)[1];
 const initial=retryDocument(script,{state:{unrelated:true}});
 assert.deepEqual(initial.classes,["retrying"]);
 assert.equal(initial.timers[0].ms,650);
 initial.timers[0].handler();
 assert.equal(initial.location.reloads,1);
 const second=retryDocument(script,{state:initial.history.state,type:"reload"});
 assert.equal(second.timers[0].ms,1200);
 second.timers[0].handler();
 assert.equal(second.location.reloads,1);
 const exhausted=retryDocument(script,{state:second.history.state,type:"reload"});
 assert.deepEqual(exhausted.classes,[]);
 assert.equal(exhausted.timers.length,0);
 exhausted.listeners.click({target:{closest:()=>true}});
 assert.equal(exhausted.history.state.gokulLaunchRetry,undefined);
 assert.equal(exhausted.history.state.unrelated,true);
 assert.equal(retryDocument(script,{state:exhausted.history.state}).timers.length,1);
 assert.equal(retryDocument(script,{denyState:true}).timers.length,0);
 second.listeners.pagehide();
 assert.equal(second.timers[0].cleared,true);
 second.listeners.pageshow({persisted:true});
 assert.deepEqual(second.classes,[]);
});

test("live retry shares the same history budget and ignores already hydrated pages",()=>{
 const ts=require("typescript"), startup={};
 new Function("exports",ts.transpileModule(fs.readFileSync("lib/pwaStartup.ts","utf8"),{
  compilerOptions:{module:ts.ModuleKind.CommonJS}
 }).outputText)(startup);
 function setup(state=null,type="navigate"){
  const attrs=new Map(),listeners={},timers=[],controller={};
  const screen={hasAttribute:key=>attrs.has(key),getAttribute:key=>attrs.get(key),setAttribute:(key,value)=>attrs.set(key,value)};
  let boot=screen,reloads=0;
  const history={state,replaceState(next){this.state=next;}};
  const location={pathname:"/menu",href:origin+"/menu",reload(){reloads++;}};
  vm.runInNewContext(startup.liveLaunchRecoveryScript,{
   location,history,navigator:{serviceWorker:{controller,addEventListener:(_name,fn)=>{listeners.message=fn;}}},
   document:{readyState:"complete",querySelector:()=>boot,addEventListener:()=>{}},
   performance:{getEntriesByType:()=>[{type}]},setTimeout:fn=>{timers.push(fn);},clearTimeout(){},addEventListener(){}
  });
  return {history,timers,attrs,removeBoot:()=>{boot=null;},reloads:()=>reloads,
   fail:source=>listeners.message({source:source??controller,data:{type:"gokul-launch-stream-incomplete"}})};
 }
 const first=setup({unrelated:true});
 first.fail({});
 assert.equal(first.timers.length,0);
 first.fail();first.fail();
 assert.equal(first.timers.length,1);
 assert.equal(first.history.state.unrelated,true);
 first.timers[0]();
 assert.equal(first.reloads(),1);
 const second=setup(first.history.state,"reload");
 second.fail();
 second.removeBoot();
 second.timers[0]();
 second.fail();
 assert.equal(second.reloads(),0);
 assert.equal(second.timers.length,1);
 const exhausted=setup(second.history.state,"reload");
 exhausted.fail();
 assert.equal(exhausted.attrs.get("data-live-launch-recovery"),"manual");
 assert.equal(exhausted.timers.length,0);
});
