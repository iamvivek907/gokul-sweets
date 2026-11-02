import {subscribeCustomerIdentityChanges} from "@/lib/customerIdentityEvents";

// Per document visit; never persist customer details or suppression across launches.
let dismissed=false,introduced=false,revision:string|null=null;
const listeners=new Set<()=>void>();
const publish=()=>listeners.forEach(fn=>fn());
export const reminderDismissed=()=>dismissed;
export const reminderServerDismissed=()=>false;
export function dismissOrderReminder(){dismissed=true;publish();}
export function claimOrderIntroduction(){if(introduced||dismissed)return false;introduced=true;return true;}
function readRevision(){try{return localStorage.getItem("gokul-customer-identity-revision");}catch{return null;}}
function reset(){dismissed=false;introduced=false;revision=readRevision();publish();}
export function subscribeOrderReminder(listener:()=>void){
 if(readRevision()!==revision)reset();
 listeners.add(listener);
 const unsubscribe=subscribeCustomerIdentityChanges(reset,{revalidateOnResume:false});
 return()=>{listeners.delete(listener);unsubscribe();};
}
