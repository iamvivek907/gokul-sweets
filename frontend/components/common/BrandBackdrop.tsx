/** Small, decorative inline artwork: no image downloads or loading dependencies. */
export default function BrandBackdrop() {
    return <div className="brand-backdrop" aria-hidden="true">
        <svg className="brand-sprig" viewBox="0 0 120 160" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round"><path d="M8 150C35 100 65 65 105 10M28 118C5 103 8 81 12 67c20 13 25 29 16 51Zm17-28C26 72 32 53 39 40c15 17 19 32 6 50Zm21-29C53 40 62 23 72 12c10 20 10 36-6 49ZM34 108c25 5 44-8 53-25-23-3-43 3-53 25Zm23-33c25 5 43-9 52-27-24-2-42 6-52 27Z"/></svg>
        <svg className="brand-mithai" viewBox="0 0 160 120" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinejoin="round"><path d="m20 47 38-22 40 21-39 24-39-23Zm0 0v28l39 23 39-24V46M59 70v28M80 32l35-19 32 19-34 20M98 44l15 8v27l34-20V32"/><path d="m46 44 6-4 7 4-7 4-6-4Zm26 5 5-3 5 3-5 3-5-3Zm37-18 5-3 5 3-5 3-5-3Z"/></svg>
        <svg className="brand-ladoo" viewBox="0 0 160 120" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round"><path d="M10 83c31 31 109 31 140 0M16 81c-5-33 39-45 49-12M57 74c-7-40 47-53 59-14M103 78c-2-29 38-35 42-5M10 82c27 14 111 14 140 0M30 60h1m9-8h1m-4 15h1m44-17h1m8-7h1m-3 20h1m34 4h1m7 9h1"/></svg>
        <svg className="brand-tea" viewBox="0 0 100 120" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"><path d="M18 51h52v22c0 17-12 25-26 25S18 90 18 73V51Zm52 6h9c18 0 18 23 0 23h-9M11 104h69M32 13c-12 15 12 17 0 31m23-31c-12 15 12 17 0 31"/></svg>
    </div>;
}
