"use client";

import {useEffect, useRef, useState} from "react";
import {campaignFrameStyle, type CampaignFrame} from "@/lib/campaignFraming";
import styles from "./CampaignFramingEditor.module.css";

type Point = {x: number; y: number};
const clamp = (value: number, min: number, max: number) => Math.min(max, Math.max(min, value));
const distance = (a: Point, b: Point) => Math.hypot(a.x - b.x, a.y - b.y);

export default function CampaignFramingEditor({label, file, savedUrl, mediaType, portrait, frame, onChange}: {
    label: string; file: File | null; savedUrl?: string | null; mediaType?: string | null;
    portrait: boolean; frame: CampaignFrame; onChange: (frame: CampaignFrame) => void;
}) {
    const [local, setLocal] = useState<{file: File; url: string} | null>(null);
    const pointers = useRef(new Map<number, Point>());
    const frameRef = useRef(frame);
    useEffect(() => {frameRef.current = frame;}, [frame]);
    useEffect(() => {
        if (!file) return;
        const url = URL.createObjectURL(file);
        let active = true;
        queueMicrotask(() => {if (active) setLocal({file, url});});
        return () => {active = false; URL.revokeObjectURL(url);};
    }, [file]);
    const url = file ? local?.file === file ? local.url : null : savedUrl;
    const video = (file?.type ?? mediaType)?.startsWith("video/");
    const update = (next: CampaignFrame) => {
        frameRef.current = next;
        onChange(next);
    };
    const release = (event: React.PointerEvent<HTMLDivElement>) => {
        pointers.current.delete(event.pointerId);
        if (event.currentTarget.hasPointerCapture(event.pointerId)) event.currentTarget.releasePointerCapture(event.pointerId);
    };
    return <div className={styles.editor}>
        <div className={styles.heading}><strong>{label}</strong><span>{portrait ? "Phone · 9:16" : "Desktop · 16:9"}</span></div>
        <div className={`${styles.viewport} ${portrait ? styles.portrait : styles.landscape}`}
            role="img" aria-label={`${label} framing preview. Drag to reposition and pinch with two fingers to zoom.`}
            onPointerDown={event => {
                event.currentTarget.setPointerCapture(event.pointerId);
                pointers.current.set(event.pointerId, {x: event.clientX, y: event.clientY});
            }}
            onPointerMove={event => {
                const before = pointers.current.get(event.pointerId);
                if (!before) return;
                const points = [...pointers.current.values()];
                const nextPoint = {x: event.clientX, y: event.clientY};
                pointers.current.set(event.pointerId, nextPoint);
                const current = frameRef.current;
                if (points.length === 2) {
                    const previousDistance = distance(points[0], points[1]);
                    const updated = [...pointers.current.values()];
                    if (previousDistance > 0) update({...current, zoom: clamp(Math.round(current.zoom * distance(updated[0], updated[1]) / previousDistance), 100, 300)});
                } else {
                    const rect = event.currentTarget.getBoundingClientRect();
                    update({...current,
                        x: clamp(Math.round(current.x - (nextPoint.x - before.x) / rect.width * 100), 0, 100),
                        y: clamp(Math.round(current.y - (nextPoint.y - before.y) / rect.height * 100), 0, 100)});
                }
            }}
            onPointerUp={release} onPointerCancel={release}>
            {url ? video ? <video key={url} src={url} muted loop playsInline autoPlay preload="metadata"
                className={styles.media} style={campaignFrameStyle(frame)} />
                // Admin previews use local blob URLs and need exact crop fidelity.
                // eslint-disable-next-line @next/next/no-img-element
                : <img src={url} alt="" className={styles.media} style={campaignFrameStyle(frame)} />
                : <div className={styles.empty}>Choose a {portrait ? "phone" : "desktop"} file to frame it here.</div>}
            {url && <div className={styles.heroOverlay} aria-hidden="true"><small>GOKUL SWEETS &amp; RESTAURANTS</small><strong>A little joy<br />in every visit.</strong></div>}
            {url && <span className={styles.hint}>Drag to move · Pinch to zoom</span>}
        </div>
        <div className={styles.controls}>
            <div className={styles.fit} role="group" aria-label={`${label} fit`}>
                <button type="button" aria-pressed={frame.fit === "COVER"} onClick={() => update({...frameRef.current, fit: "COVER"})}>Fill frame</button>
                <button type="button" aria-pressed={frame.fit === "CONTAIN"} onClick={() => update({...frameRef.current, fit: "CONTAIN"})}>Fit whole media</button>
            </div>
            <label className={styles.zoom}>Zoom <input type="range" min="100" max="300" step="1" value={frame.zoom}
                onChange={event => update({...frameRef.current, zoom: Number(event.target.value)})} /><span>{frame.zoom}%</span></label>
            <button type="button" className={styles.reset} onClick={() => update({x: 50, y: 50, zoom: 100, fit: "COVER"})}>Reset framing</button>
        </div>
    </div>;
}
