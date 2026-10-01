"use client";
import {T} from "@/lib/language";


import Image from "next/image";
import Link from "next/link";
import {useEffect, useState} from "react";
import {useStorefrontConfiguration} from "@/hooks/useStorefrontFeatures";
import styles from "./StorefrontWelcome.module.css";

export default function StorefrontWelcome() {
    const {retry} = useStorefrontConfiguration();
    const [exploring, setExploring] = useState(false);
    useEffect(() => {
        const timer = window.setTimeout(() => setExploring(true), 2500);
        return () => window.clearTimeout(timer);
    }, []);
    return <section className={styles.welcome} aria-label="Welcome to Gokul Sweets">
        <div className={styles.hero}>
            <div className={styles.copy}>
                <p className={styles.eyebrow}><T text="Gokul Sweets" /></p>
                <h1>A little sweetness is on its way.</h1>
                <p>Welcome in. Take a moment to explore while we get online ordering ready.</p>
                {!exploring && <div role="status" className={styles.preparing}><span aria-hidden="true" />Getting everything ready for you…</div>}
                <a href="#explore-gokul" className={styles.primary} onClick={() => setExploring(true)}>Explore Gokul</a>
            </div>
            <Image className={styles.photo} src="/arrival-mithai.webp" alt="A selection of sweets" width={640} height={640} priority />
        </div>
        {exploring && <div role="status" className={styles.notice}>
            <div><strong><T text="Online ordering is taking longer to connect." /></strong><p>You can keep exploring. We’ll enable live ordering automatically when ready. Your cart is saved.</p></div>
            <button type="button" onClick={retry}><T text="Try again" /></button>
        </div>}
        <div id="explore-gokul" className={styles.explore}>
            <p className={styles.eyebrow}>Plan your visit</p><h2>Good moments start with Gokul.</h2>
            <div className={styles.cards}>
                <article><span>01</span><h3>Choose your branch</h3><p>When ordering reconnects, choose where you want to collect your favourites.</p></article>
                <article><span>02</span><h3><T text="Find your favourites" /></h3><p>Live menu prices and availability will be checked at your selected branch.</p></article>
                <article><span>03</span><h3>Collect at your convenience</h3><p>Review the total and confirm an available pickup time before payment.</p></article>
            </div>
            <nav aria-label="Useful information" className={styles.links}>
                <Link href="/about#privacy-policy">Privacy policy</Link><Link href="/about#terms-and-conditions">Terms & conditions</Link><Link href="/about#refund-policy">Refund policy</Link>
            </nav>
        </div>
    </section>;
}
