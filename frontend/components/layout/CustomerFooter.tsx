import Link from "next/link";

export default function CustomerFooter() {
    return <footer aria-label="Customer footer" className="customer-site-footer border-t border-[#eadfd6] bg-white px-4 pt-6 pb-[calc(90px+env(safe-area-inset-bottom))]">
        <div className="gokul-footer-grid">
            <div><h2>Gokul Sweets</h2><p>Freshly made for the moments that matter. Order online, then collect from your chosen branch.</p></div>
            <div><h3>EXPLORE</h3><Link href="/menu">Menu</Link><Link href="/">Branches</Link><Link href="/about">Our story</Link></div>
            <div><h3>YOUR ORDER</h3><Link href="/orders">Orders</Link><Link href="/profile">Profile</Link><span>Pickup only</span></div>
            <div><h3>GOOD TO KNOW</h3><span>Choose a branch to see its live menu.</span><span>Pickup times and the full price are confirmed before payment.</span></div>
        </div>
        <div className="gokul-footer-bottom"><span>© Gokul Sweets</span><span>Made with care, ready for pickup.</span></div>
    </footer>;
}
