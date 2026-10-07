
/* eslint-disable @next/next/no-img-element -- Real catalogue URLs are rendered directly without transforming supplier photos. */
"use client";
import BrandLoading from "@/components/common/BrandLoading";
import {notifyCustomerIdentityChanged} from "@/lib/customerIdentityEvents";
import {T} from "@/lib/language";

import OccasionPackingBuilder from "@/components/occasion/OccasionPackingBuilder";
import {buildOccasionItems,packingError,inclusiveRate,indicativeFood} from "@/lib/occasionPacking";
import {useRouter} from "next/navigation";
import OccasionDatePicker,{addDays} from "@/components/occasion/OccasionDatePicker";
import {useEffect, useState} from "react";
import Link from "next/link";
import AppShell from "@/components/layout/AppShell";
import CustomerIdentityPanel, {type CustomerSession} from "@/components/customer/CustomerIdentityPanel";
import {useSelectedBranch} from "@/hooks/useSelectedBranch";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";
import {apiClient, ApiError} from "@/services/apiClient";
import type {OccasionSweet, OccasionBox, OccasionCatalogue, OccasionBranding, GiftSnapshot, PackingDraft} from "@/types/occasionCatalogue";

type Item = {productName?: string; productId: number; quantity: number; unit: "GRAM" | "PIECE"};
type Enquiry = {id: string; branchId: number; occasionType: string; serviceDate: string; guestCount: number;
    gift?: GiftSnapshot | null; status: string; quotedAmount: number | null; depositAmount: number | null; paidAmount: number;
    quoteTerms: string | null; quoteExpiresAt: string | null; balanceDueAt: string | null;
    nextStep: string; fulfilment: string; items: Item[]; orderNumber: string | null; balancePaymentOpen: boolean; cancellationReview?: {paidAmount: number; reason: string; state: string} | null; productionPlan?: {productId: number; unit: string; expectedReadyAt: string; state: string; quantity: number; readyQuantity: number}[];
    pricedLines: {productId: number; productName: string; grossAmount: number; subtotal: number;
        taxAmount: number; cgstRate: number; sgstRate: number}[]};
export default function OccasionsPage() {
    const router=useRouter();
    const features = useStorefrontFeatures();
    const {branch} = useSelectedBranch();
    const home = features?.branchExperience ? branch ? `/branches/${branch.id}` : "/branches" : "/";
    const homeLabel = features?.branchExperience ? branch ? "Branch home" : "All branches" : "Home";
    const [sessionVersion, setSessionVersion] = useState(0);
    const [session, setSession] = useState<CustomerSession>({authenticated: false});
    const [catalogueSearch, setCatalogueSearch] = useState("");
    const [catalogueBranchId,setCatalogueBranchId]=useState<number|null>(null);
    const [branding,setBranding]=useState<OccasionBranding|null>(null);
    const [category,setCategory]=useState<string>("");
    const [specialOnly, setSpecialOnly] = useState(false);
    const [products, setProducts] = useState<OccasionSweet[]>([]);
    const [boxes, setBoxes] = useState<OccasionBox[]>([]);
    const [groups,setGroups]=useState<PackingDraft[]>([]);
    const [units, setUnits] = useState<Record<number, "KG" | "PIECE">>({});
    const [items, setItems] = useState<Record<number, number>>({});
    const [type, setType] = useState("Family celebration");
    const [date, setDate] = useState("");
    const [guests, setGuests] = useState(20);
    const [mode, setMode] = useState<"PICKUP" | "DELIVERY_REQUEST">("PICKUP");
    const [address, setAddress] = useState("");
    const [notes, setNotes] = useState("");
    const [busy, setBusy] = useState(false);
    const [message, setMessage] = useState("");

    useEffect(()=>{const query=new URLSearchParams(window.location.search);if(query.has("enquiry")||window.location.hash.startsWith("#occasion-")&&window.location.hash!=="#occasion-plan"){if(!query.has("enquiry"))query.set("enquiry",window.location.hash.slice(10));router.replace(`/occasions/requests?${query}`);}},[router]);

    useEffect(() => {
        if (!branch || !features?.occasionEnquiries) return;
        const controller = new AbortController();
        apiClient<OccasionCatalogue>(`/api/branches/${branch.id}/occasion-catalogue`, {signal: controller.signal}).then(catalogue => {
            if (!controller.signal.aborted) {setCatalogueBranchId(branch.id);setProducts(catalogue.sweets); setBoxes(catalogue.boxes);setBranding(catalogue.branding??null);setCategory(catalogue.sweets[0]?.categoryName??"Selection"); setItems({}); setGroups([]);}
        }).catch(() => {if (!controller.signal.aborted) setMessage("Menu unavailable. Please retry before requesting a quote.");});
        return () => controller.abort();
    }, [branch, features?.occasionEnquiries]);

    async function submit(event: React.FormEvent<HTMLFormElement>) {
        event.preventDefault();
        if (!branch || busy || catalogueBranchId!==branch.id) return;
        if (!session.authenticated) {setMessage("Verify your phone before sending your enquiry. Your selection is saved on this page."); return;}
        if (!date || date < earliest || date > latest) {setMessage("Choose a future service date in India."); return;}
        const chosen=buildOccasionItems(products,items,units,groups);
        const packingIssue=packingError(groups,chosen,boxes);
        if(packingIssue){setMessage(packingIssue);return;}
        if (!chosen.length) {setMessage("Add at least one product and quantity."); return;}
        setBusy(true); setMessage("");
        try {
            const result = await apiClient<Enquiry>("/api/occasion-enquiries", {method: "POST", credentials: "include",
                body: JSON.stringify({branchId: branch.id, occasionType: type, serviceDate: date, guestCount: guests,
                    fulfilment: mode, deliveryAddress: mode === "DELIVERY_REQUEST" ? address : null, notes, items: chosen, gift:null, packingGroups:groups.map(group=>({kind:group.kind,boxId:group.boxId,boxCount:group.boxCount,recipe:group.recipe,productId:group.productId,totalGrams:group.totalGrams,packGrams:group.packGrams,includeSpoons:group.includeSpoons}))})});
            setMessage("Request sent. The branch will review it before sharing a quote. No booking or payment has been made.");
            setItems({});router.push(`/occasions/requests?enquiry=${encodeURIComponent(result.id)}`);
        } catch (error) {
            if (error instanceof ApiError && error.status === 401) {
                setSession({authenticated: false}); setSessionVersion(current => current + 1);
                notifyCustomerIdentityChanged();
            }
            setMessage(error instanceof ApiError && error.status === 401 ? "Please verify your phone, then try again."
                : error instanceof ApiError && error.status === 429 ? "Requests are arriving too quickly. Please wait a moment and retry; your selections are saved here."
                : error instanceof ApiError && error.status === 400 ? error.message
                : "The request could not be sent. Your entries are still here; please retry.");
        } finally {setBusy(false);}
    }

    const campaign=catalogueBranchId===branch?.id?branding:null;
    const categories=Array.from(new Set(products.map(product=>product.categoryName??"Selection")));
    const chosen=buildOccasionItems(products,items,units,groups);
    const selected=products.filter(product=>chosen.some(item=>item.productId===product.id));
    const selectedBoxes=boxes.filter(box=>groups.some(group=>group.boxId===box.id));
    const earliest=features?.today?addDays(features.today,Math.max(1,...selectedBoxes.map(box=>box.leadDays),...selected.map(product=>product.leadDays))):"";
    const latest=features?.today?addDays(features.today,365):"";
    const money=(value:number)=>new Intl.NumberFormat("en-IN",{style:"currency",currency:"INR"}).format(value);
    const prices=chosen.map(item=>indicativeFood(item,products.find(p=>p.id===item.productId)!));
    const foodEstimate=prices.reduce<number>((sum,value)=>sum+(value??0),0);
    const packingEstimate=groups.reduce((sum,group)=>sum+(boxes.find(box=>box.id===group.boxId)?.price??0)*group.boxCount,0);
    const incompletePrice=prices.some(price=>price==null)||groups.some(group=>boxes.find(box=>box.id===group.boxId)?.price==null||group.includeSpoons);
    return <AppShell editorial showSocialPopup={false}>
        <div className="occasion-journey mx-auto max-w-7xl px-4 py-8 text-[#173a37] sm:px-6">
            <nav className="mobile-occasion-navigation" aria-label="Celebration navigation"><Link href={home}><span aria-hidden="true">←</span> <T text={homeLabel} /></Link><Link href="/occasions/requests"><T text="Requests & quotes" /></Link></nav>
            <header className="occasion-hero"><p className="text-sm font-bold uppercase tracking-widest text-[#b55f4a]"><span className="desktop-celebration-label">Occasions at Gokul</span><span className="mobile-celebration-label"><T text="Bulk order" /></span></p>
            <h1 className="mt-3 font-serif text-4xl sm:text-6xl">{campaign?.headline||"Sweet moments. Thoughtfully planned."}</h1>
            <p className="mt-4 max-w-2xl">{campaign?.description||"Share the date, guests and food you have in mind. Our team reviews availability and gives you a clear quote before any payment."}</p><div className="mt-6 flex flex-wrap gap-2 text-sm"><span>Weddings & family celebrations</span><span>Corporate gifting</span><span>Made-to-order sweets</span></div>{campaign?.imageUrl&&<img src={campaign.imageUrl} alt={`${branch?.name??"Gokul"} occasion collection`} className="occasion-campaign-photo" />}<div className="occasion-hero-actions"><a href="#occasion-plan" className="occasion-primary">Build your celebration</a><Link href="/occasions/requests" className="occasion-secondary">Track requests & quotes</Link></div></header>
            {!features && <BrandLoading label="Checking availability…" />}
            {features && !features.occasionEnquiries && <p className="mt-8 rounded-xl bg-white p-6">Occasion enquiries are not available yet. <Link href="/menu" className="underline">Explore pickup ordering</Link>.</p>}
            {features?.occasionEnquiries && <>
                {!branch ? <p className="mt-8 rounded-xl bg-white p-6">Choose a branch first. <Link href="/branches" className="underline">Explore branches</Link>.</p> : <>
                    <p className="mt-5 font-semibold">Planning with {branch.name} · <Link href="/branches" className="underline"><T text="Change branch" /></Link></p>
                    <div className="mt-7 rounded-2xl border border-[#d9e5df] bg-white p-5"><CustomerIdentityPanel key={sessionVersion} mode="occasion" onSessionChange={setSession} /></div>
                    {catalogueBranchId!==branch.id?<BrandLoading compact label="Loading this branch’s occasion collection…" />:<form id="occasion-plan" onSubmit={submit} className="mt-7 space-y-5 rounded-2xl border border-[#d9e5df] bg-white p-5 sm:p-8">
                        <p className="text-xs font-bold uppercase tracking-widest text-[#c76752]">01 · Plan your celebration</p><p className="text-sm">Choose bulk quantities and optional packing groups. Our branch reviews production, box fit and pricing before you pay.</p>
                        <label className="block">Occasion <input required maxLength={80} value={type} onChange={event => setType(event.target.value)} className="mt-2 w-full rounded-xl border p-3" /></label>
                        <div className="grid gap-4 sm:grid-cols-2">
                            <OccasionDatePicker value={date} onChange={setDate} min={earliest} max={latest} />
                            <label>Guests <input type="number" required min={1} max={10000} value={guests} onChange={event => setGuests(Number(event.target.value))} className="mt-2 block w-full rounded-xl border p-3" /></label>
                        </div>
                        <label className="block">How should food be collected?
                            <select value={mode} onChange={event => setMode(event.target.value as typeof mode)} className="mt-2 block w-full rounded-xl border p-3">
                                <option value="PICKUP">Pickup at the branch</option><option value="DELIVERY_REQUEST">Ask about delivery (not confirmed)</option>
                            </select>
                        </label>
                        {mode === "DELIVERY_REQUEST" && <label className="block">Delivery address for review <textarea required maxLength={500} value={address} onChange={event => setAddress(event.target.value)} className="mt-2 block w-full rounded-xl border p-3" /></label>}
                        <fieldset><legend className="font-serif text-2xl">02 · Choose food & quantities</legend>
                            <p className="mt-2 text-sm">Discover celebration specials alongside your favourites. Piece requests for weight-based sweets are sized by the branch before quoting.</p>
                            <div className="occasion-categories" aria-label="Occasion categories">{categories.map(name=>{const categoryProducts=products.filter(product=>(product.categoryName??"Selection")===name);const photo=categoryProducts.find(product=>product.imageUrl)?.imageUrl;return <button key={name} type="button" aria-label={`${name} category, ${categoryProducts.length} options`} aria-pressed={category===name} onClick={()=>{setCategory(name);setCatalogueSearch("");}}>{photo?<img src={photo} alt={`${name} selection`} />:<div className="occasion-category-fallback">G</div>}<span><strong>{name}</strong><small>{categoryProducts.length} options · {categoryProducts.filter(product=>chosen.some(item=>item.productId===product.id)).length} selected</small></span></button>;})}</div>
                            <div className="mt-4 flex flex-wrap gap-3 items-end"><label className="flex-1 text-sm"><T text="Find your favourites" /><input type="search" value={catalogueSearch} onChange={event => setCatalogueSearch(event.target.value)} placeholder="Search the occasion collection" className="mt-1 w-full rounded-xl border p-3" /></label><label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={specialOnly} onChange={event => setSpecialOnly(event.target.checked)} />Occasion specials only</label></div>
                            <div className="mt-4 grid gap-4 sm:grid-cols-2">{products.filter(product => (catalogueSearch.trim() || (product.categoryName??"Selection")===category) && (!specialOnly || product.occasionOnly) && product.name.toLowerCase().includes(catalogueSearch.trim().toLowerCase())).map(product => <div key={product.id} className="occasion-product overflow-hidden rounded-2xl border">
                                {product.imageUrl ? <img src={product.imageUrl} alt={product.name} className="h-40 w-full object-cover" /> : <div className="flex h-24 items-center justify-center bg-[#fffaf2] font-serif text-xl">Gokul celebration selection</div>}
                                <div className="p-4">{product.occasionOnly && <p className="text-xs font-bold uppercase tracking-wider text-[#c76752]">Made for occasions</p>}<h3 className="mt-1 text-lg font-semibold">{product.name}</h3>{product.description && <p className="mt-1 text-sm">{product.description}</p>}<p className="mt-2 text-xs">{product.leadDays} days advance notice{product.pieceGrams ? ` · configured piece size ${product.pieceGrams} g` : ""}</p>
                                <p className="mt-3 font-semibold">{inclusiveRate(product)==null?"Price available on branch review":`${money(inclusiveRate(product)!)} per ${product.saleMode==="WEIGHT"?"kg":"piece"} · tax included`}</p>{product.saleMode==="WEIGHT"&&<p className="mt-1 text-xs">Pieces are priced from measured kg; the branch confirms the weight estimate.</p>}
                                <div className="occasion-quantity mt-3"><label className="text-sm">{"Bulk quantity without individual boxes"}<input aria-label={`${product.name} quantity`} type="number" min={0} max={100000} step={ (units[product.id] ?? (product.saleMode === "WEIGHT" ? "KG" : "PIECE")) === "PIECE" ? 1 : 0.001} value={items[product.id] ?? 0} onChange={event => setItems(current => ({...current, [product.id]: Number(event.target.value)}))} className="mt-1 w-full rounded-xl border p-3" /></label>{<label className="text-sm">Unit<select aria-label={`${product.name} unit`} value={units[product.id] ?? (product.saleMode === "WEIGHT" ? "KG" : "PIECE")} onChange={event => {setUnits(current => ({...current, [product.id]: event.target.value as "KG" | "PIECE"})); setItems(current => ({...current, [product.id]: 0}));}} className="mt-1 rounded-xl border p-3">{product.saleMode === "WEIGHT" && <option value="KG">kg</option>}<option value="PIECE">pieces</option></select></label>}</div>
                                </div></div>)}</div>
                        </fieldset>
                        <OccasionPackingBuilder products={products} boxes={boxes} groups={groups} onChange={setGroups} />
                        <aside className="occasion-selection" aria-label="Your occasion selection"><div className="flex flex-wrap items-center justify-between gap-3"><div><p className="text-xs font-bold uppercase tracking-widest">Your celebration basket</p><h3 className="text-lg font-semibold">{chosen.length} items · {groups.length} packing groups</h3></div><strong>{incompletePrice?"Known-price subtotal":"Indicative total"} {money(foodEstimate+packingEstimate)}</strong></div><details className="mt-2"><summary className="min-h-11 cursor-pointer text-sm font-semibold">Review quantities & indicative costs</summary><ul>{chosen.map((item,index)=>{const product=products.find(p=>p.id===item.productId)!;return <li key={item.productId}><span>{product.name}<small className="block">{item.unit==="GRAM"?`${item.quantity/1000} kg`:`${item.quantity.toLocaleString("en-IN")} pieces`}{item.supplementalGrams?` + ${item.supplementalGrams/1000} kg`:""}</small></span><strong>{prices[index]==null?"Weight / price review needed":money(prices[index]!)}</strong><button type="button" aria-label={`Remove ${product.name} from occasion selection`} onClick={()=>{setItems(current=>({...current,[product.id]:0}));setGroups(current=>current.filter(group=>group.productId!==product.id).map(group=>({...group,recipe:group.recipe.filter(line=>line.productId!==product.id)})));}}><T text="Remove" /></button></li>;})}</ul><p className="text-sm">Packaging estimate {money(packingEstimate)}{groups.some(g=>g.includeSpoons)?" · spoon price follows branch review":""}.</p></details><p className="mt-2 text-xs">{incompletePrice?"Some items need measured weight or reviewed prices, so this is a partial estimate. ":""}The branch confirms rates, rebates, fit and final quote before any advance. No payment is taken when you send this request.</p></aside>
                        <label className="block">Anything else? <textarea maxLength={1000} value={notes} onChange={event => setNotes(event.target.value)} className="mt-2 block w-full rounded-xl border p-3" /></label>
                        <div className="flex flex-wrap gap-3">
                            <button disabled={busy || !products.length || !session.authenticated || !date || date<earliest || date>latest} className="min-h-12 rounded-full bg-[#c76752] px-6 font-bold text-white disabled:opacity-50">{busy ? "Sending…" : "Request a reviewed quote"}</button>
                        </div>
                        <p className="text-sm text-[#4e605c]">{session.authenticated?"You are signed in with a verified phone. Your request will appear in Requests & quotes.":"Verify your phone above to send your request."} The branch confirms the price and collection time before you pay.</p>
                    </form>}
                </>}
                {message && <p role="status" className="mt-5 rounded-xl bg-[#fff0dc] p-4">{message}</p>}

            </>}
        </div>
    </AppShell>;
}
