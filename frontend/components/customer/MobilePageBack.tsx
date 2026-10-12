import Link from "next/link";
import {T} from "@/lib/language";
export default function MobilePageBack({href,label,className=""}:{href:string;label:string;className?:string}){
 return <Link href={href} className={`mobile-page-back ${className}`}><span className="page-back-legacy" aria-hidden="true">←</span><svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><path d="m14 6-6 6 6 6" strokeLinecap="round" strokeLinejoin="round"/></svg><span className="page-back-label"><T text={label}/></span></Link>;
}
