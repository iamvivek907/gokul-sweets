/* eslint-disable @next/next/no-img-element -- Direct media rendering exposes storage failures and supports local crop previews. */
"use client";
import { useEffect, useRef, useState } from "react";
import Cropper, { type Area } from "react-easy-crop";
import styles from "./MenuWorkspace.module.css";
export default function WorkspaceMediaEditor({
  onChange,
}: {
  onChange: (file: File | null) => void;
}) {
  const [url, setUrl] = useState("");
  const [crop, setCrop] = useState({ x: 0, y: 0 });
  const [zoom, setZoom] = useState(1);
  const [rotation, setRotation] = useState(0);
  const [fit, setFit] = useState<"contain" | "cover">("contain");
  const [area, setArea] = useState<Area | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [preview, setPreview] = useState("");
  const ref = useRef<string>("");
  useEffect(
    () => () => {
      if (ref.current) URL.revokeObjectURL(ref.current);
    },
    [],
  );
  useEffect(
    () => () => {
      if (preview) URL.revokeObjectURL(preview);
    },
    [preview],
  );
  async function apply() {
    if (!url || !area) return;
    setBusy(true);
    setError("");
    try {
      const img = new window.Image();
      img.src = url;
      await img.decode();
      const radians = (rotation * Math.PI) / 180;
      const w =
        Math.abs(Math.cos(radians) * img.width) +
        Math.abs(Math.sin(radians) * img.height);
      const h =
        Math.abs(Math.sin(radians) * img.width) +
        Math.abs(Math.cos(radians) * img.height);
      const rotated = document.createElement("canvas");
      rotated.width = Math.ceil(w);
      rotated.height = Math.ceil(h);
      const ctx = rotated.getContext("2d");
      if (!ctx) throw new Error("Image editor unavailable.");
      ctx.translate(w / 2, h / 2);
      ctx.rotate(radians);
      ctx.drawImage(img, -img.width / 2, -img.height / 2);
      const out = document.createElement("canvas");
      out.width = 800;
      out.height = 800;
      const c = out.getContext("2d");
      if (!c) throw new Error("Image editor unavailable.");
      c.fillStyle = "#fff";
      c.fillRect(0, 0, 800, 800);
      c.drawImage(
        rotated,
        area.x,
        area.y,
        area.width,
        area.height,
        0,
        0,
        800,
        800,
      );
      const blob = await new Promise<Blob>((resolve, reject) =>
        out.toBlob(
          (b) => (b ? resolve(b) : reject(new Error("Unable to crop image."))),
          "image/jpeg",
          0.9,
        ),
      );
      const file = new File([blob], "product-photo.jpg", {
        type: "image/jpeg",
      });
      onChange(file);
      setPreview(URL.createObjectURL(file));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Unable to crop photo.");
    } finally {
      setBusy(false);
    }
  }
  return (
    <div>
      <label>
        Choose photo · JPEG, PNG or WebP · Up to 5 MB
        <input
          type="file"
          accept="image/jpeg,image/png,image/webp"
          onChange={(e) => {
            const file = e.target.files?.[0];
            if (!file) return;
            setError("");
            if (
              !["image/jpeg", "image/png", "image/webp"].includes(file.type) ||
              file.size > 5 * 1024 * 1024
            ) {
              setError("Choose a JPEG, PNG or WebP up to 5 MB.");
              return;
            }
            if (ref.current) URL.revokeObjectURL(ref.current);
            ref.current = URL.createObjectURL(file);
            setUrl(ref.current);
            setCrop({ x: 0, y: 0 });
            setZoom(1);
            setRotation(0);
            setPreview("");
            onChange(null);
          }}
        />
      </label>
      {url && (
        <>
          <div className={styles.crop}>
            <Cropper
              image={url}
              crop={crop}
              zoom={zoom}
              rotation={rotation}
              aspect={1}
              objectFit={fit}
              onCropChange={(v) => {
                setCrop(v);
                onChange(null);
                setPreview("");
              }}
              onZoomChange={(v) => {
                setZoom(v);
                onChange(null);
                setPreview("");
              }}
              onCropComplete={(_, pixels) => setArea(pixels)}
            />
          </div>
          <label>
            Zoom
            <input
              type="range"
              min={1}
              max={3}
              step={0.05}
              value={zoom}
              onChange={(e) => {
                setZoom(Number(e.target.value));
                onChange(null);
                setPreview("");
              }}
            />
          </label>
          <div className={styles.row}>
            <button
              type="button"
              onClick={() => {
                setRotation((v) => (v + 90) % 360);
                onChange(null);
                setPreview("");
              }}
            >
              Rotate 90°
            </button>
            <button
              type="button"
              onClick={() => {
                setFit("contain");
                setCrop({ x: 0, y: 0 });
                setZoom(1);
                onChange(null);
                setPreview("");
              }}
            >
              Fit
            </button>
            <button
              type="button"
              onClick={() => {
                setFit("cover");
                setCrop({ x: 0, y: 0 });
                setZoom(1);
                onChange(null);
                setPreview("");
              }}
            >
              Fill
            </button>
            <button type="button" disabled={busy} onClick={() => void apply()}>
              {busy ? "Preparing…" : "Apply crop"}
            </button>
          </div>
          <p className={styles.muted}>
            Drag to reposition. Apply the crop before saving.
          </p>
        </>
      )}
      {preview && (
        <div className={styles.preview}>
          <strong>Customer card photo preview</strong>
          <img
            className={styles.imagePreview}
            src={preview}
            alt="Cropped product photo"
          />
        </div>
      )}
      {error && (
        <p role="alert" className={styles.error}>
          {error}
        </p>
      )}
    </div>
  );
}
