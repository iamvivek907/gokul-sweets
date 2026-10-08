"use client";
import Link from "next/link";
import {useEffect,useRef,useState} from "react";
import {useAdminAuth} from "@/contexts/AdminAuthContext";
import {adminManagementApi} from "@/services/adminManagementApi";
import {SavedOwnerKey,type OwnerResult} from "@/components/admin/OwnerAccessForm";
export default function AccountSecurityPage(){
    const {authorization,profile}=useAdminAuth();
    const [account,setAccount]=useState<{username:string;hasRecoveryKey:boolean}|null>(null),[username,setUsername]=useState(""),[password,setPassword]=useState(""),[code,setCode]=useState("");
    const [error,setError]=useState(""),[busy,setBusy]=useState(false),[result,setResult]=useState<OwnerResult|null>(null);
    const [loading,setLoading]=useState(true),[loadAttempt,setLoadAttempt]=useState(0);
    const operation=useRef<AbortController|null>(null),owner=profile?.roleName==="OWNER_ADMIN";
    useEffect(()=>{
        if(!authorization||!owner)return;
        const controller=new AbortController();operation.current=controller;
        adminManagementApi<{username:string;hasRecoveryKey:boolean}>("/api/admin/account-security",authorization,{signal:AbortSignal.any([controller.signal,AbortSignal.timeout(15000)])})
            .then(value=>{if(!controller.signal.aborted){setAccount(value);setUsername(value.username);}})
            .catch(()=>{if(!controller.signal.aborted)setError("Unable to load account security. Try again.");})
            .finally(()=>{if(operation.current===controller){operation.current=null;if(!controller.signal.aborted)setLoading(false);}});
        return()=>{controller.abort();operation.current?.abort();operation.current=null;};
    },[authorization,owner,loadAttempt]);
    async function action(kind:"recovery-key"|"username"){
        if(!authorization||operation.current||!account)return;
        const controller=new AbortController();operation.current=controller;setBusy(true);setError("");setResult(null);
        try{
            const value=await adminManagementApi<OwnerResult|undefined>(`/api/admin/account-security/${kind}`,authorization,{method:"POST",signal:AbortSignal.any([controller.signal,AbortSignal.timeout(30000)]),headers:{"Content-Type":"application/json"},body:JSON.stringify({username,password,code})});
            if(controller.signal.aborted)return;
            setPassword("");setCode("");
            // The server has revoked every session. A full navigation clears the old auth context
            // even if a logout endpoint is unreachable after the credential change.
            if(kind==="username"){window.location.replace("/admin/login");}
            else if(value){setResult(value);setAccount({...account,hasRecoveryKey:true});}
        }catch(failure){if(!controller.signal.aborted)setError(`${failure instanceof Error?failure.message:"Account action failed."} If the response was lost, sign in again to check your username, or generate a fresh recovery key with a new MFA code.`);}
        finally{if(operation.current===controller){operation.current=null;setBusy(false);}}
    }
    const input="mt-1 min-h-11 w-full rounded-xl border p-3";
    if(!owner)return <p className="p-6">Only owners can manage account recovery.</p>;
    return <main className="mx-auto max-w-xl space-y-5 p-4 sm:p-6"><h1 className="text-2xl font-bold">Account security</h1>
        <p>Change your own username or replace your offline recovery key. Existing staff accounts and roles stay unchanged.</p>
        {error&&<p role="alert" className="rounded-xl bg-red-50 p-3 text-red-800">{error}</p>}
        {account?<><p>Signed in as <strong>{account.username}</strong>. {account.hasRecoveryKey?"A recovery key is configured.":"No account recovery key has been generated yet."}</p>
            <fieldset disabled={busy} className="space-y-4 rounded-2xl border bg-white p-5">
                <label className="block">Current password<input className={input} type="password" autoComplete="current-password" value={password} onChange={e=>setPassword(e.target.value)} maxLength={128}/></label>
                <label className="block">Authenticator or MFA recovery code<input className={input} autoComplete="one-time-code" value={code} onChange={e=>setCode(e.target.value)} maxLength={64}/></label>
                <button type="button" disabled={!password||!code||busy} className="min-h-11 rounded-xl bg-[#7a1625] p-3 text-white disabled:opacity-50" onClick={()=>void action("recovery-key")}>Generate replacement recovery key</button>
                <p className="text-sm">This invalidates the previous account recovery key. Save the replacement offline before leaving.</p>
                <label className="block">New username<input className={input} autoComplete="username" value={username} onChange={e=>setUsername(e.target.value)} minLength={2} maxLength={100}/></label>
                <p className="text-sm">Changing your username signs out all of your devices. Sign in again with the new username, your existing password and MFA.</p>
                <button type="button" disabled={!password||!code||busy||username===account.username||username.trim().length<2} className="min-h-11 rounded-xl border p-3 disabled:opacity-50" onClick={()=>void action("username")}>Change username and sign out</button>
            </fieldset>{result&&<SavedOwnerKey result={result}/>}</>:loading?<p role="status">Loading account security…</p>:<button type="button" className="min-h-11 rounded-xl border p-3" onClick={()=>{if(operation.current)return;setError("");setLoading(true);setLoadAttempt(attempt=>attempt+1);}}>Retry</button>}
        <Link href="/admin/staff" className="inline-block underline">Staff management and password resets</Link>
    </main>;
}
