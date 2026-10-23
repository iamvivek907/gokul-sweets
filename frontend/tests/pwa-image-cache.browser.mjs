import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
const browser=await chromium.launch({headless:true});
try {
 const context=await browser.newContext({serviceWorkers:'allow'});
 await context.route('**/cache-probe',route=>route.fulfill({contentType:'text/html',body:'<!doctype html><title>Public image cache verification</title>'}));
 const page=await context.newPage();
 await page.goto(base+'/cache-probe');
 await page.evaluate(async()=>{
  const old=await caches.open('gokul-images-v2');await old.put('/legacy-image',new Response('old'));
  await navigator.serviceWorker.register('/sw.js');await navigator.serviceWorker.ready;
  if(!navigator.serviceWorker.controller)await new Promise(resolve=>navigator.serviceWorker.addEventListener('controllerchange',resolve,{once:true}));
  await new Promise((resolve,reject)=>{const image=new Image();image.onload=resolve;image.onerror=()=>reject(Error('Image did not render'));image.src='/icon-192.png?bounded-cache-check=1';document.body.append(image);});
 });
 await page.waitForFunction(async()=>!!await(await caches.open('gokul-image-meta-v3')).match('/icon-192.png?bounded-cache-check=1'));
 const result=await page.evaluate(async()=>{
  const image=await(await caches.open('gokul-images-v3')).match('/icon-192.png?bounded-cache-check=1');
  const meta=await(await caches.open('gokul-image-meta-v3')).match('/icon-192.png?bounded-cache-check=1');
  return {names:await caches.keys(),status:image.status,size:(await image.arrayBuffer()).byteLength,metadata:await meta.json()};
 });
 assert.equal(result.names.includes('gokul-images-v2'),false);
 assert.equal(result.status,200);assert.ok(result.size>0);assert.equal(result.metadata.size,result.size);
 assert.ok(result.metadata.storedAt>0);
 await context.close();console.log('Real service worker upgrades unbounded cache and stores bounded public image payload/metadata');
}finally{await browser.close();}
