"use client";

import {useId, useRef} from "react";

/** Touch and keyboard help stays in the dialog top layer, clear of clipped admin panels. */
export default function AdminHelp({title, description, guidance}: {
    title: string;
    description: string;
    guidance: string;
}) {
    const id = useId();
    const dialog = useRef<HTMLDialogElement>(null);
    return <>
        <button type="button" aria-label={`Help for ${title}`} aria-haspopup="dialog" aria-controls={id}
            onClick={() => dialog.current?.showModal()}
            className="inline-flex h-11 w-11 shrink-0 items-center justify-center rounded-full text-[#143936] hover:bg-[#e7f0e9] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#143936]">
            <span aria-hidden="true" className="flex h-6 w-6 items-center justify-center rounded-full border border-current text-sm font-bold">i</span>
        </button>
        <dialog ref={dialog} id={id} aria-labelledby={`${id}-title`} aria-describedby={`${id}-description`}
            className="m-auto max-h-[calc(100dvh_-_2rem)] w-[calc(100vw_-_2rem)] max-w-md overflow-y-auto rounded-2xl border border-[#c4d4c9] bg-[#fffaf2] p-5 text-left text-[#143936] shadow-xl backdrop:bg-black/40"
            onClick={event => {
                if (event.target !== event.currentTarget) return;
                const box = event.currentTarget.getBoundingClientRect();
                if (event.clientX < box.left || event.clientX > box.right || event.clientY < box.top || event.clientY > box.bottom) event.currentTarget.close();
            }}>
            <div className="flex items-start justify-between gap-3">
                <h2 id={`${id}-title`} className="min-w-0 break-words pt-2 text-lg font-bold">{title}</h2>
                <button type="button" onClick={() => dialog.current?.close()}
                    className="min-h-11 shrink-0 rounded-xl border border-[#143936] px-3 text-sm font-bold">Close</button>
            </div>
            <p id={`${id}-description`} className="mt-3 text-sm font-normal leading-6">{description}</p>
            <div className="mt-4 rounded-xl bg-[#e7f0e9] p-4 text-sm font-normal leading-6">
                <p className="font-bold">What to enter or do</p>
                <p className="mt-1">{guidance}</p>
            </div>
        </dialog>
    </>;
}
