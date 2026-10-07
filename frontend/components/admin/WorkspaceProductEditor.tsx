"use client";
import { useRef, useState } from "react";
import WorkspaceDialog from "./WorkspaceDialog";
import WorkspaceMediaEditor from "./WorkspaceMediaEditor";
import {
  workspaceRequest,
  jsonRequest,
  stockQuantity,
  type WorkspaceItem,
  type WorkspacePage,
} from "@/services/menuWorkspaceApi";
import styles from "./MenuWorkspace.module.css";
export type EditMode =
  | "add"
  | "details"
  | "price"
  | "availability"
  | "photo"
  | "stock";
export default function WorkspaceProductEditor({
  item,
  mode,
  branch,
  date,
  data,
  branches,
  onClose,
  onSaved,
}: {
  item: WorkspaceItem | null;
  mode: EditMode;
  branch: number;
  date: string;
  data: WorkspacePage;
  branches: { id: number; name: string }[];
  onClose: () => void;
  onSaved: (notice?: string) => void;
}) {
  const [busy, setBusy] = useState(false);
  const lock = useRef(false);
  const [error, setError] = useState("");
  const [name, setName] = useState(item?.name ?? "");
  const [code, setCode] = useState(item?.code ?? "");
  const [category, setCategory] = useState(
    item?.categoryId ?? data.categories[0]?.id ?? 0,
  );
  const [description, setDescription] = useState(item?.description ?? "");
  const [price, setPrice] = useState(String(item?.basePrice ?? ""));
  const [override, setOverride] = useState(
    item?.priceOverride == null ? "" : String(item.priceOverride),
  );
  const [sale, setSale] = useState<"UNIT" | "WEIGHT">(item?.saleMode ?? "UNIT");
  const [minimum, setMinimum] = useState(item?.minimumWeightGrams ?? 250);
  const [step, setStep] = useState(item?.weightStepGrams ?? 250);
  const [tax, setTax] = useState(item?.taxCategoryId ?? 0);
  const [assigned, setAssigned] = useState([branch]);
  const [available, setAvailable] = useState(item?.available ?? false);
  const [file, setFile] = useState<File | null>(null);
  const [remove, setRemove] = useState(false);
  const [quantity, setQuantity] = useState(
    String(item?.allocation?.approvedQuantity ?? ""),
  );
  const [reason, setReason] = useState("");
  const [createdId, setCreatedId] = useState<number | null>(null);
  const [control, setControl] = useState(
    item?.policy?.controlMode ?? "DAILY_PRODUCTION",
  );
  const [readyRequired, setReadyRequired] = useState(
    item?.policy?.readyStockRequired ?? false,
  );
  const [ready, setReady] = useState(false);
  const [readyQty, setReadyQty] = useState(
    String(item?.allocation?.readyQuantity ?? 0),
  );
  const full = mode === "add" || mode === "details";
  const title =
    mode === "add"
      ? "Add product"
      : `${{ details: "Edit details", price: "Edit branch price", availability: "Set availability", photo: "Edit product photo", stock: "Stock details" }[mode]} · ${item?.name}`;
  async function save() {
    if (lock.current) return;
    lock.current = true;
    setBusy(true);
    setError("");
    try {
      if (full) {
        if (
          !name.trim() ||
          !code.trim() ||
          !category ||
          !price ||
          Number(price) <= 0
        )
          throw new Error("Enter name, code, category and a positive price.");
        const details = {
          name,
          code,
          categoryId: category,
          description,
          basePrice: Number(price),
          saleMode: sale,
          minimumWeightGrams: sale === "WEIGHT" ? minimum : null,
          weightStepGrams: sale === "WEIGHT" ? step : null,
          taxCategoryId: tax || null,
          version: item?.productVersion ?? 0,
        };
        if (mode === "add") {
          let productId = createdId;
          if (!productId) {
            const created = await workspaceRequest<{ productId: number }>(
              branch,
              "",
              jsonRequest("POST", { details, branchIds: assigned }),
            );
            productId = created.productId;
            setCreatedId(productId);
          }
          if (file) {
            const body = new FormData();
            body.append("image", file);
            body.append("version", "0");
            await workspaceRequest(branch, `/${productId}/image`, {
              method: "POST",
              body,
            });
          }
          onSaved(
            "Product created unavailable. Configure stock and service hours before enabling it.",
          );
        } else {
          await workspaceRequest(
            branch,
            `/${item!.productId}/details`,
            jsonRequest("PUT", details),
          );
          onSaved();
        }
      }
      if (mode === "price" || mode === "availability") {
        await workspaceRequest(
          branch,
          `/${item!.productId}/branch`,
          jsonRequest("PATCH", {
            version: item!.branchVersion,
            ...(mode === "price"
              ? {
                  priceOverride: override === "" ? null : Number(override),
                  clearPriceOverride: override === "",
                }
              : { available }),
          }),
        );
        onSaved();
      }
      if (mode === "photo") {
        if (!remove && !file)
          throw new Error("Choose a photo and apply the crop first.");
        if (remove)
          await workspaceRequest(
            branch,
            `/${item!.productId}/image?version=${item!.productVersion}`,
            { method: "DELETE" },
          );
        else {
          const body = new FormData();
          body.append("image", file!);
          body.append("version", String(item!.productVersion));
          const result = await workspaceRequest<{ imageUrl: string }>(
            branch,
            `/${item!.productId}/image`,
            { method: "POST", body },
          );
          const image = new window.Image();
          image.src = result.imageUrl;
          try {
            let timer: ReturnType<typeof setTimeout> | undefined;
            try {
              await Promise.race([
                image.decode(),
                new Promise((_, reject) => {
                  timer = setTimeout(
                    () => reject(new Error("Image load timed out")),
                    10000,
                  );
                }),
              ]);
            } finally {
              clearTimeout(timer);
            }
          } catch {
            onSaved(
              "Photo saved, but its public URL could not load. Check R2 public access and the configured public URL.",
            );
            return;
          }
        }
        onSaved();
      }
      if (mode === "stock") {
        if (!reason.trim())
          throw new Error("Provide a reason for this inventory adjustment.");
        if (!quantity || Number(quantity) <= 0)
          throw new Error("Enter a positive allocation quantity.");
        await workspaceRequest(
          branch,
          `/${item!.productId}/stock/${date}`,
          jsonRequest("PUT", {
            version: item!.allocationVersion,
            policyVersion: item!.policyVersion,
            reason,
            policy: item!.policy
              ? null
              : {
                  controlMode: control,
                  inventoryUnit: item!.saleMode === "WEIGHT" ? "GRAM" : "PIECE",
                  onlineEnabled: true,
                  readyStockRequired: readyRequired,
                  defaultSafetyBuffer: 0,
                  maximumDailyAllocation: null,
                  bookingHorizonDays: 14,
                  productionLeadMinutes: 0,
                  shelfLifeMinutes: null,
                },
            readiness: ready
              ? {
                  status: "READY",
                  readyQuantity: Number(readyQty),
                  expectedReadyAt: item!.allocation?.expectedReadyAt,
                  note: reason,
                }
              : null,
            allocation: {
              approvedQuantity: Number(quantity),
              safetyBufferQuantity: item!.allocation?.safetyBufferQuantity,
              forecastQuantity: item!.allocation?.forecastQuantity,
              forecastConfidence: item!.allocation?.forecastConfidence,
              expectedReadyAt: item!.allocation?.expectedReadyAt,
              note: reason,
            },
          }),
        );
        onSaved();
      }
    } catch (e) {
      setError(
        (e instanceof Error ? e.message : "Save failed. Retry.") +
          (createdId
            ? " The product was created unavailable. Retry the photo without creating another product, or close and find it in the list."
            : ""),
      );
    } finally {
      setBusy(false);
      lock.current = false;
    }
  }
  return (
    <WorkspaceDialog
      title={title}
      scope={
        full || mode === "photo"
          ? "Shared product · All assigned branches"
          : "Selected branch only"
      }
      busy={busy}
      onClose={onClose}
      footer={
        <>
          <button disabled={busy} onClick={onClose}>
            Cancel
          </button>
          <button
            className={styles.primary}
            disabled={busy}
            onClick={() => void save()}
          >
            {busy
              ? "Saving…"
              : mode === "add"
                ? createdId
                  ? "Retry photo"
                  : "Create product"
                : "Save changes"}
          </button>
        </>
      }
    >
      <fieldset disabled={busy || (!!createdId && full)}>
        <div className={full ? styles.formGrid : undefined}>
          {full && (
            <section>
              <h3>Product details</h3>
              <label>
                Name *
                <input
                  required
                  maxLength={150}
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                />
              </label>
              <label>
                Product code *
                <input
                  required
                  maxLength={100}
                  value={code}
                  onChange={(e) => setCode(e.target.value)}
                />
              </label>
              <label>
                Category *
                <select
                  value={category}
                  onChange={(e) => setCategory(Number(e.target.value))}
                >
                  {data.categories.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Description
                <textarea
                  maxLength={500}
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                />
              </label>
              <label>
                Sale unit
                <select
                  disabled={mode !== "add"}
                  value={sale}
                  onChange={(e) => setSale(e.target.value as "UNIT" | "WEIGHT")}
                >
                  <option value="UNIT">Count · pieces</option>
                  <option value="WEIGHT">Weight · g / kg</option>
                </select>
              </label>
              {sale === "WEIGHT" && (
                <div className={styles.formGrid}>
                  <label>
                    Minimum · g
                    <input
                      type="number"
                      min={1}
                      value={minimum}
                      onChange={(e) => setMinimum(Number(e.target.value))}
                    />
                  </label>
                  <label>
                    Step · g
                    <input
                      type="number"
                      min={1}
                      value={step}
                      onChange={(e) => setStep(Number(e.target.value))}
                    />
                  </label>
                </div>
              )}
              <label>
                Base price · ₹ per {sale === "WEIGHT" ? "kg" : "piece"} *
                <input
                  type="number"
                  min={0.01}
                  step={0.01}
                  value={price}
                  onChange={(e) => setPrice(e.target.value)}
                />
              </label>
              <label>
                Tax category
                <select
                  value={tax}
                  onChange={(e) => setTax(Number(e.target.value))}
                >
                  <option value={0}>No product tax category</option>
                  {data.taxes.map((t) => (
                    <option key={t.id} value={t.id}>
                      {t.name}
                    </option>
                  ))}
                </select>
              </label>
            </section>
          )}
          {mode === "add" && (
            <section>
              <h3>Photo & branch assignment</h3>
              <WorkspaceMediaEditor onChange={setFile} />
              <div className={styles.checklist}>
                {branches.map((b) => (
                  <label key={b.id}>
                    <input
                      type="checkbox"
                      checked={assigned.includes(b.id)}
                      disabled={b.id === branch}
                      onChange={(e) =>
                        setAssigned((a) =>
                          e.target.checked
                            ? [...a, b.id]
                            : a.filter((id) => id !== b.id),
                        )
                      }
                    />
                    {b.name}
                  </label>
                ))}
              </div>
              <p className={styles.notice}>
                Starts unavailable. Creating a product does not create stock or
                configure service hours.
              </p>
            </section>
          )}
          {mode === "price" && (
            <>
              <p>
                Base price: ₹{item?.basePrice} /{" "}
                {item?.saleMode === "WEIGHT" ? "kg" : "piece"}
              </p>
              <label>
                Branch override · Leave blank to use base
                <input
                  type="number"
                  min={0.01}
                  step={0.01}
                  value={override}
                  onChange={(e) => setOverride(e.target.value)}
                />
              </label>
              <p>Effective price: ₹{override || item?.basePrice}</p>
              <button onClick={() => setOverride("")}>
                Reset to base price
              </button>
            </>
          )}
          {mode === "availability" && (
            <>
              <label>
                New purchases
                <select
                  value={String(available)}
                  onChange={(e) => setAvailable(e.target.value === "true")}
                >
                  <option value="true">Available</option>
                  <option value="false">Unavailable</option>
                </select>
              </label>
              <p className={styles.notice}>
                Availability does not create stock. Turning it off keeps
                existing orders and reservations.
              </p>
            </>
          )}
          {mode === "photo" && (
            <>
              <WorkspaceMediaEditor onChange={setFile} />
              <label>
                <span>
                  <input
                    type="checkbox"
                    checked={remove}
                    onChange={(e) => setRemove(e.target.checked)}
                  />{" "}
                  Remove current photo
                </span>
              </label>
              <p className={styles.notice}>
                Shared image. A replacement receives a new URL so cached photos
                refresh.
              </p>
            </>
          )}
          {mode === "stock" && (
            <>
              <p>Inventory date: {date} · IST</p>
              <p>Policy: {item?.policy?.controlMode ?? "Not configured"}</p>
              {!item?.policy && (
                <>
                  <label>
                    Inventory policy
                    <select
                      value={control}
                      onChange={(e) =>
                        setControl(e.target.value as typeof control)
                      }
                    >
                      <option value="DAILY_PRODUCTION">Daily production</option>
                      <option value="READY_STOCK">Ready stock</option>
                      <option value="SLOT_CAPACITY">Slot capacity</option>
                      <option value="MANUAL">Manual</option>
                    </select>
                  </label>
                  <label>
                    <span>
                      <input
                        type="checkbox"
                        checked={readyRequired}
                        onChange={(e) => setReadyRequired(e.target.checked)}
                      />{" "}
                      Require ready stock
                    </span>
                  </label>
                </>
              )}
              {item?.allocation && (
                <>
                  <p>
                    Approved:{" "}
                    {stockQuantity(
                      item.allocation.approvedQuantity,
                      item.allocation.inventoryUnit,
                    )}{" "}
                    · Ready:{" "}
                    {stockQuantity(
                      item.allocation.readyQuantity,
                      item.allocation.inventoryUnit,
                    )}
                  </p>
                  <p>
                    Held:{" "}
                    {stockQuantity(
                      item.allocation.heldQuantity,
                      item.allocation.inventoryUnit,
                    )}{" "}
                    · Committed:{" "}
                    {stockQuantity(
                      item.allocation.committedQuantity,
                      item.allocation.inventoryUnit,
                    )}
                  </p>
                  <p>
                    Sellable:{" "}
                    {stockQuantity(
                      item.allocation.availableQuantity,
                      item.allocation.inventoryUnit,
                    )}
                  </p>
                  <p className={styles.notice}>
                    {item.allocation.unavailableReason ??
                      "Sellable balance respects ready-stock, safety buffer, held and committed quantities."}
                  </p>
                </>
              )}
              <label>
                New approved allocation ·{" "}
                {(item?.allocation?.inventoryUnit ??
                  (item?.saleMode === "WEIGHT" ? "GRAM" : "PIECE")) === "GRAM"
                  ? "grams"
                  : "pieces"}
                <input
                  type="number"
                  min={0.001}
                  step={item?.saleMode === "WEIGHT" ? 1 : 1}
                  value={quantity}
                  onChange={(e) => setQuantity(e.target.value)}
                />
              </label>
              <label>
                Adjustment reason *
                <textarea
                  required
                  maxLength={500}
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                />
              </label>
              <label>
                <span>
                  <input
                    type="checkbox"
                    checked={ready}
                    onChange={(e) => setReady(e.target.checked)}
                  />{" "}
                  Mark stock ready after approval
                </span>
              </label>
              {ready && (
                <label>
                  Ready quantity ·{" "}
                  {item?.saleMode === "WEIGHT" ? "grams" : "pieces"}
                  <input
                    type="number"
                    min={0}
                    value={readyQty}
                    onChange={(e) => setReadyQty(e.target.value)}
                  />
                </label>
              )}
            </>
          )}
        </div>
      </fieldset>
      {error && (
        <p role="alert" className={styles.error}>
          {error}
        </p>
      )}
    </WorkspaceDialog>
  );
}
