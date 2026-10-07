/* eslint-disable @next/next/no-img-element -- Direct media rendering exposes storage failures and supports local crop previews. */
"use client";
import { useEffect, useRef, useState } from "react";
import { renderProductPhoto } from "@/lib/productPhotoCanvas";
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
  const fitCanvas = useRef<HTMLCanvasElement>(null);
  useEffect(() => {
    if (!url || fit !== "contain") return;
    let active = true;
    const image = new window.Image();
    image.src = url;
    image
      .decode()
      .then(() => {
        if (active && fitCanvas.current) {
          const canvas = renderProductPhoto(image, rotation);
          fitCanvas.current.getContext("2d")?.drawImage(canvas, 0, 0);
        }
      })
      .catch(() => {
        if (active) setError("Unable to preview photo.");
      });
    return () => {
      active = false;
    };
  }, [url, rotation, fit]);
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
    if (!url || (fit === "cover" && !area)) return;
    setBusy(true);
    setError("");
    try {
      const img = new window.Image();
      img.src = url;
      await img.decode();
      const out = renderProductPhoto(
        img,
        rotation,
        fit === "cover" ? area! : undefined,
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
            setFit("contain");
            setArea(null);
            setPreview("");
            onChange(null);
          }}
        />
      </label>
      {url && (
        <>
          <div className={styles.crop}>
            {fit === "contain" ? (
              <canvas
                ref={fitCanvas}
                width={800}
                height={800}
                aria-label="Full photo fit preview"
                style={{ width: "100%", height: "100%", objectFit: "contain" }}
              />
            ) : (
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
            )}
          </div>
          <label>
            Zoom
            <input
              type="range"
              disabled={fit === "contain"}
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
            {fit === "contain"
              ? "Fit keeps the whole photo with white padding. Rotate or select Fill to crop."
              : "Drag to reposition. Apply the crop before saving."}
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
