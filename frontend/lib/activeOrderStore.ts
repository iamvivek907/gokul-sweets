import {getVerifiedOrderPage} from "@/services/orderApi";
import {ApiError} from "@/services/apiClient";
import {subscribeCustomerIdentityChanges} from "@/lib/customerIdentityEvents";
import type {CustomerOrderSummaryResponse} from "@/types/order";

type Snapshot={order:CustomerOrderSummaryResponse|null;stale:boolean};
const empty:Snapshot={order:null,stale:false};
let snapshot=empty,controller:AbortController|null=null,timer:ReturnType<typeof setTimeout>|null=null,generation=0;
const listeners=new Set<()=>void>();
let cleanup:(()=>void)|null=null;
const active=new Set(["PENDING_PAYMENT","CONFIRMED","PREPARING","READY_FOR_PICKUP","READY_FOR_DELIVERY","OUT_FOR_DELIVERY"]);
function publish(value:Snapshot){snapshot=value;listeners.forEach(listener=>listener());}
function stop(){generation++;controller?.abort();controller=null;if(timer)clearTimeout(timer);timer=null;}
function visible(){return document.visibilityState==="visible"&&navigator.onLine;}
async function refresh(){
 if(controller||!listeners.size||!visible())return;
 const request=new AbortController(),revision=generation;controller=request;
 try{
  const result=await getVerifiedOrderPage(null,AbortSignal.any([request.signal,AbortSignal.timeout(8000)]),{limit:"1",status:"ACTIVE"});
  if(revision!==generation||request.signal.aborted)return;
  publish({order:result.orders.find(order=>active.has(order.orderStatus))??null,stale:false});
 }catch(error){
  if(revision!==generation||request.signal.aborted)return;
  if(error instanceof ApiError&&[401,403,404].includes(error.status))publish(empty);
  else if(snapshot.order)publish({...snapshot,stale:true});
 }finally{
  if(controller===request){controller=null;if(listeners.size&&visible())timer=setTimeout(()=>{timer=null;void refresh();},snapshot.order?15000:60000);}
 }
}
/** Only one summary read per visible interval; memory is cleared on logout and last unmount. */
export function subscribeActiveOrder(listener:()=>void){
 listeners.add(listener);
 if(listeners.size===1){
  const resume=()=>{stop();if(!visible()){if(snapshot.order)publish({...snapshot,stale:true});return;}void refresh();};
  const identity=()=>{stop();publish(empty);void refresh();};
  const unsubscribe=subscribeCustomerIdentityChanges(identity,{revalidateOnResume:false});
  document.addEventListener("visibilitychange",resume);window.addEventListener("online",resume);window.addEventListener("offline",resume);
  cleanup=()=>{unsubscribe();document.removeEventListener("visibilitychange",resume);window.removeEventListener("online",resume);window.removeEventListener("offline",resume);};
  void refresh();
 }
 return()=>{listeners.delete(listener);if(!listeners.size){stop();cleanup?.();cleanup=null;snapshot=empty;}};
}
export function getActiveOrder(){return snapshot;}
export function getServerActiveOrder(){return empty;}
