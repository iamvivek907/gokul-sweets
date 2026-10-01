"use client";
import {useEffect,useState} from "react";
import {apiClient,ApiError} from "@/services/apiClient";
import {useTranslation} from "@/lib/language";
export default function PickupCodeCard({orderNumber}:{orderNumber:string}) {
 const t=useTranslation(),[code,setCode]=useState<string|null|undefined>(undefined),[error,setError]=useState(""),[retry,setRetry]=useState(0);
 useEffect(()=>{const controller=new AbortController();apiClient<{code:string|null}>(`/api/orders/${encodeURIComponent(orderNumber)}/pickup-code`,{credentials:"include",signal:controller.signal}).then(result=>{if(!controller.signal.aborted){setCode(result.code);setError("");}}).catch(error=>{if(!controller.signal.aborted)setError(error instanceof ApiError&&[401,403,404].includes(error.status)?"Sign in with the account used for this order to view the pickup code.":"Could not load your pickup code. Please retry.");});return()=>controller.abort();},[orderNumber,retry]);
 return <section aria-label={t("Pickup code")} className="mt-5 rounded-2xl border border-[#c4d4c9] bg-[#fffaf2] p-5 text-[#143936]"><h2 className="font-bold">{t("Your pickup code")}</h2>{code?<><p className="my-3 text-4xl font-bold tracking-[.35em]" aria-label={`${t("Pickup code")}: ${code.split("").join(" ")}`}>{code}</p><p className="text-sm">{t("Share these four digits with staff when collecting your order. Keep this code private until handover.")}</p></>:error?<><p role="alert" className="mt-3 text-sm">{t(error)}</p><button type="button" className="mt-3 min-h-11 rounded-xl border px-4" onClick={()=>setRetry(v=>v+1)}>{t("Retry")}</button></>:<p role="status" className="mt-3 text-sm">{t(code===undefined?"Loading pickup code…":"Pickup code is available for confirmed pickup orders.")}</p>}</section>;
}
