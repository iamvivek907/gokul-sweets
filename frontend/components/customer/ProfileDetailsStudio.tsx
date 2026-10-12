"use client";
import {useRef, useState} from "react";
import Link from "next/link";
import {T,useTranslation} from "@/lib/language";
import {apiClient} from "@/services/apiClient";
import CustomerIcon from "./CustomerIcon";
import AddressLocationAssist from "./AddressLocationAssist";
import styles from "./ProfileDetailsStudio.module.css";

type Address = {id:number;label:string;addressLine:string;locality:string;postalCode:string};
type Draft = Omit<Address,"id">;
const empty = ():Draft => ({label:"Home",addressLine:"",locality:"",postalCode:""});

export function ProfileCompletion({named,addresses,onName,onAddresses}:{named:boolean;addresses:number;onName:()=>void;onAddresses:()=>void}) {
    return <aside className={styles.completion} aria-label="Your profile at a glance">
        <div><span className={styles.eyebrow}><T text="MADE FOR YOU"/></span><h2><T text={named?"Your next visit, made easier":"A name makes it personal"}/></h2>
        <p><T text={named?"Keep your favourite places together with your verified account.":"What should we call you when your order is ready? Add the name you like to use. It’s optional."}/></p></div>
        <ul className={styles.steps}><li data-done="true"><T text="✓ Phone verified"/></li><li data-done={named}><T text={named?"✓ Name saved":"○ Your name"}/></li><li data-done={addresses>0}><T text={addresses>0?"✓ Address saved":"○ Your places"}/></li></ul>
        <div className={styles.actions}>{!named&&<button type="button" onClick={onName}><T text="Add your name"/></button>}<button type="button" className={styles.secondary} onClick={onAddresses}><T text={addresses?"Manage addresses":"Save a place"}/></button></div>
        <small><T text="Optional details. Ordering and rewards work without completing your profile."/></small>
    </aside>;
}

export default function ProfileDetailsStudio({mode,name,phone,nameDraft,onNameDraft,editing,onEditing,onSaveName,nameBusy,addresses,onRefresh,onPreferences,onSignOut}:{
    mode:"details"|"addresses";name:string;phone:string;nameDraft:string;onNameDraft:(value:string)=>void;editing:boolean;onEditing:(value:boolean)=>void;
    onSaveName:()=>Promise<void>;nameBusy:boolean;addresses:Address[];onRefresh:()=>Promise<void>;onPreferences:()=>void;onSignOut:()=>void;
}) {
    const translate=useTranslation();
    const [draft,setDraft]=useState<Draft>(empty),[editId,setEditId]=useState<number|null>(null),[busy,setBusy]=useState(false),[message,setMessage]=useState("");
    const [removeId,setRemoveId]=useState<number|null>(null);
    const locked=useRef(false),form=useRef<HTMLFormElement>(null);
    const base="/api/customer/identity/account/addresses";
    async function saveAddress() {
        if(locked.current)return;locked.current=true;setBusy(true);setMessage("");
        try {
            await apiClient<Address>(editId===null?base:`${base}/${editId}`,{method:editId===null?"POST":"PUT",credentials:"include",body:JSON.stringify(Object.fromEntries(Object.entries(draft).map(([key,value])=>[key,value.trim()]))),signal:AbortSignal.timeout(10000)});
            const savedMessage=editId===null?"Address saved.":"Address updated.";
            setDraft(empty());setEditId(null);
            try {await onRefresh();setMessage(savedMessage);} catch {setMessage(`${savedMessage} Your list could not refresh. Reload the page to see it.`);}
        } catch {setMessage("The save could not be confirmed. Your details are still here. Retry to confirm this place; an identical saved address will not be added twice.");}
        finally {locked.current=false;setBusy(false);}
    }
    async function removeAddress(id:number) {
        if(locked.current)return;locked.current=true;setBusy(true);setMessage("");
        try {
            await apiClient<void>(`${base}/${id}`,{method:"DELETE",credentials:"include",signal:AbortSignal.timeout(10000)});
            if(editId===id){setEditId(null);setDraft(empty());}setRemoveId(null);
            try {await onRefresh();setMessage("Address removed.");} catch {setMessage("Address removed. Reload the page to refresh your places.");}
        } catch {setMessage("Your address could not be removed. Please try again.");}
        finally {locked.current=false;setBusy(false);}
    }
    return <section className={styles.studio} id={mode==="details"?"account-details":"account-addresses"}>
        <header className={styles.hero}><span className={styles.orbit} aria-hidden="true"><CustomerIcon kind={mode==="details"?"profile":"pin"}/></span>
            <span className={styles.eyebrow}><T text="YOUR PERSONAL SPACE"/></span><h2><T text={mode==="details"?"A little more you.":"Your places. One account."}/></h2>
            <p><T text={mode==="details"?"A familiar name. Your saved places. Ready whenever you return.":"Save once, find it here on your next device. You choose what to share."}/></p>
            <span className={styles.secure}><T text="✓ Linked to your verified phone"/></span>
        </header>
        {mode==="details"&&<>
            <div className={styles.identity}><div className={styles.avatar} aria-hidden="true">{name.trim()?name.trim().slice(0,1).toUpperCase():<CustomerIcon kind="profile"/>}</div><div><small><T text="YOUR ACCOUNT"/></small><strong>{name.trim()||"Let’s put a name to your order"}</strong><p>{phone} <span><T text="Verified"/></span></p></div></div>
            <div className={styles.nameCard}><div className={styles.cardTitle}><CustomerIcon kind="profile"/><div><h3><T text="What should we call you?"/></h3><p><T text="Your name helps our team recognise your order. A first name is enough."/></p></div></div>
                {editing?<form onSubmit={event=>{event.preventDefault();void onSaveName();}}>
                    <label htmlFor="account-name-edit"><T text="Your name"/><span><T text="Optional profile detail"/></span></label>
                    <input id="account-name-edit" autoComplete="given-name" required minLength={2} maxLength={80} placeholder={translate("The name you like to use")} value={nameDraft} disabled={nameBusy} onChange={event=>onNameDraft(event.target.value)}/>
                    <div className={styles.actions}><button type="submit" disabled={nameBusy||nameDraft.trim()===(name??"")}><T text={nameBusy?"Saving…":"Save name"}/></button>{editing&&<button className={styles.secondary} type="button" disabled={nameBusy} onClick={()=>{onEditing(false);onNameDraft(name);}}><T text="Cancel"/></button>}</div>
                    <small><T text="You can leave this blank and keep ordering. No new OTP needed."/></small>
                </form>:<div className={styles.actions}><button className={styles.secondary} type="button" onClick={()=>{onNameDraft(name);onEditing(true);}}><T text={name.trim()?"Edit name":"Add your name"}/></button></div>}
            </div>
        </>}
        <div className={styles.placesHeading}><div><span className={styles.eyebrow}><T text="SAVED PLACES"/></span><h3><T text={addresses.length?"Your address collection":"Where’s home for you?"}/></h3></div><span>{addresses.length}/5 saved</span></div>
        <p className={styles.context}><T text="Today, orders are collected at the shop. Sharing your locality and postal code helps us plan future delivery areas. Saving an address does not confirm delivery availability."/></p>
        <div className={styles.places}>{addresses.map(saved=><article key={saved.id} className={styles.place}><CustomerIcon kind="pin"/><div><strong>{saved.label}</strong><p>{saved.addressLine}</p><small>{saved.locality} · {saved.postalCode}</small></div><div className={styles.placeActions}>
            <button type="button" disabled={busy} onClick={()=>{setEditId(saved.id);setDraft({label:saved.label,addressLine:saved.addressLine,locality:saved.locality,postalCode:saved.postalCode});setMessage("");form.current?.scrollIntoView({block:"center",behavior:window.matchMedia("(prefers-reduced-motion: reduce)").matches?"auto":"smooth"});}}><T text="Edit"/></button>
            <button type="button" disabled={busy} onClick={()=>setRemoveId(saved.id)}><T text="Remove"/></button>
        </div>{removeId===saved.id&&<div className={styles.confirm}><p><T text="Remove this saved place?"/></p><button type="button" disabled={busy} onClick={()=>void removeAddress(saved.id)}><T text="Remove address"/></button><button type="button" disabled={busy} onClick={()=>setRemoveId(null)}><T text="Keep address"/></button></div>}</article>)}</div>
        <form ref={form} className={styles.addressForm} onSubmit={event=>{event.preventDefault();void saveAddress();}}>
            <div className={styles.cardTitle}><CustomerIcon kind="pin"/><div><h3><T text={editId===null?"Save a place":"Update your place"}/></h3><p><T text="Keep the details you’d want us to find easily."/></p></div></div>
            <fieldset disabled={busy||nameBusy||editId===null&&addresses.length>=5}>
                <AddressLocationAssist contextKey={JSON.stringify([editId,draft])} onSuggestion={value=>setDraft(current=>({...current,addressLine:value.addressLine,locality:value.locality,postalCode:value.postalCode}))}/>
                <div className={styles.labelChoices} role="group" aria-label="Address label">{["Home","Work","Other"].map(label=><button key={label} type="button" aria-pressed={draft.label===label} className={styles.secondary} onClick={()=>setDraft(current=>({...current,label}))}><T text={label}/></button>)}</div>
                <div className={styles.fields}>
                    <label><T text="Label"/><input autoComplete="off" required maxLength={40} value={draft.label} onChange={event=>{const value=event.target.value;setDraft(current=>({...current,label:value}));}}/></label>
                    <label className={styles.wide}><T text="Address line"/><input autoComplete="address-line1" placeholder={translate("House / flat, building and street")} required maxLength={180} value={draft.addressLine} onChange={event=>{const value=event.target.value;setDraft(current=>({...current,addressLine:value}));}}/></label>
                    <label><T text="Locality"/><input autoComplete="address-level2" placeholder={translate("Area or village")} required maxLength={100} value={draft.locality} onChange={event=>{const value=event.target.value;setDraft(current=>({...current,locality:value}));}}/></label>
                    <label><T text="Postal code"/><input autoComplete="postal-code" inputMode="numeric" pattern="[1-9][0-9]{5}" placeholder={translate("6-digit PIN")} required maxLength={6} value={draft.postalCode} onChange={event=>{const value=event.target.value.replace(/\D/g,"").slice(0,6);setDraft(current=>({...current,postalCode:value}));}}/></label>
                </div><div className={styles.actions}><button type="submit"><T text={busy?"Saving…":editId===null?"Save address":"Save changes"}/></button></div>
            </fieldset>
            {addresses.length>=5&&editId===null&&<p><T text="Five places are saved. Edit or remove a place before adding another."/></p>}
            {editId!==null&&<button type="button" disabled={busy} className={styles.secondary} onClick={()=>{setEditId(null);setDraft(empty());}}><T text="Cancel"/></button>}
            <small><T text="Location is suggested only when you ask. Check the details before saving."/></small>
        </form>
        {message&&<p className={styles.message} role="status">{message}</p>}
        <footer className={styles.footer}><Link href="/profile/privacy"><T text="Privacy & your data"/></Link>{mode==="details"&&<><button type="button" onClick={onPreferences}><T text="Manage preferences"/></button><button type="button" className="account-details-signout" disabled={nameBusy||busy} onClick={onSignOut}><T text="Sign out"/></button></>}</footer>
    </section>;
}
