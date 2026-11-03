"use client";

import {useEffect, useRef, useState, type ChangeEvent, type PointerEvent} from "react";
import {useAdminAuth} from "@/contexts/AdminAuthContext";
import {adminManagementApi} from "@/services/adminManagementApi";

type Point = {x: number; y: number};
type Frame = Point & {zoom: number};
type IconVersion = {revision: string | null};
const centered: Frame = {x: .5, y: .5, zoom: 1};
const clamp = (value: number, min: number, max: number) => Math.min(max, Math.max(min, value));
const distance = (a: Point, b: Point) => Math.hypot(a.x - b.x, a.y - b.y);
const midpoint = (a: Point, b: Point) => ({x: (a.x + b.x) / 2, y: (a.y + b.y) / 2});
const adminPath = "/api/admin/app-icon";

export default function AppIconPage() {
    const {profile, authorization} = useAdminAuth();
    const owner = profile?.roleName === "OWNER_ADMIN";
    const [revision, setRevision] = useState<string | null>(null);
    const [image, setImage] = useState<HTMLImageElement | null>(null);
    const [busy, setBusy] = useState(false);
    const [decoding, setDecoding] = useState(false);
    const [confirmRestore, setConfirmRestore] = useState(false);
    const [message, setMessage] = useState("");
    const [error, setError] = useState("");
    const generation = useRef(0);
    const writeLock = useRef(false);
    const canvas = useRef<HTMLCanvasElement>(null);
    const frame = useRef<Frame>(centered);
    const touches = useRef(new Map<number, Point>());

    useEffect(() => {
        if (!owner || !authorization) return;
        const controller = new AbortController();
        adminManagementApi<IconVersion>("/api/storefront/app-icon", authorization, {signal: controller.signal})
            .then(value => {if (!controller.signal.aborted && !writeLock.current) setRevision(value.revision);})
            .catch(() => {if (!controller.signal.aborted) setError("Could not load the published icon.");});
        return () => controller.abort();
    }, [owner, authorization]);

    useEffect(() => {
        const surface = canvas.current;
        if (!surface || !image) return;
        const context = surface.getContext("2d");
        if (!context) return;
        const paint = () => {
            const {x, y, zoom} = frame.current;
            const scale = 512 * zoom / Math.max(image.width, image.height);
            const width = image.width * scale, height = image.height * scale;
            context.fillStyle = "#ffffff";
            context.fillRect(0, 0, 512, 512);
            context.imageSmoothingQuality = "high";
            context.drawImage(image, x * 512 - width / 2, y * 512 - height / 2, width, height);
        };
        paint();
        const redraw = () => paint();
        surface.addEventListener("icon-frame", redraw);
        const wheel = (event: WheelEvent) => {
            if (!event.ctrlKey || writeLock.current) return;
            event.preventDefault();
            frame.current = {...frame.current, zoom: clamp(frame.current.zoom * Math.exp(-event.deltaY / 140), .5, 5)};
            paint();
        };
        surface.addEventListener("wheel", wheel, {passive: false});
        return () => {
            surface.removeEventListener("icon-frame", redraw);
            surface.removeEventListener("wheel", wheel);
        };
    }, [image]);

    async function choose(event: ChangeEvent<HTMLInputElement>) {
        const file = event.target.files?.[0];
        event.target.value = "";
        if (!file) return;
        setError(""); setMessage("");
        if (!["image/png", "image/jpeg", "image/webp"].includes(file.type) || file.size > 8 * 1024 * 1024) {
            setError("Choose a PNG, JPG or WebP up to 8 MB.");
            return;
        }
        const attempt = ++generation.current;
        setDecoding(true);
        const url = URL.createObjectURL(file);
        try {
            const draft = new Image();
            draft.src = url;
            await draft.decode();
            if (attempt !== generation.current) return;
            if (Math.max(draft.naturalWidth, draft.naturalHeight) > 4096) {
                setError("Choose an image no larger than 4096 pixels on either side.");
                return;
            }
            frame.current = centered;
            touches.current.clear();
            setConfirmRestore(false);
            setImage(draft);
        } catch {
            if (attempt === generation.current) setError("Could not open that image. Try a different file.");
        } finally {
            URL.revokeObjectURL(url);
            if (attempt === generation.current) setDecoding(false);
        }
    }

    function update(next: Frame) {
        frame.current = {x: clamp(next.x, .1, .9), y: clamp(next.y, .1, .9), zoom: clamp(next.zoom, .5, 5)};
        canvas.current?.dispatchEvent(new Event("icon-frame"));
    }

    function point(event: PointerEvent<HTMLCanvasElement>): Point {
        const rect = event.currentTarget.getBoundingClientRect();
        return {x: (event.clientX - rect.left) / rect.width, y: (event.clientY - rect.top) / rect.height};
    }

    function move(event: PointerEvent<HTMLCanvasElement>) {
        const before = touches.current.get(event.pointerId);
        if (!before || writeLock.current) return;
        const oldPoints = Array.from(touches.current.values());
        const next = point(event);
        touches.current.set(event.pointerId, next);
        const current = frame.current;
        if (oldPoints.length === 2) {
            const [a, b] = Array.from(touches.current.values());
            const oldDistance = distance(oldPoints[0], oldPoints[1]);
            if (oldDistance < .005) return;
            const from = midpoint(oldPoints[0], oldPoints[1]);
            const to = midpoint(a, b);
            const zoom = clamp(current.zoom * distance(a, b) / oldDistance, .5, 5);
            const ratio = zoom / current.zoom;
            update({zoom, x: to.x + (current.x - from.x) * ratio, y: to.y + (current.y - from.y) * ratio});
        } else {
            update({...current, x: current.x + next.x - before.x, y: current.y + next.y - before.y});
        }
    }

    async function save(restore = false) {
        if (!authorization || !owner || writeLock.current || decoding || (!restore && !canvas.current)) return;
        writeLock.current = true;
        setBusy(true); setError(""); setMessage("");
        try {
            if (restore) {
                await adminManagementApi(adminPath, authorization, {method: "DELETE", signal: AbortSignal.timeout(15000)});
                setRevision(null);
            } else {
                const surface = canvas.current!;
                const png = await new Promise<Blob>((resolve, reject) =>
                    surface.toBlob(blob => blob ? resolve(blob) : reject(new Error("Could not prepare the icon.")), "image/png"));
                const form = new FormData();
                form.append("file", png, "app-icon.png");
                const value = await adminManagementApi<IconVersion>(adminPath, authorization, {
                    method: "POST", body: form, signal: AbortSignal.timeout(15000)
                });
                setRevision(value.revision);
            }
            setImage(null); setConfirmRestore(false);
            setMessage(restore ? "Default icon restored." : "App icon published. Installed devices refresh on their own schedule.");
        } catch (cause) {
            setError(cause instanceof Error ? cause.message : "Could not save the icon. Try again.");
        } finally {
            writeLock.current = false;
            setBusy(false);
        }
    }

    if (!owner) return <p>Only the owner can change the app icon.</p>;
    return <section className="mx-auto max-w-4xl space-y-6">
        <header>
            <p className="text-xs font-bold uppercase tracking-widest text-[#980c31]">Storefront branding</p>
            <h1 className="mt-2 text-3xl font-bold">App icon</h1>
            <p className="mt-2 text-sm text-slate-600">Set the icon customers see when they add Gokul to their home screen.</p>
        </header>
        <div className="grid gap-5 md:grid-cols-[1fr_1.2fr]">
            <div className="rounded-3xl border border-[#ead8df] bg-[#fff7f3] p-5 sm:p-7">
                <h2 className="font-semibold">Published icon</h2>
                <div className="my-5 grid place-items-center rounded-2xl bg-[#f5e5ea] p-8">
                    {/* The public icon URL is versioned at publication. */}
                    {/* eslint-disable-next-line @next/next/no-img-element */}
                    <img className="h-24 w-24 rounded-[24%] shadow-lg" width="96" height="96" alt="Current Gokul home-screen icon"
                        src={revision ? `/pwa-icon/512?v=${revision}` : "/icons/icon-512.png"}/>
                </div>
                <p className="text-sm text-slate-600">New installs get the published artwork. Android checks for updates periodically. Existing iPhone installs may need to add the app again.</p>
                {revision && (confirmRestore ? <div className="mt-4 space-y-2">
                    <p className="text-sm">Replace the published icon with the original Gokul icon?</p>
                    <button type="button" disabled={busy} onClick={() => void save(true)} className="mr-4 min-h-11 font-semibold text-[#980c31]">Restore default</button>
                    <button type="button" disabled={busy} onClick={() => setConfirmRestore(false)} className="min-h-11 underline">Cancel</button>
                </div> : <button type="button" disabled={busy} onClick={() => setConfirmRestore(true)} className="mt-3 min-h-11 text-sm font-semibold text-[#980c31] underline">Restore default icon</button>)}
            </div>
            <div className="space-y-4 rounded-3xl border border-[#ead8df] bg-white p-5 sm:p-7">
                <h2 className="font-semibold">Design your icon</h2>
                <label className="block text-sm font-medium">Upload artwork
                    <input type="file" accept="image/png,image/jpeg,image/webp" disabled={busy || decoding} onChange={choose}
                        className="mt-2 block w-full rounded-xl border border-[#ead8df] p-2 text-sm file:mr-3 file:rounded-lg file:border-0 file:bg-[#f7e9ee] file:px-3 file:py-2 file:text-[#980c31]"/>
                </label>
                {image ? <>
                    <div className="rounded-2xl bg-[#f7e9ee] p-4">
                        <canvas ref={canvas} width={512} height={512} tabIndex={0} aria-label="Icon crop preview" aria-describedby="app-icon-gesture-help"
                            className="mx-auto aspect-square w-full max-w-64 touch-none rounded-[23%] bg-white shadow-md outline-offset-4 focus-visible:outline-[#980c31]"
                            onPointerDown={event => {
                                if (busy || touches.current.size >= 2 || event.button !== 0) return;
                                event.currentTarget.setPointerCapture(event.pointerId);
                                touches.current.set(event.pointerId, point(event));
                            }}
                            onPointerMove={move} onPointerUp={event => {touches.current.delete(event.pointerId);}}
                            onPointerCancel={event => {touches.current.delete(event.pointerId);}}
                            onLostPointerCapture={event => {touches.current.delete(event.pointerId);}}
                            onKeyDown={event => {
                                if (busy) return;
                                const step = event.shiftKey ? .06 : .02;
                                const current = frame.current;
                                const next = {
                                    ArrowLeft: {...current, x: current.x - step}, ArrowRight: {...current, x: current.x + step},
                                    ArrowUp: {...current, y: current.y - step}, ArrowDown: {...current, y: current.y + step},
                                    "+": {...current, zoom: current.zoom * 1.1}, "=": {...current, zoom: current.zoom * 1.1},
                                    "-": {...current, zoom: current.zoom / 1.1}, Home: centered
                                }[event.key];
                                if (next) {event.preventDefault(); update(next);}
                            }}/>
                    </div>
                    <p id="app-icon-gesture-help" className="text-sm text-slate-600">Pinch to resize and drag to position. On a keyboard, use arrows and + or −. Keep the important details near the centre.</p>
                    <div className="flex flex-wrap items-center gap-4">
                        <button type="button" disabled={busy || decoding} onClick={() => void save()} className="min-h-11 rounded-xl bg-[#980c31] px-5 font-semibold text-white disabled:opacity-60">{busy ? "Saving…" : "Publish app icon"}</button>
                        <button type="button" disabled={busy} onClick={() => update(centered)} className="min-h-11 text-sm font-semibold text-[#980c31] underline">Reset crop</button>
                    </div>
                </> : <p className="rounded-2xl bg-[#fff7f3] p-5 text-sm text-slate-600">PNG, JPG or WebP up to 8 MB. A simple mark with clear contrast works best at small sizes.</p>}
            </div>
        </div>
        {error && <p role="alert" className="rounded-xl bg-red-50 p-3 text-sm text-red-800">{error}</p>}
        {message && <p role="status" className="rounded-xl bg-emerald-50 p-3 text-sm text-emerald-900">{message}</p>}
    </section>;
}
