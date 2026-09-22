"use client";

import {
    useEffect,
    useId,
    useMemo,
    useRef,
    useState
} from "react";
import {
    INVENTORY_HELP,
    inventoryHelpDefinition,
    type InventoryHelpContext
} from "@/lib/inventoryHelp";

export function InventoryColumnHeader({
    label,
    helpKey
}: {
    label: string;
    helpKey: string;
}) {
    return (
        <span className="inline-flex items-center gap-1.5">
            {label}
            <InventoryInfo helpKey={helpKey} />
        </span>
    );
}

export function InventoryInfo({
    helpKey
}: {
    helpKey: string;
}) {
    const help = inventoryHelpDefinition(helpKey);
    const instanceId = useId();
    const containerRef = useRef<HTMLSpanElement>(null);
    const [open, setOpen] = useState(false);

    useEffect(() => {
        function closeWhenAnotherOpens(event: Event) {
            const customEvent = event as CustomEvent<string>;
            if (customEvent.detail !== instanceId) setOpen(false);
        }

        function closeOnOutsidePointer(event: PointerEvent) {
            if (!containerRef.current?.contains(event.target as Node)) {
                setOpen(false);
            }
        }

        function closeOnEscape(event: KeyboardEvent) {
            if (event.key === "Escape") setOpen(false);
        }

        window.addEventListener(
            "gokul-inventory-help-open",
            closeWhenAnotherOpens
        );
        document.addEventListener("pointerdown", closeOnOutsidePointer);
        document.addEventListener("keydown", closeOnEscape);

        return () => {
            window.removeEventListener(
                "gokul-inventory-help-open",
                closeWhenAnotherOpens
            );
            document.removeEventListener("pointerdown", closeOnOutsidePointer);
            document.removeEventListener("keydown", closeOnEscape);
        };
    }, [instanceId]);

    if (!help) return null;

    function toggleHelp(event: React.MouseEvent<HTMLButtonElement>) {
        event.preventDefault();
        event.stopPropagation();

        const nextOpen = !open;
        if (nextOpen) {
            window.dispatchEvent(
                new CustomEvent<string>(
                    "gokul-inventory-help-open",
                    {detail: instanceId}
                )
            );
        }
        setOpen(nextOpen);
    }

    return (
        <span
            ref={containerRef}
            className="relative inline-block normal-case"
        >
            <button
                type="button"
                onClick={toggleHelp}
                className="inline-flex h-5 w-5 cursor-pointer list-none items-center justify-center rounded-full border border-[#cbb9ad] bg-white text-[11px] font-bold text-[#7a1625] marker:content-none hover:border-[#7a1625] focus:outline-none focus:ring-2 focus:ring-[#7a1625]/20"
                aria-label={`Help for ${help.label}`}
                title={`Help for ${help.label}`}
                aria-expanded={open}
                aria-controls={`${instanceId}-content`}
            >
                i
            </button>
            {open && (
                <div
                    id={`${instanceId}-content`}
                    className="absolute left-1/2 top-7 z-[60] w-72 max-w-[calc(100vw-2rem)] -translate-x-1/2 rounded-2xl border border-[#eadfd6] bg-white p-4 text-left shadow-xl"
                    role="tooltip"
                    onPointerDown={event => event.stopPropagation()}
                >
                    <p className="text-sm font-bold text-[#241715]">{help.label}</p>
                    <p className="mt-2 text-xs font-normal leading-5 text-[#756763]">
                        {help.definition}
                    </p>
                    <div className="mt-3 rounded-xl bg-[#fff4e5] p-3 text-xs font-medium leading-5 text-[#7a1625]">
                        <span className="font-bold">What to enter: </span>
                        {help.guidance}
                    </div>
                </div>
            )}
        </span>
    );
}

export default function InventoryHelp({
    context
}: {
    context: InventoryHelpContext;
}) {
    const [open, setOpen] = useState(false);
    const definitions = useMemo(
        () => INVENTORY_HELP.filter(item => item.context.includes(context)),
        [context]
    );

    return (
        <>
            <button
                type="button"
                onClick={() => setOpen(true)}
                className="inline-flex min-h-11 w-fit items-center justify-center gap-2 rounded-xl border border-[#eadfd6] bg-white px-4 text-sm font-bold text-[#7a1625] transition hover:border-[#c88a20] hover:bg-[#fffaf3]"
            >
                <span aria-hidden="true">?</span>
                Inventory guide
            </button>

            {open && (
                <div
                    className="fixed inset-0 z-[70] flex items-end justify-center bg-black/45 sm:items-center sm:p-6"
                    role="dialog"
                    aria-modal="true"
                    aria-labelledby="inventory-guide-title"
                    onMouseDown={event => {
                        if (event.currentTarget === event.target) setOpen(false);
                    }}
                >
                    <div className="max-h-[92vh] w-full max-w-4xl overflow-y-auto rounded-t-3xl bg-white p-5 shadow-2xl sm:rounded-3xl sm:p-7">
                        <div className="flex items-start justify-between gap-4">
                            <div>
                                <p className="text-xs font-bold uppercase tracking-[0.16em] text-[#c88a20]">
                                    Operating guide
                                </p>
                                <h2 id="inventory-guide-title" className="mt-1 text-2xl font-bold text-[#241715]">
                                    How inventory works
                                </h2>
                                <p className="mt-2 max-w-2xl text-sm leading-6 text-[#756763]">
                                    Configure the product rule once, allocate what can safely be sold for each date, then record what is physically ready. Holds and commitments are maintained by the system.
                                </p>
                            </div>
                            <button
                                type="button"
                                onClick={() => setOpen(false)}
                                className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-[#fff4e5] text-xl text-[#7a1625]"
                                aria-label="Close inventory guide"
                            >
                                ×
                            </button>
                        </div>

                        <div className="mt-6 grid gap-3 sm:grid-cols-3">
                            {[
                                ["1", "Configure", "Define units, safe maximum, buffer, lead time, shelf life, and booking horizon."],
                                ["2", "Allocate", "Open a safe quantity for a particular branch and pickup date."],
                                ["3", "Operate", "Record production, readiness, corrections, and wastage from actual physical stock."]
                            ].map(([number, title, text]) => (
                                <div key={number} className="rounded-2xl border border-[#eadfd6] bg-[#fffaf3] p-4">
                                    <span className="inline-flex h-7 w-7 items-center justify-center rounded-full bg-[#7a1625] text-xs font-bold text-white">
                                        {number}
                                    </span>
                                    <h3 className="mt-3 font-bold text-[#241715]">{title}</h3>
                                    <p className="mt-1 text-xs leading-5 text-[#756763]">{text}</p>
                                </div>
                            ))}
                        </div>

                        <div className="mt-6 rounded-2xl border border-blue-200 bg-blue-50 p-4 text-sm leading-6 text-blue-900">
                            <strong>Never manually calculate Available online.</strong> The system derives it from approved/ready stock, safety buffer, pending-payment holds, and confirmed commitments.
                        </div>

                        <h3 className="mt-7 text-lg font-bold text-[#241715]">Field and column definitions</h3>
                        <div className="mt-3 divide-y divide-[#eadfd6] rounded-2xl border border-[#eadfd6]">
                            {definitions.map(item => (
                                <div key={item.key} className="grid gap-1 p-4 md:grid-cols-[190px_1fr] md:gap-5">
                                    <p className="font-bold text-[#241715]">{item.label}</p>
                                    <div>
                                        <p className="text-sm leading-6 text-[#756763]">{item.definition}</p>
                                        <p className="mt-1 text-xs font-medium leading-5 text-[#7a1625]">
                                            What to do: {item.guidance}
                                        </p>
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>
                </div>
            )}
        </>
    );
}
