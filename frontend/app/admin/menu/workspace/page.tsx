"use client";
import Image from "next/image";
import {
  readWorkspaceDraft,
  writeWorkspaceDraft,
} from "@/lib/menuWorkspaceDraft";
import { useCallback, useEffect, useRef, useState } from "react";
import { useAdminAuth } from "@/contexts/AdminAuthContext";
import { apiClient } from "@/services/apiClient";
import {
  preferredAdminBranchId,
  rememberAdminBranchId,
} from "@/lib/adminBranchSelection";
import {
  workspaceRequest,
  jsonRequest,
  stockQuantity,
  type WorkspaceItem,
  type WorkspacePage,
  type Group,
  type GroupPage,
} from "@/services/menuWorkspaceApi";
import WorkspaceProductEditor, {
  type EditMode,
} from "@/components/admin/WorkspaceProductEditor";
import WorkspaceGroupEditor from "@/components/admin/WorkspaceGroupEditor";
import WorkspaceRoutine from "@/components/admin/WorkspaceRoutine";
import WorkspaceAppearance from "@/components/admin/WorkspaceAppearance";
import WorkspaceDialog from "@/components/admin/WorkspaceDialog";
import {InventoryInfo} from "@/components/admin/inventory/InventoryHelp";
import styles from "@/components/admin/MenuWorkspace.module.css";
function Photo({ url, name }: { url: string | null; name: string }) {
  const [failedUrl, setFailedUrl] = useState<string | null>(null);
  const broken = failedUrl === url && !!url;
  return url && !broken ? (
    <Image
      className={styles.photo}
      src={url}
      alt={name}
      width={56}
      height={56}
      sizes="56px"
      loading="lazy"
      onError={() => setFailedUrl(url)}
    />
  ) : (
    <span className={styles.noPhoto}>
      {broken ? "Image cannot load" : "No photo"}
    </span>
  );
}
const empty: WorkspacePage = {
  content: [],
  totalElements: 0,
  page: 0,
  totalPages: 0,
  categories: [],
  branchCategories: [],
  taxes: [],
};
export default function MenuWorkspace() {
  const { profile, authorization, hasPermission } = useAdminAuth();
  const [branches, setBranches] = useState<{ id: number; name: string }[]>([]);
  const [branch, setBranch] = useState<number | null>(null);
  const [date, setDate] = useState(() =>
    new Intl.DateTimeFormat("en-CA", {
      timeZone: "Asia/Kolkata",
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
    }).format(new Date()),
  );
  const [search, setSearch] = useState("");
  const [category, setCategory] = useState("");
  const [filter, setFilter] = useState("ALL");
  const [page, setPage] = useState(0);
  const [tab, setTab] = useState("items");
  const [data, setData] = useState(empty);
  const [groups, setGroups] = useState<GroupPage>({
    version: 0,
    groups: [],
    total: 0,
    page: 0,
    totalPages: 0,
  });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [revision, setRevision] = useState(0);
  const [selected, setSelected] = useState<WorkspaceItem[]>([]);
  const [editor, setEditor] = useState<{
    mode: EditMode;
    item: WorkspaceItem | null;
  } | null>(null);
  const [groupEditor, setGroupEditor] = useState<{
    initial: Group | null;
    key: number;
    version?: number;
  } | null>(null);
  const [routine, setRoutine] = useState(false);
  const [selectingAll, setSelectingAll] = useState(false);
  const [bulk, setBulk] = useState(false);
  const [bulkAvailable, setBulkAvailable] = useState(false);
  const [bulkBusy, setBulkBusy] = useState(false);
  const [results, setResults] = useState<
    { item: WorkspaceItem; error: string | null }[]
  >([]);
  const bulkLock = useRef(false);
  const restored = useRef(false);
  const currentBranch = useRef<number | null>(null);
  const scrollRestore = useRef<number | null>(null);
  const [loadedContext, setLoadedContext] = useState("");
  const queryContext = JSON.stringify([
    branch,
    date,
    search,
    category,
    filter,
    page,
    tab,
    revision,
  ]);
  const fresh = loadedContext === queryContext;
  const currentQuery = useRef(queryContext);
  useEffect(() => {currentQuery.current = queryContext;}, [queryContext]);
  const workspaceKey = `menu-workspace:${profile?.staffId}`;
  const canMenu = hasPermission("MENU_MANAGE"),
    canStock = hasPermission("INVENTORY_MANAGE"),
    canView = hasPermission("INVENTORY_VIEW");
  useEffect(() => {
    if (!profile || !authorization) return;
    const c = new AbortController();
    apiClient<{ id: number; name: string; active: boolean }[]>(
      "/api/branches",
      { signal: c.signal },
    )
      .then((bs) => {
        if (c.signal.aborted) return;
        const permitted = bs.filter(
          (b) =>
            b.active &&
            (profile.roleName === "OWNER_ADMIN" ||
              profile.branchIds.includes(b.id)),
        );
        setBranches(permitted);
        const preferred = preferredAdminBranchId(profile.staffId, permitted);
        currentBranch.current = preferred;
        setBranch(preferred);
        if (!restored.current) {
          restored.current = true;
          try {
            const p = JSON.parse(
              sessionStorage.getItem(`menu-workspace:${profile.staffId}`) ||
                "null",
            );
            if (p) {
              setSearch(p.search || "");
              setCategory(p.category || "");
              setFilter(p.filter || "ALL");
              setPage(p.page || 0);
              setTab(
                ["items", "groups", "appearance"].includes(p.tab)
                  ? p.tab
                  : "items",
              );
              if (/^\d{4}-\d{2}-\d{2}$/.test(p.date)) setDate(p.date);
              scrollRestore.current = p.scrollY || 0;
              if (p.branch === preferred) {
                setEditor(p.editor || null);
                setGroupEditor(p.groupEditor || null);
                setSelected(p.selected || []);
                setRoutine(p.routine || false);
              }
            }
          } catch {}
        }
      })
      .catch((e) => {
        if (!c.signal.aborted) setError(e.message);
      });
    return () => c.abort();
  }, [profile, authorization]);
  useEffect(() => {
    if (!profile || !restored.current) return;
    try {
      sessionStorage.setItem(
        `menu-workspace:${profile.staffId}`,
        JSON.stringify({
          search,
          category,
          filter,
          page,
          tab,
          date,
          branch,
          editor,
          groupEditor,
          selected,
          routine,
          scrollY: window.scrollY,
        }),
      );
    } catch {}
  }, [
    profile,
    search,
    category,
    filter,
    page,
    tab,
    date,
    branch,
    editor,
    groupEditor,
    selected,
    routine,
  ]);
  useEffect(() => {
    if (!profile || !restored.current) return;
    const saveScroll = () => {
      const saved = readWorkspaceDraft<Record<string, unknown>>(workspaceKey);
      if (saved)
        writeWorkspaceDraft(workspaceKey, {
          ...saved,
          scrollY: window.scrollY,
        });
    };
    window.addEventListener("scroll", saveScroll, { passive: true });
    return () => window.removeEventListener("scroll", saveScroll);
  }, [profile, workspaceKey]);
  useEffect(() => {
    if (!branch || !canMenu || !canView) return;
    const c = new AbortController();
    const timer = setTimeout(() => {
      setLoading(true);
      setError("");
      const params = new URLSearchParams({
        date,
        search,
        filter,
        page: String(page),
        size: "25",
      });
      if (category) params.set("category", category);
      Promise.all([
        workspaceRequest<WorkspacePage>(branch, `?${params}`, {
          signal: c.signal,
        }),
        workspaceRequest<GroupPage>(
          branch,
          `/groups?search=${tab === "groups" ? encodeURIComponent(search) : ""}&page=${tab === "groups" ? page : 0}`,
          { signal: c.signal },
        ),
      ])
        .then(([p, g]) => {
          if (!c.signal.aborted) {
            const lastPage = Math.max(0, (tab === "groups" ? g.totalPages : p.totalPages) - 1);
            if (page > lastPage && tab !== "appearance") {
              setPage(lastPage);
              return;
            }
            setData(p);
            setGroups(g);
            setLoadedContext(queryContext);
            if (scrollRestore.current !== null) {
              const y = scrollRestore.current;
              scrollRestore.current = null;
              requestAnimationFrame(() => window.scrollTo(0, y));
            }
          }
        })
        .catch((e) => {
          if (!c.signal.aborted) setError(e.message);
        })
        .finally(() => {
          if (!c.signal.aborted) setLoading(false);
        });
    }, 200);
    return () => {
      c.abort();
      clearTimeout(timer);
    };
  }, [
    branch,
    date,
    search,
    category,
    filter,
    page,
    revision,
    tab,
    canMenu,
    canView,
    queryContext,
  ]);
  async function openProductGroup(item: WorkspaceItem) {
    if (!branch) return;
    try {
      const match = await workspaceRequest<{
        version: number;
        group: Group | null;
      }>(branch, `/groups/product/${item.productId}`);
      if (currentBranch.current !== branch) return;
      setGroups((g) => ({ ...g, version: match.version }));
      setSelected([item]);
      setGroupEditor({
        initial: match.group,
        key: revision + 1,
        version: match.version,
      });
    } catch (e) {
      setError(e instanceof Error ? e.message : "Unable to load group.");
    }
  }
  const reload = useCallback(
    (message = "Saved. The customer catalogue will refresh.") => {
      setEditor(null);
      setGroupEditor(null);
      setSelected([]);
      setLoading(true);
      setNotice(message);
      setRevision((n) => n + 1);
    },
    [],
  );
  async function selectMatchingItems() {
    const context = queryContext;
    setSelectingAll(true); setError("");
    try {
      if (data.totalElements > 500) throw new Error("Filter to 500 items or fewer before selecting all.");
      const items: WorkspaceItem[] = [];
      for (let index = 0; index < 10; index++) {
        const params = new URLSearchParams({date, search, filter, page: String(index), size: "50"});
        if (category) params.set("category", category);
        const result = await workspaceRequest<WorkspacePage>(branch!, `?${params}`);
        if (currentQuery.current !== context) return;
        if (result.totalElements > 500) throw new Error("The catalogue changed. Filter to 500 items or fewer.");
        items.push(...result.content);
        if (index + 1 >= result.totalPages) break;
      }
      setSelected([...new Map(items.map(item => [item.productId, item])).values()]);
    } catch (e) {if (currentQuery.current === context) setError(e instanceof Error ? e.message : "Unable to select items.");}
    finally {setSelectingAll(false);}
  }
  async function runBulk(failedOnly = false) {
    if (!branch || bulkLock.current) return;
    bulkLock.current = true;
    setBulkBusy(true);
    const items = failedOnly
      ? results.filter((r) => r.error).map((r) => r.item)
      : selected;
    const next = [...results.filter((r) => !r.error)];
    for (const item of items) {
      try {
        await workspaceRequest(
          branch,
          `/${item.productId}/branch`,
          jsonRequest("PATCH", {
            version: item.branchVersion,
            available: bulkAvailable,
            clearPriceOverride: false,
          }),
        );
        next.push({ item, error: null });
      } catch (e) {
        next.push({
          item,
          error: e instanceof Error ? e.message : "Update failed",
        });
      }
      setResults([...next]);
    }
    setRevision((n) => n + 1);
    setBulkBusy(false);
    bulkLock.current = false;
  }
  if (!canMenu || !canView)
    return (
      <div className={styles.workspace}>
        Menu management and inventory view permissions are required for this
        workspace.
      </div>
    );
  return (
    <main className={styles.workspace}>
      <div className={styles.heading}>
        <div>
          <p className={styles.muted}>BRANCH WORKSPACE</p>
          <h1>Your menu, at a glance.</h1>
          <p className={styles.muted}>
            Product details, media, prices and inventory in one place.
          </p>
        </div>
        <button
          className={styles.primary}
          disabled={!branch || !fresh}
          onClick={() => setEditor({ mode: "add", item: null })}
        >
          + Add product
        </button>
      </div>
      <div className={styles.toolbar}>
        <label>
          Branch{" "}
          <select
            aria-label="Selected branch"
            value={branch ?? ""}
            onChange={(e) => {
              const id = Number(e.target.value);
              currentBranch.current = id;
              setBranch(id);
              setData(empty);
              setGroups({
                version: 0,
                groups: [],
                total: 0,
                page: 0,
                totalPages: 0,
              });
              setLoadedContext("");
              setLoading(true);
              setEditor(null);
              setRoutine(false);
              setGroupEditor(null);
              setBulk(false);
              setResults([]);
              rememberAdminBranchId(profile!.staffId, id);
              setPage(0);
              setSelected([]);
              setCategory("");
              setNotice("");
            }}
          >
            {branches.map((b) => (
              <option key={b.id} value={b.id}>
                {b.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          Inventory date · IST{" "}
          <input
            type="date"
            value={date}
            onChange={(e) => {
              setDate(e.target.value);
              setPage(0);
              setSelected([]);
            }}
          />
        </label>
        <button onClick={() => setRevision((n) => n + 1)}>Refresh list</button>
      </div>
      <div className={styles.filters}>
        <button
          className={tab === "items" ? styles.selected : ""}
          onClick={() => {
            setTab("items");
            setPage(0);
            setSearch("");
          }}
        >
          Item overview
        </button>
        <button
          className={tab === "groups" ? styles.selected : ""}
          onClick={() => {
            setTab("groups");
            setPage(0);
            setSearch("");
          }}
        >
          Groups & sizes
        </button>
        <button
          className={tab === "appearance" ? styles.selected : ""}
          onClick={() => setTab("appearance")}
        >
          Menu appearance
        </button>
        <a href="/admin/homepage-campaigns">Homepage media & video</a>
      </div>
      {tab !== "appearance" && (
        <>
          <div className={styles.toolbar}>
            <input
              id="workspace-product-search"
              type="search"
              aria-label="Search products or groups"
              placeholder={
                tab === "groups"
                  ? "Search group name…"
                  : "Search product name or code…"
              }
              value={search}
              onChange={(e) => {
                setSearch(e.target.value);
                setPage(0);
              }}
            />
            {tab === "items" && (

              <select
                aria-label="Category filter"
                value={category}
                onChange={(e) => {
                  setCategory(e.target.value);
                  setPage(0);
                }}
              >
                <option value="">All categories</option>
                {data.categories.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
              </select>
            )}
          </div>
        </>
      )}
      {tab === "items" && (
        <>
          <div className={styles.filters}>
            {[
              ["ALL", "All items"],
              ["LOW_STOCK", "Low / zero stock"],
              ["MISSING_IMAGE", "Missing photo"],
              ["UNAVAILABLE", "Unavailable"],
              ["OVERRIDE", "Price overrides"],
              ["GROUPED", "Grouped"],
            ].map(([id, label]) => (
              <button
                key={id}
                className={filter === id ? styles.selected : ""}
                onClick={() => {
                  setFilter(id);
                  setPage(0);
                }}
              >
                {label}
              </button>
            ))}
          </div>
          <p className={styles.notice}>For routine work: select items across pages, then choose Quick inventory setup. Configure new items and their quantities together; daily repetition is optional. Use Stock for exceptions or existing custom policies.</p>
          <p className={styles.muted}>
            Low stock: sellable balance at or below the configured safety
            buffer. Availability remains subject to service hours and checkout
            validation.
          </p>
          <div className={`${styles.row} ${styles.selection}`}>
            <span>
              {selected.length} selected · {date} IST
            </span>
            <div className={styles.row}>
              <button
                disabled={
                  !fresh ||
                  selected.filter((p) => p.saleMode === "UNIT").length < 2
                }
                onClick={() =>
                  setGroupEditor({
                    initial: null,
                    key: revision + 1,
                    version: groups.version,
                  })
                }
              >
                Group selected
              </button>
              <button disabled={!fresh || selectingAll} onClick={() => void selectMatchingItems()}>{selectingAll ? "Selecting items…" : "Select all matching (up to 500)"}</button>
              <button disabled={!fresh || !selected.length || selectingAll} onClick={() => setSelected([])}>Clear selection</button>
              <button disabled={!fresh || selectingAll || !canStock || !selected.length} onClick={() => setRoutine(true)}>Quick inventory setup</button>
              <button
                disabled={!fresh || !selected.length}
                onClick={() => {
                  setBulk(true);
                  setResults([]);
                }}
              >
                Set availability
              </button>
            </div>
          </div>
        </>
      )}
      {error && (
        <div role="alert" className={styles.error}>
          {error}
          <button onClick={() => setRevision((n) => n + 1)}>Retry</button>
        </div>
      )}
      {notice && (
        <p role="status" className={styles.notice}>
          {notice}
        </p>
      )}
      {loading && <p role="status">Updating list…</p>}
      {tab === "appearance" && branch ? (
        <WorkspaceAppearance
          key={branch}
          branch={branch}
          draftKey={`${workspaceKey}:appearance:${branch}`}
          categories={data.branchCategories ?? []}
          categoriesReady={fresh && Array.isArray(data.branchCategories)}
          onNotice={setNotice}
        />
      ) : tab === "items" ? (
        <div className={styles.grid}>
          {data.content.map((p) => (
            <article key={p.productId} className={styles.product}>
              <div className={styles.identity}>
                <Photo url={p.imageUrl} name={p.name} />
                <div>
                  <h3>{p.name}</h3>
                  <span className={styles.muted}>
                    {p.code} · {p.categoryName} ·{" "}
                    {p.saleMode === "WEIGHT" ? "Loose weight" : "Pieces"}
                  </span>
                </div>
                <input
                  type="checkbox"
                  disabled={!fresh}
                  aria-label={`Select ${p.name}`}
                  checked={selected.some((s) => s.productId === p.productId)}
                  onChange={(e) =>
                    setSelected((s) =>
                      e.target.checked
                        ? [...s, p]
                        : s.filter((x) => x.productId !== p.productId),
                    )
                  }
                />
              </div>
              <span
                className={`${styles.badge} ${!p.available ? styles.warning : ""}`}
              >
                {p.available && p.active ? "Available" : "Unavailable"}
              </span>
              {p.priceOverride != null && (
                <span className={`${styles.badge} ${styles.warning}`}>
                  Price override
                </span>
              )}
              {p.allocation && !p.allocation.orderable && (
                <span className={`${styles.badge} ${styles.warning}`}>
                  Stock not orderable
                </span>
              )}
              {!p.imageUrl && (
                <span className={`${styles.badge} ${styles.warning}`}>
                  Missing photo
                </span>
              )}
              <div className={styles.numbers}>
                <div>
                  <span className={styles.muted}>Effective price <InventoryInfo helpKey="effectivePrice" /></span>
                  <div className={styles.value}>
                    ₹{p.effectivePrice} /{" "}
                    {p.saleMode === "WEIGHT" ? "kg" : "piece"}
                  </div>
                  <span className={styles.muted}>Base ₹{p.basePrice}</span>
                </div>
                <div>
                  <span className={styles.muted}>Sellable · {date} <InventoryInfo helpKey="available" /></span>
                  <div className={styles.value}>
                    {p.allocation
                      ? stockQuantity(
                          p.allocation.availableQuantity,
                          p.allocation.inventoryUnit,
                        )
                      : "Not configured"}
                  </div>
                  <span className={styles.muted}>
                    {p.allocation
                      ? `Allocated ${stockQuantity(p.allocation.approvedQuantity, p.allocation.inventoryUnit)} · Held ${stockQuantity(p.allocation.heldQuantity, p.allocation.inventoryUnit)} · Committed ${stockQuantity(p.allocation.committedQuantity, p.allocation.inventoryUnit)}`
                      : (p.policy?.controlMode ?? "Set an inventory policy")}
                  </span>
                </div>
              </div>
              <div className={styles.actions}>
                {(
                  [
                    ["price", "Price"],
                    ["photo", "Photo"],
                    ["availability", "Availability"],
                    ["details", "Details"],
                    ["stock", "Stock"],
                    ["delete", "Delete from branch"],
                  ] as [EditMode, string][]
                ).map(([mode, label]) => (
                  <button
                    key={mode}
                    onClick={() => setEditor({ mode, item: p })}
                    disabled={
                      !fresh || loading || ((mode === "stock" || mode === "delete") && !canStock)
                    }
                  >
                    {label}
                  </button>
                ))}
                <button
                  disabled={!fresh}
                  onClick={() => void openProductGroup(p)}
                >
                  Group / sizes
                </button>
              </div>
            </article>
          ))}
        </div>
      ) : (
        <>
          <div className={styles.row}>
            <button
              className={styles.primary}
              disabled={!fresh}
              onClick={() =>
                setGroupEditor({
                  initial: null,
                  key: revision + 1,
                  version: groups.version,
                })
              }
            >
              + New group
            </button>
            <span className={styles.muted}>
              {groups.total} groups · Save one group at a time
            </span>
          </div>
          <div className={styles.groupGrid}>
            {groups.groups.map((g) => (
              <article className={styles.product} key={g.key}>
                <h3>{g.title}</h3>
                <div className={styles.row}>
                  {g.choices.map((c) => (
                    <span className={styles.badge} key={c.productId}>
                      {c.label}
                    </span>
                  ))}
                </div>
                <div className={styles.actions}>
                  <button
                    disabled={!fresh}
                    onClick={() =>
                      setGroupEditor({
                        initial: g,
                        key: revision + 1,
                        version: groups.version,
                      })
                    }
                  >
                    Edit group / options
                  </button>
                </div>
              </article>
            ))}
          </div>
        </>
      )}
      {tab !== "appearance" &&
        !loading &&
        !(tab === "items" ? data.content.length : groups.groups.length) && (
          <p>No matching items. Change your search or filters.</p>
        )}
      {tab !== "appearance" && (
        <div className={`${styles.row} ${styles.pagination}`}>
          <span className={styles.muted}>
            {tab === "items" ? data.totalElements : groups.total} results · 25
            per page
          </span>
          <div className={styles.row}>
            <button
              disabled={page === 0 || loading}
              onClick={() => setPage((n) => n - 1)}
            >
              Previous
            </button>
            <span>Page {page + 1}</span>
            <button
              disabled={
                page + 1 >=
                  (tab === "items" ? data.totalPages : groups.totalPages) ||
                loading
              }
              onClick={() => setPage((n) => n + 1)}
            >
              Next
            </button>
          </div>
        </div>
      )}
      {routine && branch && (
        <WorkspaceRoutine key={`${branch}-${date}`} items={selected} branch={branch} date={date}
          draftKey={`${workspaceKey}:routine:${branch}:${date}`} onClose={() => {setRoutine(false); reload();}}
          onSaved={(notice) => {setRoutine(false); reload(notice);}} />
      )}
      {editor && branch && (
        <WorkspaceProductEditor
          key={`${branch}-${editor.mode}-${editor.item?.productId ?? "new"}`}
          draftKey={`${workspaceKey}:product:${branch}:${editor.mode}:${editor.item?.productId ?? "new"}:${editor.mode === "stock" ? date : ""}`}
          item={editor.item}
          mode={editor.mode}
          branch={branch}
          date={date}
          data={data}
          branches={branches}
          onClose={() => setEditor(null)}
          onSaved={reload}
        />
      )}
      {groupEditor && branch && (
        <WorkspaceGroupEditor
          key={`${branch}-${groupEditor.key}`}
          draftKey={`${workspaceKey}:group:${branch}:${groupEditor.initial?.key ?? "new"}`}
          branch={branch}
          date={date}
          version={groupEditor.version ?? groups.version}
          initial={groupEditor.initial}
          selected={selected}
          onClose={() => setGroupEditor(null)}
          onSaved={(next, version) => {
            setNotice("Group saved. Product stock and prices are unchanged.");
            setRevision((n) => n + 1);
            setSelected([]);
            setGroupEditor((previous) =>
              next
                ? {
                    initial: null,
                    key: (previous?.key ?? revision) + 1,
                    version,
                  }
                : null,
            );
          }}
        />
      )}
      {bulk && branch && (
        <WorkspaceDialog
          title="Review bulk availability"
          scope={`${branches.find((b) => b.id === branch)?.name} · ${selected.length} products`}
          busy={bulkBusy}
          onClose={() => {
            setBulk(false);
            setSelected([]);
          }}
          footer={
            <>
              <button
                disabled={bulkBusy}
                onClick={() => {
                  setBulk(false);
                  setSelected([]);
                }}
              >
                Close
              </button>
              {results.length ? (
                <button
                  disabled={bulkBusy || !results.some((r) => r.error)}
                  onClick={() => void runBulk(true)}
                >
                  Retry failed only
                </button>
              ) : (
                <button
                  className={styles.primary}
                  disabled={bulkBusy}
                  onClick={() => void runBulk()}
                >
                  Confirm update
                </button>
              )}
            </>
          }
        >
          <label>
            Set new purchase availability
            <select
              disabled={bulkBusy || results.length > 0}
              value={String(bulkAvailable)}
              onChange={(e) => setBulkAvailable(e.target.value === "true")}
            >
              <option value="false">Unavailable</option>
              <option value="true">Available</option>
            </select>
          </label>
          <p className={styles.notice}>
            Existing orders and reservations are retained. Each item checks the
            version you selected.
          </p>
          {selected.map((p) => (
            <p key={p.productId}>
              {p.name} · {p.code}
            </p>
          ))}
          {results.map((r) => (
            <p
              key={r.item.productId}
              className={r.error ? styles.error : styles.notice}
            >
              {r.item.name}: {r.error ?? "Updated"}
            </p>
          ))}
        </WorkspaceDialog>
      )}
    </main>
  );
}
