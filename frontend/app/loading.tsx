export default function Loading() {
    return <div role="status" aria-live="polite" className="fixed inset-0 z-[9999] grid min-h-dvh place-items-center bg-[#123b36] px-6 text-[#f7f4ed]">
        <div className="w-full max-w-sm text-center">
            <span className="font-serif text-5xl tracking-tight sm:text-6xl">Gokul</span>
            <span className="mt-1 block text-xs font-semibold uppercase tracking-[0.25em] text-[#e9bd9f]">Sweets &amp; Restaurants</span>
            <div aria-hidden="true" className="mx-auto mt-9 h-1 w-40 overflow-hidden rounded-full bg-white/20">
                <div className="h-full w-1/2 animate-pulse rounded-full bg-[#e5aa83] motion-reduce:animate-none" />
            </div>
            <p className="mt-5 text-sm text-[#e5ebe4]">Preparing your visit…</p>
        </div>
    </div>;
}
