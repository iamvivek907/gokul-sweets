import ReferenceWordmark from "@/components/layout/ReferenceWordmark";
import {T} from "@/lib/language";

/** Shared customer loading language; the caller retains its own timeout and retry. */
export default function BrandLoading({label="Loading your page…",className="",compact=false,fullscreen=false}:{label?:string;className?:string;compact?:boolean;fullscreen?:boolean}) {
    return <div role="status" aria-live="polite" aria-busy="true" className={`customer-brand-loading ${compact?"is-compact":"customer-page-state"} ${fullscreen?"customer-route-loading":""} ${className}`}>
        <div><ReferenceWordmark className="brand-loading-wordmark" /><span className="customer-brand-loading-spinner" aria-hidden="true"/><p><T text={label}/></p></div>
    </div>;
}
