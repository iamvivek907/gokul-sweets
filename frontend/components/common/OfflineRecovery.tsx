"use client";
import {useSyncExternalStore} from "react";
import {useRouter} from "next/navigation";
import CustomerRecovery from "./CustomerRecovery";
import {useOnlineStatus} from "@/hooks/useOnlineStatus";
import {useSelectedBranch} from "@/hooks/useSelectedBranch";
import {T} from "@/lib/language";
const subscribe=()=>()=>{};
const clientSnapshot=()=>true,serverSnapshot=()=>false;
export default function OfflineRecovery(){
 const router=useRouter();
 const hydrated=useSyncExternalStore(subscribe,clientSnapshot,serverSnapshot),connected=useOnlineStatus(),{branch}=useSelectedBranch();
 const online=hydrated&&connected;
 return <main><CustomerRecovery title={online?"You’re connected again":"You’re offline"} message={online?"Your saved cart is kept. Retry the page to check the latest menu and pickup times.":"Your cart is saved on this device. Reconnect to check current stock, place an order or pay."} disabled={!online} onRetry={()=>{if(location.pathname==="/offline")router.replace("/menu");else location.reload();}}/>{branch?.phone&&<p className="offline-branch-help"><T text="Contact your selected branch"/> <a href={`tel:${branch.phone.replace(/[^+\d]/g,"")}`}><T text="Call branch"/> · {branch.name}</a></p>}</main>;
}
