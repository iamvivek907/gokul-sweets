"use client";
import {useRef,useState} from "react";
import {useTranslation} from "@/lib/language";
import styles from "./SwipeOrderAction.module.css";
/** A deliberate horizontal gesture, with a keyboard/tap confirmation alternative. */
export default function SwipeOrderAction({label,busy,disabled,onComplete}:{label:string;busy:boolean;disabled:boolean;onComplete:()=>void}){
 const translate=useTranslation(),[distance,setDistance]=useState(0),[confirm,setConfirm]=useState(false);const origin=useRef<number|null>(null),track=useRef<HTMLDivElement>(null);
 const width=()=>Math.max(1,(track.current?.clientWidth??240)-56);
 return <div className={styles.action}><div ref={track} className={styles.track} aria-hidden="true"><span>{translate(busy?'Updating order…':label)}</span><div className={styles.thumb} style={{transform:`translateX(${distance}px)`}} onPointerDown={event=>{if(disabled||busy)return;origin.current=event.clientX;event.currentTarget.setPointerCapture(event.pointerId);}} onPointerMove={event=>{if(origin.current!==null)setDistance(Math.min(width(),Math.max(0,event.clientX-origin.current)));}} onPointerUp={event=>{if(origin.current===null)return;const travelled=event.clientX-origin.current;origin.current=null;setDistance(0);if(travelled>=width()*.8&&!disabled&&!busy)onComplete();}} onPointerCancel={()=>{origin.current=null;setDistance(0);}}>{busy?'…':'→'}</div></div><button type="button" disabled={disabled||busy} className={styles.alternative} onClick={()=>setConfirm(!confirm)}>{translate('Use button instead')}</button>{confirm&&<div className={styles.confirm}><p>{translate(label)}</p><button type="button" disabled={disabled||busy} onClick={()=>{setConfirm(false);onComplete();}}>{translate('Confirm')}</button><button type="button" disabled={busy} onClick={()=>setConfirm(false)}>{translate('Cancel')}</button></div>}</div>;
}
