"use client";
import {T} from "@/lib/language";

import {useState} from "react";
import type {Branch} from "@/types/branch";

export default function BranchDetails({branch}: {branch: Branch}) {
    const [failedImage, setFailedImage] = useState(false);
    return <section className="branch-details-panel" aria-label={`${branch.name} details`}>
        {!failedImage && branch.coverImageUrl && <picture>
            {branch.mobileCoverImageUrl && <source media="(max-width: 700px)" srcSet={branch.mobileCoverImageUrl} />}
            <img src={branch.coverImageUrl} alt={branch.coverAltText || branch.name}
                onError={() => setFailedImage(true)} className="branch-details-photo" />
        </picture>}
        <div className="branch-details-content">
            <p className="gokul-overline">Visit us</p><h2>{branch.name}</h2>
            {branch.description && <p>{branch.description}</p>}
            <div className="branch-details-facts">
                <div><h3>Address</h3><p>{[branch.address, branch.city, branch.state, branch.pincode].filter(Boolean).join(", ") || "Address being updated"}</p></div>
                <div><h3>Opening hours</h3><p>{branch.openingTime && branch.closingTime ? `${branch.openingTime.slice(0, 5)}–${branch.closingTime.slice(0, 5)} IST` : "Contact the branch for hours"}</p></div>
                {branch.phone && <div><h3>Telephone</h3><a href={`tel:${branch.phone}`}>{branch.phone}</a></div>}
                <div><h3><T text="Pickup" /></h3><p>{branch.pickupAvailable ? "Online pickup available" : "Online pickup currently unavailable"}</p></div>
            </div>
        </div>
    </section>;
}
