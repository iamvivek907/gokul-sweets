/* eslint-disable @next/next/no-img-element -- Preview must display the original managed upload and expose public URL failures. */
"use client";
import { useEffect, useRef, useState } from "react";
import {
  readWorkspaceDraft,
  writeWorkspaceDraft,
  clearWorkspaceDraft,
} from "@/lib/menuWorkspaceDraft";
import WorkspaceDialog from "./WorkspaceDialog";
import CampaignFramingEditor from "./CampaignFramingEditor";
import {apiClient} from "@/services/apiClient";
import type {MenuCategory,MenuProduct} from "@/types/menu";
import { workspaceRequest, jsonRequest } from "@/services/menuWorkspaceApi";
import { fromIndiaDateTimeInput, toIndiaDateTimeInput } from "@/lib/campaigns";
import { campaignFrameStyle, type CampaignFrame } from "@/lib/campaignFraming";
import styles from "./MenuWorkspace.module.css";
export type MenuBanner = {
  key: string;
  titleEn: string;
  titleHi: string;
  subtitleEn: string;
  subtitleHi: string;
  buttonLabel: string;
  categoryId: number | null;
  visible: boolean;
  order: number;
  startAt: string | null;
  endAt: string | null;
  mediaUrl: string | null;
  mediaType: string | null;
  posterUrl: string | null;
  frame: CampaignFrame;
};
export type MenuPairing = {seedProductId:number;suggestedProductId:number};
export type AppearanceConfig = {
  pairings?: MenuPairing[];
  banners: MenuBanner[];
  categories: { id: number; order: number; imageUrl: string | null }[];
};
type Snapshot = {
  version: number;
  draft: AppearanceConfig;
  live: AppearanceConfig;
  publishedAt: string | null;
};
export default function WorkspaceAppearance({
  branch,
  draftKey,
  categories,
  categoriesReady,
  onNotice,
}: {
  branch: number;
  draftKey: string;
  categories: { id: number; name: string }[];
  categoriesReady: boolean;
  onNotice: (message: string) => void;
}) {
  const [restoredDraft] = useState(() =>
    readWorkspaceDraft<{
      editing: MenuBanner | null;
      categoryEditor: boolean;
      categoryDraft: AppearanceConfig["categories"];
      version: number;
    }>(draftKey),
  );
  const [snapshot, setSnapshot] = useState<Snapshot | null>(null);
  const [editing, setEditing] = useState<MenuBanner | null>(
    restoredDraft?.editing ?? null,
  );
  const [pairingEditor,setPairingEditor]=useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [categoryEditor, setCategoryEditor] = useState(
    restoredDraft?.categoryEditor ?? false,
  );
  const [categoryDraft, setCategoryDraft] = useState<
    AppearanceConfig["categories"]
  >(restoredDraft?.categoryDraft ?? []);
  const [confirm, setConfirm] = useState(false);
  const [file, setFile] = useState<File | null>(null);
  const lock = useRef(false);
  const [reloading, setReloading] = useState(false);
  const [needsReload, setNeedsReload] = useState(false);
  const live = useRef(true);
  useEffect(() => {
    live.current = true;
    return () => {
      live.current = false;
    };
  }, []);
  async function discardDraft() {
    if (lock.current || reloading) return;
    setEditing(null);
    setCategoryEditor(false);
    setCategoryDraft([]);
    setConfirm(false);
    clearWorkspaceDraft(draftKey);
    setReloading(true);
    setNeedsReload(true);
    setError("");
    try {
      const result = await workspaceRequest<Snapshot>(branch, "/appearance");
      if (live.current) {
        setSnapshot(result);
        setNeedsReload(false);
      }
    } catch (e) {
      if (live.current)
        setError(
          e instanceof Error
            ? e.message
            : "Could not reload current appearance. Retry before editing.",
        );
    } finally {
      if (live.current) setReloading(false);
    }
  }
  useEffect(() => {
    const c = new AbortController();
    workspaceRequest<Snapshot>(branch, "/appearance", { signal: c.signal })
      .then((result) => {
        if (!c.signal.aborted)
          setSnapshot(
            restoredDraft &&
              (restoredDraft.editing || restoredDraft.categoryEditor)
              ? { ...result, version: restoredDraft.version }
              : result,
          );
      })
      .catch((e) => {
        if (!c.signal.aborted) setError(e.message);
      });
    return () => c.abort();
  }, [branch, restoredDraft]);
  useEffect(() => {
    if (snapshot)
      writeWorkspaceDraft(draftKey, {
        editing,
        categoryEditor,
        categoryDraft,
        version: snapshot.version,
      });
  }, [draftKey, editing, categoryEditor, categoryDraft, snapshot]);
  async function save(config: AppearanceConfig, publish = false) {
    if (lock.current || !snapshot || reloading || needsReload) return;
    lock.current = true;
    setBusy(true);
    setError("");
    try {
      const result = await workspaceRequest<Snapshot>(
        branch,
        "/appearance",
        jsonRequest("PUT", { version: snapshot.version, config, publish }),
      );
      clearWorkspaceDraft(draftKey);
      setSnapshot(result);
      setEditing(null);
      setCategoryEditor(false);
      setPairingEditor(false);
      setConfirm(false);
      onNotice(
        publish
          ? "Menu appearance published."
          : "Draft saved. Customer menu is unchanged until published.",
      );
    } catch (e) {
      setError(
        e instanceof Error ? e.message : "Save failed; your draft is retained.",
      );
    } finally {
      setBusy(false);
      lock.current = false;
    }
  }
  async function upload(f: File, target: "media" | "poster" | number) {
    setBusy(true);
    setError("");
    try {
      const body = new FormData();
      body.append("file", f);
      body.append("poster", String(target !== "media"));
      const result = await workspaceRequest<{
        url: string;
        contentType: string;
      }>(branch, "/appearance/media", { method: "POST", body });
      if (typeof target === "number") {
        setCategoryDraft((items) =>
          items.map((c) =>
            c.id === target ? { ...c, imageUrl: result.url } : c,
          ),
        );
      } else
        setEditing((b) =>
          b
            ? {
                ...b,
                ...(target === "media"
                  ? { mediaUrl: result.url, mediaType: result.contentType }
                  : { posterUrl: result.url }),
              }
            : b,
        );
    } catch (e) {
      setError(e instanceof Error ? e.message : "Upload failed.");
    } finally {
      setBusy(false);
    }
  }
  if (!snapshot)
    return (
      <div>
        {error ? (
          <p role="alert" className={styles.error}>
            {error}
          </p>
        ) : (
          "Loading appearance…"
        )}
      </div>
    );
  function edit(b: MenuBanner) {
    setError("");
    setFile(null);
    setEditing({ ...b });
  }
  const config = snapshot.draft;
  return (
    <section>
      {reloading && <p role="status">Reloading current appearance…</p>}
      {needsReload && !reloading && (
        <button onClick={() => void discardDraft()}>
          Retry appearance reload
        </button>
      )}
      {!categoriesReady && (
        <p role="status">Loading categories before editing…</p>
      )}
      <div className={styles.heading}>
        <div>
          <h2>Menu appearance</h2>
          <span className={styles.muted}>
            {snapshot.publishedAt
              ? `Published · ${new Date(snapshot.publishedAt).toLocaleString("en-IN", { timeZone: "Asia/Kolkata" })} IST`
              : "No published custom appearance"}{" "}
            · Draft edits are separate
          </span>
        </div>
        <div className={styles.row}>
          <button
            disabled={
              busy || reloading || needsReload || config.banners.length >= 12
            }
            onClick={() =>
              edit({
                key: crypto.randomUUID(),
                titleEn: "",
                titleHi: "",
                subtitleEn: "",
                subtitleHi: "",
                buttonLabel: "",
                categoryId: null,
                visible: true,
                order: config.banners.length,
                startAt: null,
                endAt: null,
                mediaUrl: null,
                mediaType: null,
                posterUrl: null,
                frame: { x: 50, y: 50, zoom: 100, fit: "COVER" },
              })
            }
          >
            + Add banner
          </button>
          <button
            disabled={busy || reloading || needsReload || !categoriesReady}
            onClick={() => {
              setCategoryDraft(
                categories.map(
                  (c, i) =>
                    config.categories.find((x) => x.id === c.id) ?? {
                      id: c.id,
                      order: i,
                      imageUrl: null,
                    },
                ),
              );
              setCategoryEditor(true);
            }}
          >
            Category images & order
          </button>
          <button type="button" disabled={busy||reloading||needsReload} onClick={()=>{setError("");setPairingEditor(true);}}>
            Dish pairings
          </button>
          <button
            className={styles.primary}
            disabled={busy || reloading || needsReload}
            onClick={() => setConfirm(true)}
          >
            Review & publish
          </button>
        </div>
      </div>
      {pairingEditor&&<PairingEditor branch={branch} pairs={config.pairings??[]} error={error} busy={busy||reloading||needsReload}
        onClose={()=>setPairingEditor(false)} onSave={pairings=>void save({...config,pairings})}/>}
      <div className={styles.groupGrid}>
        {config.banners.map((b) => (
          <article className={styles.product} key={b.key}>
            <h3>{b.titleEn}</h3>
            <p className={styles.muted}>
              {b.visible ? "Shown when live" : "Hidden"} · Position {b.order} ·{" "}
              {b.mediaType?.startsWith("video/") ? "Video" : "Image"}
            </p>
            <button
              disabled={busy || reloading || needsReload}
              onClick={() => edit(b)}
            >
              Edit banner
            </button>
          </article>
        ))}
      </div>
      {error && !editing && !categoryEditor && !pairingEditor && !confirm && (
        <p role="alert" className={styles.error}>
          {error}
        </p>
      )}
      {editing && (
        <WorkspaceDialog
          title="Edit menu banner"
          scope="Selected branch · Draft / Live"
          busy={busy}
          onClose={() => void discardDraft()}
          footer={
            <>
              <button
                disabled={busy || reloading || needsReload}
                onClick={() => void discardDraft()}
              >
                Cancel
              </button>
              <button
                disabled={busy || reloading || needsReload}
                onClick={() =>
                  void save({
                    ...config,
                    banners: config.banners.filter(
                      (b) => b.key !== editing.key,
                    ),
                  })
                }
              >
                Delete from draft
              </button>
              <button
                className={styles.primary}
                disabled={busy || reloading || needsReload}
                onClick={() =>
                  void save({
                    ...config,
                    banners: config.banners.some((b) => b.key === editing.key)
                      ? config.banners.map((b) =>
                          b.key === editing.key ? editing : b,
                        )
                      : [...config.banners, editing],
                  })
                }
              >
                Save draft
              </button>
            </>
          }
        >
          <fieldset disabled={busy || reloading || needsReload}>
            <div className={styles.formGrid}>
              <section>
                {(
                  [
                    ["titleEn", "English title"],
                    ["titleHi", "Hindi title"],
                    ["subtitleEn", "English subtitle"],
                    ["subtitleHi", "Hindi subtitle"],
                    ["buttonLabel", "Button label"],
                  ] as const
                ).map(([key, label]) => (
                  <label key={key}>
                    {label}
                    <input
                      maxLength={
                        key.startsWith("subtitle")
                          ? 240
                          : key === "buttonLabel"
                            ? 60
                            : 120
                      }
                      value={editing[key]}
                      onChange={(e) =>
                        setEditing({ ...editing, [key]: e.target.value })
                      }
                    />
                  </label>
                ))}
                <label>
                  Destination category
                  <select
                    value={editing.categoryId ?? ""}
                    onChange={(e) =>
                      setEditing({
                        ...editing,
                        categoryId: e.target.value
                          ? Number(e.target.value)
                          : null,
                      })
                    }
                  >
                    <option value="">No button destination</option>
                    {categories.map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.name}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Order
                  <input
                    type="number"
                    min={0}
                    value={editing.order}
                    onChange={(e) =>
                      setEditing({ ...editing, order: Number(e.target.value) })
                    }
                  />
                </label>
                <label>
                  Start · IST
                  <input
                    type="datetime-local"
                    value={toIndiaDateTimeInput(editing.startAt)}
                    onChange={(e) =>
                      setEditing({
                        ...editing,
                        startAt: fromIndiaDateTimeInput(e.target.value),
                      })
                    }
                  />
                </label>
                <label>
                  End · IST
                  <input
                    type="datetime-local"
                    value={toIndiaDateTimeInput(editing.endAt)}
                    onChange={(e) =>
                      setEditing({
                        ...editing,
                        endAt: fromIndiaDateTimeInput(e.target.value),
                      })
                    }
                  />
                </label>
                <label>
                  <span>
                    <input
                      type="checkbox"
                      checked={editing.visible}
                      onChange={(e) =>
                        setEditing({ ...editing, visible: e.target.checked })
                      }
                    />{" "}
                    Show when published
                  </span>
                </label>
              </section>
              <section>
                <label>
                  Banner image or video
                  <input
                    type="file"
                    accept="image/jpeg,image/png,image/webp,video/mp4,video/webm"
                    onChange={(e) => {
                      const f = e.target.files?.[0];
                      if (f) {
                        setFile(f);
                        void upload(f, "media");
                      }
                    }}
                  />
                </label>
                <div className={styles.framing}>
                  <CampaignFramingEditor
                    label="Menu banner framing"
                    file={file}
                    savedUrl={editing.mediaUrl}
                    mediaType={editing.mediaType}
                    portrait={false}
                    frame={editing.frame}
                    onChange={(frame) => setEditing({ ...editing, frame })}
                  />
                </div>
                {editing.mediaType?.startsWith("video/") && (
                  <label>
                    Static poster · Required before publishing
                    <input
                      type="file"
                      accept="image/jpeg,image/png,image/webp"
                      onChange={(e) => {
                        const f = e.target.files?.[0];
                        if (f) void upload(f, "poster");
                      }}
                    />
                  </label>
                )}
                <div className={styles.preview}>
                  <p className={styles.muted}>MOBILE MENU PREVIEW</p>
                  {editing.mediaUrl &&
                    (editing.mediaType?.startsWith("video/") ? (
                      <video
                        src={editing.mediaUrl}
                        poster={editing.posterUrl ?? undefined}
                        controls
                        muted
                        playsInline
                        className={styles.bannerPreviewMedia}
                        style={campaignFrameStyle(editing.frame)}
                      />
                    ) : (
                      <img
                        src={editing.mediaUrl}
                        alt="Banner preview"
                        className={styles.bannerPreviewMedia}
                        style={campaignFrameStyle(editing.frame)}
                        onError={() =>
                          setError(
                            "Media uploaded, but its public URL cannot load. Check R2 public access and the public URL configuration.",
                          )
                        }
                      />
                    ))}
                  <strong>{editing.titleEn || "Mobile menu preview"}</strong>
                  <p>{editing.subtitleEn}</p>
                  {editing.buttonLabel && (
                    <button disabled>{editing.buttonLabel}</button>
                  )}
                </div>
                <p className={styles.notice}>
                  Published banners replace the phone menu promotion below Offers &amp; savings across Food, Sweets, Bakery and Snacks. Desktop uses the same published content. One category banner can introduce up to eight live categories using the same artwork; publish multiple banners to choose each slide’s artwork, copy and category. Marketing content does not change stock, prices, ratings or
                  offers.
                </p>
              </section>
            </div>
          </fieldset>
          {error && (
            <p role="alert" className={styles.error}>
              {error}
            </p>
          )}
        </WorkspaceDialog>
      )}
      {categoryEditor && (
        <WorkspaceDialog
          title="Category appearance"
          scope="Selected branch · Draft"
          busy={busy}
          onClose={() => void discardDraft()}
          footer={
            <>
              <button
                disabled={busy || reloading || needsReload}
                onClick={() => void discardDraft()}
              >
                Cancel
              </button>
              <button
                className={styles.primary}
                disabled={busy || reloading || needsReload || !categoriesReady}
                onClick={() =>
                  categoriesReady &&
                  void save({ ...config, categories: categoryDraft })
                }
              >
                Save draft
              </button>
            </>
          }
        >
          <fieldset disabled={busy || reloading || needsReload}>
            {categoryDraft.map((c) => (
              <div className={styles.product} key={c.id}>
                <h3>{categories.find((x) => x.id === c.id)?.name}</h3>
                <label>
                  Display order
                  <input
                    type="number"
                    min={0}
                    value={c.order}
                    onChange={(e) =>
                      setCategoryDraft((items) =>
                        items.map((x) =>
                          x.id === c.id
                            ? { ...x, order: Number(e.target.value) }
                            : x,
                        ),
                      )
                    }
                  />
                </label>
                <label>
                  Image
                  <input
                    type="file"
                    accept="image/jpeg,image/png,image/webp"
                    onChange={(e) => {
                      const f = e.target.files?.[0];
                      if (f) void upload(f, c.id);
                    }}
                  />
                </label>
                {c.imageUrl && (
                  <button
                    onClick={() =>
                      setCategoryDraft((items) =>
                        items.map((x) =>
                          x.id === c.id ? { ...x, imageUrl: null } : x,
                        ),
                      )
                    }
                  >
                    Remove image
                  </button>
                )}
              </div>
            ))}
          </fieldset>
          {error && (
            <p role="alert" className={styles.error}>
              {error}
            </p>
          )}
        </WorkspaceDialog>
      )}
      {confirm && (
        <WorkspaceDialog
          title="Publish menu appearance?"
          scope="Selected branch · Customer-visible"
          busy={busy}
          onClose={() => setConfirm(false)}
          footer={
            <>
              <button
                disabled={busy || reloading || needsReload}
                onClick={() => setConfirm(false)}
              >
                Keep draft
              </button>
              <button
                className={styles.primary}
                disabled={busy || reloading || needsReload}
                onClick={() => void save(config, true)}
              >
                Publish now
              </button>
            </>
          }
        >
          <p>
            Publish {config.banners.length} banners and{" "}
            {config.categories.length} category settings. Visibility and IST
            start/end dates still apply.
          </p>
          <p>
            Current live configuration remains active until publication
            succeeds.
          </p>
          {error && (
            <p role="alert" className={styles.error}>
              {error}
            </p>
          )}
        </WorkspaceDialog>
      )}
    </section>
  );
}

function PairingEditor({branch,pairs,busy,error,onClose,onSave}:{branch:number;pairs:MenuPairing[];busy:boolean;error:string;onClose:()=>void;onSave:(pairs:MenuPairing[])=>void}){
  const [draft,setDraft]=useState(pairs);
  const [catalog,setCatalog]=useState<MenuProduct[]|null>(null);
  const [failed,setFailed]=useState(false);
  const [revision,retry]=useState(0);
  const [seed,setSeed]=useState(0),[suggestion,setSuggestion]=useState(0);
  useEffect(()=>{
    const controller=new AbortController();
    void apiClient<MenuCategory[]>(`/api/menu?branchId=${branch}`,{signal:controller.signal})
      .then(categories=>{if(!controller.signal.aborted)setCatalog(categories.flatMap(category=>category.products).sort((a,b)=>a.name.localeCompare(b.name)));})
      .catch(()=>{if(!controller.signal.aborted)setFailed(true);});
    return()=>controller.abort();
  },[branch,revision]);
  const name=(id:number)=>catalog?.find(product=>product.id===id)?.name??`Dish #${id}`;
  const canAdd=!!seed&&!!suggestion&&seed!==suggestion&&draft.length<100&&!draft.some(pair=>pair.seedProductId===seed&&pair.suggestedProductId===suggestion);
  return <WorkspaceDialog title="Dish pairings" scope="Selected branch" busy={busy} onClose={onClose} footer={<button type="button" className={styles.primary} disabled={busy} onClick={()=>onSave(draft)}>Save pairings draft</button>}>
    {error&&<p className={styles.error} role="alert">{error}</p>}
    <p>Choose which dishes this branch should suggest together. Pairings appear only when the suggested dish can be ordered. Save the draft, then use Review &amp; publish to make it live.</p>
    {failed?<button type="button" onClick={()=>{setFailed(false);retry(value=>value+1);}}>Retry loading dishes</button>:!catalog&&<p role="status">Loading dishes…</p>}
    <div className={styles.row}>
      <label>When a customer adds <select disabled={!catalog||busy} value={seed} onChange={event=>setSeed(Number(event.target.value))}>
        <option value={0}>Choose dish</option>{catalog?.map(product=><option key={product.id} value={product.id}>{product.name} · {product.categoryName}</option>)}
      </select></label>
      <label>Suggest <select disabled={!catalog||busy} value={suggestion} onChange={event=>setSuggestion(Number(event.target.value))}>
        <option value={0}>Choose dish</option>{catalog?.filter(product=>product.id!==seed).map(product=><option key={product.id} value={product.id}>{product.name} · {product.categoryName}</option>)}
      </select></label>
      <button type="button" disabled={!canAdd||busy} onClick={()=>{setDraft(value=>[...value,{seedProductId:seed,suggestedProductId:suggestion}]);setSuggestion(0);}}>Add pairing</button>
    </div>
    <ul>{draft.map((pair,index)=><li key={`${pair.seedProductId}-${pair.suggestedProductId}`} className={styles.row}>
      <span>{name(pair.seedProductId)} → {name(pair.suggestedProductId)}</span>
      <button type="button" aria-label={`Remove pairing: ${name(pair.seedProductId)} and ${name(pair.suggestedProductId)}`} disabled={busy} onClick={()=>setDraft(value=>value.filter((_,position)=>position!==index))}>Remove</button>
    </li>)}</ul>
  </WorkspaceDialog>;
}
