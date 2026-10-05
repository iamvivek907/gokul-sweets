import type {CSSProperties} from "react";
export default function CustomerIcon({kind,className,style}:{kind:"menu"|"pin"|"receipt"|"globe"|"profile";className?:string;style?:CSSProperties}){
 return <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" className={className} style={style}>
  {kind==="menu"?<><circle cx="12" cy="12" r="5.5"/><circle cx="12" cy="12" r="3"/><path d="M2 4v5h3V4M3.5 4v16M21 4v16M21 4c-3 3-3 7 0 7"/></>:kind==="pin"?<><path d="M20 10c0 6-8 11-8 11S4 16 4 10a8 8 0 1 1 16 0Z"/><circle cx="12" cy="10" r="2.5"/></>:kind==="receipt"?<><path d="M6 3h12v18l-3-2-3 2-3-2-3 2V3Z"/><path d="M9 7h6M9 11h6M9 15h3"/></>:kind==="globe"?<><circle cx="12" cy="12" r="9"/><ellipse cx="12" cy="12" rx="4" ry="9"/><path d="M3 12h18"/></>:<><circle cx="12" cy="8" r="3"/><path d="M5 21v-2a7 7 0 0 1 14 0v2"/></>}
 </svg>;
}
