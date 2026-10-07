/** Compact wordmark from the approved menu reference; the shell owns its link. */
export default function ReferenceWordmark({className="reference-wordmark"}:{className?:string}={}) {
    return <span className={className} aria-hidden="true">
        <svg viewBox="0 0 40 44" fill="none">
            <g stroke="currentColor" strokeWidth="2" strokeLinejoin="round">
                <path d="M20 40C12 33 5 28 5 19c7-1 12 3 15 9 3-6 8-10 15-9 0 9-7 14-15 21Z"/>
                <path d="M20 40V28M20 28c-5-6-7-12-4-20 5 3 8 10 4 20Z" fill="currentColor"/>
                <path d="M14 20C7 17 6 11 8 6c5 1 9 6 8 12M25 20c7-3 8-9 6-14-5 1-9 6-8 12" fill="currentColor"/>
                <path d="m20 3-3 4 3 3 3-3-3-4Z" fill="currentColor"/>
            </g>
        </svg>
        <span><strong>GOKUL</strong><small>SWEETS</small></span>
    </span>;
}
