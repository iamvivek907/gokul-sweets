"use client";
import Link from "next/link";
import {useEffect,useRef,useState} from "react";
import {ADMIN_API_BASE_URL} from "@/lib/constants";
export interface OwnerResult {username:string;recoveryKey:string}
export function SavedOwnerKey({result}:{result:OwnerResult}) {
    const [saved,setSaved]=useState(false),[visible,setVisible]=useState(false),[copied,setCopied]=useState(false);
    return <section className="space-y-4 rounded-xl bg-green-50 p-4" aria-label="Save recovery key">
        <p role="status">Account ready for <strong>{result.username}</strong>. Save this single-use recovery key now.</p>
        <p className="text-sm">Store it offline. It can reset this owner’s username and password. Your authenticator is still required to sign in. A replacement key is issued each time recovery succeeds.</p>
        <label className="block text-sm font-semibold">Recovery key<input readOnly value={result.recoveryKey} type={visible?"text":"password"} autoComplete="off" className="mt-2 w-full rounded-lg border p-3 font-mono text-sm"/></label>
        <div className="flex flex-wrap gap-3"><button type="button" className="underline" onClick={()=>setVisible(!visible)}>{visible?"Hide key":"Show key"}</button><button type="button" className="underline" onClick={async()=>{try{await navigator.clipboard.writeText(result.recoveryKey);setCopied(true);}catch{setVisible(true);}}}>{copied?"Copied":"Copy key"}</button></div>
        <label className="flex items-center gap-2"><input type="checkbox" checked={saved} onChange={e=>setSaved(e.target.checked)}/>I saved the recovery key offline</label>
        {/* Reload the auth provider after recovery has revoked the old session. */}
        {saved&&<a href="/admin/login" className="inline-flex min-h-11 items-center rounded-xl bg-[#7a1625] px-4 text-white">Go to admin login</a>}
    </section>;
}
async function publicApi<T>(path:string,signal:AbortSignal,body?:unknown):Promise<T> {
    const response=await fetch(`${ADMIN_API_BASE_URL}${path}`,{method:body?"POST":"GET",credentials:"omit",cache:"no-store",signal,
        headers:body?{"Content-Type":"application/json"}:undefined,body:body?JSON.stringify(body):undefined});
    if(!response.ok){let message="Unable to complete account verification.";try{const json=await response.json();message=json.message||message;}catch{}throw new Error(message);}
    return response.json();
}
export default function OwnerAccessForm({mode}:{mode:"setup"|"recover"}) {
    const [available,setAvailable]=useState<boolean|null>(mode==="recover"?true:null),[error,setError]=useState(""),[busy,setBusy]=useState(false),[result,setResult]=useState<OwnerResult|null>(null);
    const [key,setKey]=useState(""),[username,setUsername]=useState(""),[password,setPassword]=useState(""),[confirm,setConfirm]=useState(""),[fullName,setFullName]=useState("");
    const operation=useRef<AbortController|null>(null);
    useEffect(()=>{
        if(mode==="setup"){
            const controller=new AbortController();operation.current=controller;
            publicApi<{available:boolean}>("/api/admin/auth/owner-setup",AbortSignal.any([controller.signal,AbortSignal.timeout(15000)]))
                .then(value=>{if(!controller.signal.aborted)setAvailable(value.available);})
                .catch(()=>{if(!controller.signal.aborted){setAvailable(null);setError("Unable to check setup. Reload this page to try again.");}})
                .finally(()=>{if(operation.current===controller)operation.current=null;});
        }
        return()=>{operation.current?.abort();operation.current=null;};
    },[mode]);
    async function submit(event:React.FormEvent){
        event.preventDefault();if(operation.current||!available||result)return;
        if(password!==confirm){setError("Passwords do not match.");return;}
        const controller=new AbortController();operation.current=controller;setBusy(true);setError("");
        try{
            const value=await publicApi<OwnerResult>(`/api/admin/auth/owner-${mode==="setup"?"setup":"recovery"}`,AbortSignal.any([controller.signal,AbortSignal.timeout(30000)]),
                mode==="setup"?{setupKey:key,username,password,fullName}:{recoveryKey:key,username,password});
            if(!controller.signal.aborted){setResult(value);setKey("");setPassword("");setConfirm("");}
        }catch(failure){if(!controller.signal.aborted)setError(`${failure instanceof Error?failure.message:"Request failed."} If the response was lost, try admin login with your chosen username and password before trying again.`);}
        finally{if(operation.current===controller){operation.current=null;setBusy(false);}}
    }
    const input="mt-1 min-h-11 w-full rounded-xl border border-[#eadfd6] p-3";
    return <main className="flex min-h-screen items-center justify-center bg-[#fffaf3] p-4 py-12"><section className="w-full max-w-lg space-y-4 rounded-2xl border bg-white p-5 sm:p-8">
        <h1 className="text-2xl font-bold">{mode==="setup"?"Set up the first owner":"Recover owner account"}</h1>
        <p className="text-sm text-[#756763]">{mode==="setup"?"Create the first owner once. Additional admins are added through Add staff after sign-in.":"Use your saved account recovery key to choose a new username and password. MFA and staff permissions stay in place."}</p>
        {error&&<p role="alert" className="rounded-xl bg-red-50 p-3 text-sm text-red-800">{error}</p>}
        {result?<SavedOwnerKey result={result}/>:available===false?<p role="status">Setup is unavailable or already completed. Use admin login.</p>:available===null?<p role="status">{error?"Setup has not been checked.":"Checking setup…"}</p>:<form onSubmit={submit} className="space-y-4"><fieldset disabled={busy} className="space-y-4">
            <label className="block">{mode==="setup"?"Setup key":"Saved recovery key"}<input className={input} type="password" required autoComplete="off" value={key} onChange={e=>setKey(e.target.value)} maxLength={43}/></label>
            {mode==="setup"&&<label className="block">Owner name<input className={input} required maxLength={150} autoComplete="name" value={fullName} onChange={e=>setFullName(e.target.value)}/></label>}
            <label className="block">{mode==="setup"?"Username":"New username"}<input className={input} required minLength={2} maxLength={100} autoComplete="username" value={username} onChange={e=>setUsername(e.target.value)}/></label>
            <label className="block">New password<input className={input} type="password" required minLength={12} maxLength={72} autoComplete="new-password" value={password} onChange={e=>setPassword(e.target.value)}/></label>
            <label className="block">Confirm new password<input className={input} type="password" required minLength={12} maxLength={72} autoComplete="new-password" value={confirm} onChange={e=>setConfirm(e.target.value)}/></label>
            <p className="text-sm">Use at least 12 characters. {mode==="setup"?"After setup, sign in and enroll your authenticator.":"Sign in with your existing authenticator after recovery."} Keep MFA recovery codes separately from the account recovery key.</p>
            <button className="min-h-11 w-full rounded-xl bg-[#7a1625] p-3 font-semibold text-white" type="submit">{busy?"Checking account…":mode==="setup"?"Create first owner":"Recover credentials"}</button>
        </fieldset></form>}
        <Link href="/admin/login" className="inline-block underline">Back to admin login</Link>
    </section></main>;
}
