/** Optional reads publish independently, with at most two requests in flight. */
export async function progressiveBatches<T,R>(batches:T[],load:(batch:T)=>Promise<R>,publish:(value:R,batch:T)=>void,signal:AbortSignal):Promise<void>{
 let next=0;
 async function worker(){
  while(!signal.aborted){const index=next++;if(index>=batches.length)return;
   let value:R;try{value=await load(batches[index]);}catch{continue;}
   if(!signal.aborted)publish(value,batches[index]);
  }
 }
 await Promise.all([worker(),worker()]);
}
