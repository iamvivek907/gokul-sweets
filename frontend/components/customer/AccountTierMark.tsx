/** Recognition for completed visits; never a phone/identity verification claim. */
export default function AccountTierMark({orders}:{orders:number}){
 if(orders<1)return null;
 const tier=orders>=20?"gold":orders>=5?"blue":"grey";
 const label=orders>=20?"Gokul favourite":orders>=5?"Regular":"First visit";
 return <span className={`account-tier-mark tier-${tier}`} role="img" aria-label={`${label} · earned Gokul recognition`} title={`${label} · earned Gokul recognition`}><svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="m12 1 3 2 3.5.5.5 3.5 2 3-2 3-.5 3.5-3.5.5-3 2-3-2-3.5-.5-.5-3.5-2-3 2-3 .5-3.5 3.5-.5z"/><path d="m7 12 3 3 7-7" fill="none" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/></svg></span>;
}
