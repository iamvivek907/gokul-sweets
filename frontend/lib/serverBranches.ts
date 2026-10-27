import {cache} from "react";
import {API_BASE_URL} from "@/lib/constants";
import {branchPath} from "@/lib/branchSeo";
import type {Branch} from "@/types/branch";
/** Public data only. A bounded lookup must not prevent the customer page from rendering. */
export const serverBranches=cache(async ():Promise<Branch[]>=>{
    try {
        const response=await fetch(`${API_BASE_URL}/api/branches`,{next:{revalidate:3600},signal:AbortSignal.timeout(2000)});
        if(!response.ok)return [];
        const data:unknown=await response.json();
        if(!Array.isArray(data))return [];
        return data.filter((b):b is Branch=>!!b&&b.active===true&&typeof b.name==="string"&&!!b.name.trim()&&branchPath(b.id)!==null);
    }catch{return [];}
});
