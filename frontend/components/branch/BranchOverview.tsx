"use client";

import {useState} from "react";
import Link from "next/link";
import type {Branch} from "@/types/branch";
import {T} from "@/lib/language";
import CustomerIcon from "@/components/customer/CustomerIcon";
import MobilePageBack from "@/components/customer/MobilePageBack";
import BranchSelector from "./BranchSelector";
import BranchDetails from "./BranchDetails";
import BranchDiscovery from "./BranchDiscovery";

export default function BranchOverview({branch, selected, occasionEnquiries}: {
    branch: Branch; selected: boolean; occasionEnquiries: boolean;
}) {
    const [details, setDetails] = useState(false);
    const [failedPhoto, setFailedPhoto] = useState(false);
    const address = [branch.address, branch.city, branch.state, branch.pincode].filter(Boolean).join(", ");
    const hours = branch.openingTime && branch.closingTime
        ? `${branch.openingTime.slice(0, 5)}–${branch.closingTime.slice(0, 5)} IST` : null;
    const coordinates = branch.latitude != null && branch.longitude != null
        && Number.isFinite(branch.latitude) && Number.isFinite(branch.longitude);
    const mapQuery = coordinates ? `${branch.latitude},${branch.longitude}` : address;
    return <article className="branch-home branch-overview">
        <div className="branch-overview-top">
            <MobilePageBack href="/branches" label="All branches" className="branch-home-back" />
            <span><T text="Branch home" /></span>
        </div>
        <header className="branch-home-hero branch-overview-hero">
            <div className="branch-overview-copy">
                <p className="gokul-overline"><T text="Welcome to your Gokul branch" /></p>
                <h1>{branch.name}</h1>
                <p>{branch.description || <T text="Fresh sweets, snacks and meals from your neighbourhood Gokul branch." />}</p>
                <div className="branch-overview-badges">
                    <span data-pickup={branch.pickupAvailable === true}><CustomerIcon kind="menu" /><T text={branch.pickupAvailable ? "Order online · Collect here" : "Explore the branch · Contact us to visit"} /></span>
                </div>
                <div className="branch-home-actions">
                    {selected ? <Link data-ordering-target="menu" href="/menu"><T text="Browse menu" /><span aria-hidden="true">→</span></Link>
                        : <BranchSelector cardBranch={branch} destination="menu" actionLabel="Browse menu" />}
                </div>
                <small><T text="See this branch’s items, prices and availability on the menu." /></small>
            </div>
            <div className="branch-overview-photo">
                {!failedPhoto && branch.coverImageUrl ? <picture>
                    {branch.mobileCoverImageUrl && <source media="(max-width: 640px)" srcSet={branch.mobileCoverImageUrl} />}
                    <img src={branch.coverImageUrl} alt={branch.coverAltText || branch.name} onError={() => setFailedPhoto(true)} />
                </picture> : <div className="branch-overview-monogram" aria-hidden="true"><span>G</span><small>GOKUL SWEETS</small></div>}
            </div>
        </header>

        <section className="branch-visit-summary" aria-label="Plan your visit">
            <div><CustomerIcon kind="pin" /><div><h2><T text="Find us" /></h2><p>{address || <T text="Address being updated" />}</p></div></div>
            <div><span className="branch-clock-icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><circle cx="12" cy="12" r="9"/><path d="M12 6v6l4 2"/></svg></span><div><h2><T text="Opening hours" /></h2><p>{hours || <T text="Contact the branch for hours" />}</p></div></div>
            <div className="branch-visit-links">
                {mapQuery && <a href={`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(mapQuery)}`} target="_blank" rel="noreferrer"><T text="Get directions" /><span aria-hidden="true">↗</span></a>}
                {branch.phone && <a href={`tel:${branch.phone}`}><T text="Call branch" /><span aria-hidden="true">↗</span></a>}
            </div>
        </section>

        <nav className="gokul-branch-tabs branch-overview-tabs" aria-label="Branch pages">
            <button className="branch-home-tab" type="button" aria-current={!details ? "page" : undefined} onClick={() => setDetails(false)}><T text="Home" /></button>
            {occasionEnquiries && (selected ? <Link href="/occasions"><span className="desktop-celebration-label"><T text="Occasions & gifting" /></span><span className="mobile-celebration-label"><T text="Bulk order" /></span></Link> : <BranchSelector cardBranch={branch} destination="occasions" />)}
            {occasionEnquiries && <Link className="branch-request-desktop" href="/occasions/requests"><T text="Requests & quotes" /></Link>}
            <button type="button" aria-current={details ? "page" : undefined} onClick={() => setDetails(true)}><T text="Branch details" /></button>
        </nav>

        {details ? <BranchDetails branch={branch} /> : <>
            {branch.pickupAvailable && <section className="branch-pickup-guide" aria-labelledby="branch-pickup-title">
                <div><p className="gokul-overline"><T text="A little planning. An easy pickup." /></p><h2 id="branch-pickup-title"><T text="Your order, ready to collect" /></h2><p><T text="Online orders are for pickup at this branch. Choose an available time at checkout." /></p></div>
                <ol>{[
                    ["Choose your favourites", "Browse the menu and add what you love."],
                    ["Choose pickup & pay", "Select an available slot and pay online."],
                    ["Collect at this branch", "Follow your order status and show your pickup code."],
                ].map(([title, description], index) => <li key={title}><span aria-hidden="true">{index + 1}</span><div><h3><T text={title} /></h3><p><T text={description} /></p></div></li>)}</ol>
            </section>}
            <BranchDiscovery branch={branch} selected={selected} />
        </>}
    </article>;
}
