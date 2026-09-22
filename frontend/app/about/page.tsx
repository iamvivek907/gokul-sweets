"use client";

import Link from "next/link";
import {useEffect, useState, type ReactNode} from "react";

import AppShell from "@/components/layout/AppShell";
import {getActiveBranches} from "@/services/branchApi";
import type {Branch} from "@/types/branch";

const INSTAGRAM_URL = "https://www.instagram.com/_gokulsweets";
const FACEBOOK_URL = "https://www.facebook.com/visitgokulsweets";

const POLICY_LINKS = [
    ["pickup-policy", "Pickup Policy"],
    ["cancellation-policy", "Cancellation Policy"],
    ["refund-policy", "Refund Policy"],
    ["privacy-policy", "Privacy Policy"],
    ["terms-and-conditions", "Terms & Conditions"]
] as const;

function formatTime(value: string | null): string | null {
    if (!value) return null;
    const [hour, minute] = value.split(":").map(Number);
    if (!Number.isFinite(hour) || !Number.isFinite(minute)) return value;
    const date = new Date();
    date.setHours(hour, minute, 0, 0);
    return new Intl.DateTimeFormat("en-IN", {
        hour: "numeric",
        minute: "2-digit"
    }).format(date);
}

function formatAddress(branch: Branch): string {
    return [branch.address, branch.city, branch.state, branch.pincode]
        .filter((value): value is string => Boolean(value?.trim()))
        .join(", ");
}

function directionsUrl(branch: Branch): string | null {
    const query = branch.latitude !== null && branch.longitude !== null
        ? `${branch.latitude},${branch.longitude}`
        : formatAddress(branch);
    return query
        ? `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(query)}`
        : null;
}

function BranchCard({branch}: {branch: Branch}) {
    const address = formatAddress(branch);
    const opening = formatTime(branch.openingTime);
    const closing = formatTime(branch.closingTime);
    const mapUrl = directionsUrl(branch);

    return (
        <article className="rounded-3xl border border-[#eadfd6] bg-white p-5 shadow-[0_5px_18px_rgba(60,30,20,0.06)] sm:p-6">
            <div className="flex items-start justify-between gap-4">
                <div className="min-w-0">
                    <p className="text-xs font-bold uppercase tracking-[0.14em] text-[#c88a20]">Pickup location</p>
                    <h3 className="mt-1 text-xl font-extrabold text-[#241715]">{branch.name}</h3>
                </div>
                <span className="shrink-0 rounded-full bg-green-50 px-3 py-1 text-xs font-bold text-green-700">Active</span>
            </div>

            <div className="mt-5 grid gap-4 sm:grid-cols-2">
                {address && (
                    <div>
                        <p className="text-xs font-bold uppercase tracking-wide text-[#756763]">Address</p>
                        <p className="mt-1 selectable-text text-sm leading-6 text-[#241715]">{address}</p>
                    </div>
                )}
                {opening && closing && (
                    <div>
                        <p className="text-xs font-bold uppercase tracking-wide text-[#756763]">Opening hours</p>
                        <p className="mt-1 text-sm font-semibold text-[#241715]">{opening} – {closing}</p>
                    </div>
                )}
                {branch.phone && (
                    <div>
                        <p className="text-xs font-bold uppercase tracking-wide text-[#756763]">Telephone</p>
                        <a href={`tel:${branch.phone}`} className="mt-1 inline-block selectable-text text-sm font-bold text-[#7a1625]!">{branch.phone}</a>
                    </div>
                )}
            </div>

            <div className="mt-5 flex flex-wrap gap-3">
                {mapUrl && (
                    <a href={mapUrl} target="_blank" rel="noreferrer" className="flex min-h-11 flex-1 items-center justify-center rounded-xl border border-[#7a1625] bg-white px-4 text-sm font-bold text-[#7a1625]!">Get Directions</a>
                )}
                {branch.phone && (
                    <a href={`tel:${branch.phone}`} className="flex min-h-11 flex-1 items-center justify-center rounded-xl bg-[#7a1625] px-4 text-sm font-bold text-white!">Call Branch</a>
                )}
            </div>
        </article>
    );
}

function PolicyPoint({title, children, important = false}: {title: string; children: ReactNode; important?: boolean}) {
    return (
        <div className={important
            ? "rounded-2xl border border-[#e8c67a] bg-[#fff8e8] px-4 py-3"
            : "flex items-start gap-3"
        }>
            {!important && <span className="mt-[11px] h-1.5 w-1.5 shrink-0 rounded-full bg-[#7a1625]" />}
            <p><strong className="text-[#241715]">{title}:</strong> {children}</p>
        </div>
    );
}

function PolicyCard({id, symbol, title, description, children}: {
    id: string;
    symbol: string;
    title: string;
    description: string;
    children: ReactNode;
}) {
    return (
        <section id={id} className="scroll-mt-28 rounded-3xl border border-[#eadfd6] bg-white p-5 shadow-[0_4px_18px_rgba(60,30,20,0.05)] sm:p-7">
            <div className="flex items-start gap-4">
                <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-[#7a1625]/8 text-xl font-extrabold text-[#7a1625]">{symbol}</div>
                <div className="min-w-0">
                    <h3 className="text-xl font-extrabold tracking-tight text-[#241715]">{title}</h3>
                    <p className="mt-1 text-sm leading-6 text-[#756763]">{description}</p>
                </div>
            </div>
            <div className="mt-5 space-y-3 text-sm leading-7 text-[#5f514d] sm:pl-16">{children}</div>
        </section>
    );
}

function QuickFact({symbol, title, message}: {symbol: string; title: string; message: string}) {
    return (
        <div className="rounded-2xl border border-[#eadfd6] bg-white/80 p-4">
            <span className="text-xl text-[#7a1625]">{symbol}</span>
            <p className="mt-2 text-sm font-extrabold text-[#241715]">{title}</p>
            <p className="mt-1 text-xs leading-5 text-[#756763]">{message}</p>
        </div>
    );
}

function PolicyCenter() {
    return (
        <section id="policies" className="mt-8 scroll-mt-24">
            <div className="rounded-3xl border border-[#eadfd6] bg-white px-5 py-7 sm:px-8">
                <p className="text-xs font-bold uppercase tracking-[0.16em] text-[#c88a20]">Clear and transparent</p>
                <h2 className="mt-2 text-3xl font-extrabold tracking-tight text-[#7a1625]">Policies &amp; Customer Care</h2>
                <p className="mt-3 max-w-2xl text-sm leading-7 text-[#756763] sm:text-base">Everything you need to know about ordering, collecting, cancelling and requesting help with a Gokul Sweets order.</p>
            </div>

            <div className="mt-5 grid gap-5 lg:grid-cols-[240px_minmax(0,1fr)]">
                <aside className="min-w-0">
                    <nav aria-label="Policy navigation" className="no-scrollbar flex gap-2 overflow-x-auto rounded-2xl border border-[#eadfd6] bg-white p-3 lg:sticky lg:top-24 lg:block lg:space-y-1 lg:overflow-visible lg:p-4">
                        <p className="hidden px-3 pb-3 text-sm font-extrabold text-[#241715] lg:block">On this page</p>
                        {POLICY_LINKS.map(([id, label]) => (
                            <a key={id} href={`#${id}`} className="flex min-h-10 shrink-0 items-center rounded-xl px-3 text-sm font-semibold text-[#756763] transition hover:bg-[#fff4e5] hover:text-[#7a1625] lg:w-full">{label}</a>
                        ))}
                    </nav>
                </aside>

                <div className="min-w-0">
                    <div className="rounded-3xl border border-[#ead19a] bg-linear-to-br from-[#fff8e8] to-[#fffdf7] p-5 sm:p-7">
                        <p className="text-xs font-extrabold uppercase tracking-[0.16em] text-[#c88a20]">At a glance</p>
                        <h3 className="mt-1 text-xl font-extrabold text-[#7a1625]">Quick things to know</h3>
                        <div className="mt-5 grid gap-3 sm:grid-cols-3">
                            <QuickFact symbol="⌂" title="Pickup only" message="Website orders must be collected from the selected branch." />
                            <QuickFact symbol="▣" title="Online payment" message="All website orders are paid securely during checkout." />
                            <QuickFact symbol="◷" title="Report within 2 hours" message="Tell us promptly about quality, damage or incorrect items." />
                        </div>
                    </div>

                    <div className="mt-5 space-y-4">
                        <PolicyCard id="pickup-policy" symbol="⌂" title="Pickup Policy" description="Website orders are prepared for collection from your selected branch.">
                            <PolicyPoint title="Pickup location">Collect from the branch selected while placing the order.</PolicyPoint>
                            <PolicyPoint title="Pickup window">Please arrive during the reserved date and time shown in your order.</PolicyPoint>
                            <PolicyPoint title="Order identification">Provide the order number and registered mobile number during collection.</PolicyPoint>
                            <PolicyPoint title="Order inspection">Check your order at pickup and immediately report missing, incorrect or visibly damaged items.</PolicyPoint>
                        </PolicyCard>

                        <PolicyCard id="cancellation-policy" symbol="▦" title="Cancellation Policy" description="Cancellation availability depends on the products included in the order.">
                            <PolicyPoint title="Weight or mixed orders" important>Cancel at least <strong className="text-[#7a1625]">24 hours before</strong> the scheduled pickup.</PolicyPoint>
                            <PolicyPoint title="Unit-only orders" important>Cancel at least <strong className="text-[#7a1625]">2 hours before</strong> the scheduled pickup.</PolicyPoint>
                            <PolicyPoint title="Preparation started">Cancellation may be unavailable once preparation has begun or the applicable cutoff has passed.</PolicyPoint>
                        </PolicyCard>

                        <PolicyCard id="refund-policy" symbol="₹" title="Refund Policy" description="Eligible refunds are processed securely to the original payment method.">
                            <PolicyPoint title="No-show orders">Refunds are not available when an order is not collected and is marked as a no-show.</PolicyPoint>
                            <PolicyPoint title="Quality concerns">Wrong, damaged or quality-related concerns must be reported at pickup or within 2 hours of collection.</PolicyPoint>
                            <PolicyPoint title="Approved refunds">The amount is returned to the original payment method. Your bank or payment provider may require additional processing time.</PolicyPoint>
                        </PolicyCard>

                        <PolicyCard id="privacy-policy" symbol="◇" title="Privacy Policy" description="We use customer information only where it is needed to operate and support orders.">
                            <PolicyPoint title="Information collected">We collect your name, mobile number, selected branch, order details and payment references.</PolicyPoint>
                            <PolicyPoint title="Payment security">Card, banking and UPI credentials are handled by the payment provider and are not stored by Gokul Sweets.</PolicyPoint>
                            <PolicyPoint title="How information is used">Information may be used for fulfilment, pickup verification, support, refunds, fraud prevention and required records.</PolicyPoint>
                        </PolicyCard>

                        <PolicyCard id="terms-and-conditions" symbol="▤" title="Terms and Conditions" description="Please review these terms before completing your order.">
                            <PolicyPoint title="Prices and availability">Product availability, price and final payable amount are confirmed by the server when the order is created.</PolicyPoint>
                            <PolicyPoint title="Product appearance">Product images are illustrative. Handmade products can have natural differences in colour, shape and presentation.</PolicyPoint>
                            <PolicyPoint title="Order fulfilment">Gokul Sweets may reject or refund an order when stock, payment, food-safety or operational constraints prevent fulfilment.</PolicyPoint>
                        </PolicyCard>
                    </div>
                </div>
            </div>
        </section>
    );
}

export default function AboutPage() {
    const [branches, setBranches] = useState<Branch[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [reloadKey, setReloadKey] = useState(0);

    useEffect(() => {
        const controller = new AbortController();

        void getActiveBranches(controller.signal)
            .then(result => {
                if (controller.signal.aborted) return;
                setBranches(result.filter(branch => branch.active !== false));
                setError(null);
            })
            .catch(exception => {
                if (controller.signal.aborted) return;
                console.error("Unable to load branch details:", exception);
                setError(exception instanceof Error ? exception.message : "Unable to load branch details.");
            })
            .finally(() => {
                if (!controller.signal.aborted) setLoading(false);
            });

        return () => controller.abort();
    }, [reloadKey]);

    function retryBranches(): void {
        setLoading(true);
        setError(null);
        setReloadKey(value => value + 1);
    }

    return (
        <AppShell>
            <div className="mx-auto w-full max-w-6xl pb-12">
                <section className="overflow-hidden rounded-3xl border border-[#eadfd6] bg-white shadow-[0_8px_30px_rgba(60,30,20,0.08)]">
                    <div className="bg-linear-to-br from-[#fff4e5] via-[#fffaf3] to-[#f6dfad]/40 px-6 py-9 sm:px-9 sm:py-12">
                        <p className="text-xs font-bold uppercase tracking-[0.16em] text-[#c88a20]">Our story</p>
                        <h1 className="mt-2 text-3xl font-extrabold tracking-tight text-[#7a1625] sm:text-5xl">More than sweets.<br />It&apos;s a feeling.</h1>
                        <p className="mt-4 max-w-2xl text-sm leading-7 text-[#756763] sm:text-base">Gokul Sweets prepares fresh sweets, savouries and food for moments shared with family and friends. Choose your favourites, pay securely and collect your order from your selected branch.</p>
                        <div className="mt-6 flex flex-wrap gap-3">
                            <Link href="/menu" className="flex min-h-12 items-center justify-center rounded-xl bg-[#7a1625] px-6 font-bold text-white!">Browse Menu</Link>
                            <a href="#our-branches" className="flex min-h-12 items-center justify-center rounded-xl border border-[#eadfd6] bg-white px-6 font-bold text-[#7a1625]!">Find a Branch</a>
                        </div>
                    </div>
                </section>

                <section id="our-branches" className="mt-8 scroll-mt-24">
                    <p className="text-xs font-bold uppercase tracking-[0.14em] text-[#c88a20]">Visit us</p>
                    <h2 className="mt-1 text-3xl font-extrabold text-[#241715]">Our Branches</h2>
                    <p className="mt-2 text-sm leading-6 text-[#756763]">Choose your preferred pickup location while placing the order.</p>

                    {loading && <div className="mt-5 grid gap-4 md:grid-cols-2">{[1, 2].map(item => <div key={item} className="h-64 animate-pulse rounded-3xl bg-[#f4ebe4]" />)}</div>}

                    {!loading && error && (
                        <div className="mt-5 rounded-2xl border border-red-200 bg-red-50 p-5 text-center">
                            <p className="text-sm text-red-700">{error}</p>
                            <button type="button" onClick={retryBranches} className="mt-4 min-h-11 rounded-xl bg-[#7a1625] px-5 text-sm font-bold text-white!">Try Again</button>
                        </div>
                    )}

                    {!loading && !error && branches.length === 0 && <div className="mt-5 rounded-2xl bg-[#fff4e5] p-5 text-sm text-[#756763]">Branch information is currently unavailable.</div>}

                    {!loading && !error && branches.length > 0 && (
                        <div className="mt-5 grid gap-4 md:grid-cols-2">{branches.map(branch => <BranchCard key={branch.id} branch={branch} />)}</div>
                    )}
                </section>

                <PolicyCenter />

                <section className="mt-8 rounded-3xl bg-[#7a1625] p-6 text-white sm:p-8">
                    <div className="grid gap-6 lg:grid-cols-[1fr_auto] lg:items-center">
                        <div>
                            <p className="text-xs font-bold uppercase tracking-[0.14em] text-[#f6dfad]">Stay connected</p>
                            <h2 className="mt-1 text-2xl font-extrabold">Follow Gokul Sweets</h2>
                            <p className="mt-2 text-sm leading-6 text-white/80">Discover festive specials, new products and updates from our shops.</p>
                        </div>
                        <div className="grid gap-3 sm:grid-cols-2 lg:min-w-[440px]">
                            <a href={INSTAGRAM_URL} target="_blank" rel="noreferrer" className="flex min-h-12 items-center justify-center rounded-xl bg-white px-5 text-sm font-bold text-[#7a1625]!">Follow on Instagram</a>
                            <a href={FACEBOOK_URL} target="_blank" rel="noreferrer" className="flex min-h-12 items-center justify-center rounded-xl border border-white/40 px-5 text-sm font-bold text-white!">Follow on Facebook</a>
                        </div>
                    </div>
                </section>
            </div>
        </AppShell>
    );
}
