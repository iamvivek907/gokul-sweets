"use client";
import { useEffect, useRef, useState } from "react";
import {
  readWorkspaceDraft,
  writeWorkspaceDraft,
  clearWorkspaceDraft,
} from "@/lib/menuWorkspaceDraft";
import WorkspaceDialog from "./WorkspaceDialog";
import {
  workspaceRequest,
  jsonRequest,
  type Group,
  type WorkspaceItem,
  type WorkspacePage,
} from "@/services/menuWorkspaceApi";
import styles from "./MenuWorkspace.module.css";
export default function WorkspaceGroupEditor({
  branch,
  version,
  initial,
  selected,
  date,
  draftKey,
  onClose: close,
  onSaved: saved,
}: {
  draftKey: string;
  branch: number;
  version: number;
  initial: Group | null;
  selected: WorkspaceItem[];
  date: string;
  onClose: () => void;
  onSaved: (next: boolean, version: number) => void;
}) {
  const [restoredDraft] = useState(() =>
    readWorkspaceDraft<{ group: Group; version: number }>(draftKey),
  );
  const [draftVersion] = useState(restoredDraft?.version ?? version);
  const [draft, setDraft] = useState<Group>(
    () =>
      restoredDraft?.group ??
      initial ?? {
        key: crypto.randomUUID(),
        title: "",
        choices: selected
          .filter((p) => p.saleMode === "UNIT")
          .slice(0, 6)
          .map((p) => ({ productId: p.productId, label: p.name.slice(0, 30) })),
      },
  );
  useEffect(
    () =>
      writeWorkspaceDraft(draftKey, { group: draft, version: draftVersion }),
    [draftKey, draft, draftVersion],
  );
  function onClose() {
    clearWorkspaceDraft(draftKey);
    close();
  }
  function onSaved(next: boolean, version: number) {
    clearWorkspaceDraft(draftKey);
    saved(next, version);
  }
  const [candidatePage, setCandidatePage] = useState(0);
  const [candidateTotalPages, setCandidateTotalPages] = useState(0);
  const [candidateLoading, setCandidateLoading] = useState(true);
  const [search, setSearch] = useState("");
  const [candidates, setCandidates] = useState<WorkspaceItem[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [confirm, setConfirm] = useState(false);
  const lock = useRef(false);
  useEffect(() => {
    const controller = new AbortController();
    const timer = setTimeout(() => {
      setCandidateLoading(true);
      workspaceRequest<WorkspacePage>(
        branch,
        `?date=${date}&search=${encodeURIComponent(search)}&size=25&filter=COUNT_SKU&page=${candidatePage}`,
        { signal: controller.signal },
      )
        .then((p) => {
          if (!controller.signal.aborted) {
            setCandidates(p.content);
            setCandidateTotalPages(p.totalPages);
          }
        })
        .catch((e) => {
          if (!controller.signal.aborted) setError(e.message);
        })
        .finally(() => {
          if (!controller.signal.aborted) setCandidateLoading(false);
        });
    }, 250);
    return () => {
      controller.abort();
      clearTimeout(timer);
    };
  }, [branch, date, search, candidatePage]);
  async function save(next: boolean, remove = false) {
    if (lock.current) return;
    lock.current = true;
    setBusy(true);
    setError("");
    try {
      if (
        !remove &&
        (!draft.title.trim() ||
          draft.choices.length < 2 ||
          draft.choices.some((c) => !c.label.trim()))
      )
        throw new Error("Enter a name and 2–6 uniquely labelled options.");
      const result = await workspaceRequest<{ version: number }>(
        branch,
        remove ? `/groups/${draft.key}?version=${draftVersion}` : "/groups",
        remove
          ? { method: "DELETE" }
          : jsonRequest("PUT", { version: draftVersion, group: draft }),
      );
      onSaved(next, result?.version ?? draftVersion + 1);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Unable to save group.");
    } finally {
      setBusy(false);
      lock.current = false;
    }
  }
  function move(i: number, d: number) {
    setDraft((g) => {
      const choices = [...g.choices];
      [choices[i], choices[i + d]] = [choices[i + d], choices[i]];
      return { ...g, choices };
    });
  }
  return (
    <WorkspaceDialog
      title={initial ? `Edit ${initial.title}` : "New product group"}
      scope="Branch presentation · Products retain separate stock and price"
      busy={busy}
      onClose={onClose}
      footer={
        <>
          <button disabled={busy} onClick={onClose}>
            Cancel
          </button>
          {initial && (
            <button disabled={busy} onClick={() => setConfirm(true)}>
              Ungroup…
            </button>
          )}
          <button disabled={busy} onClick={() => void save(true)}>
            Save & create next
          </button>
          <button
            className={styles.primary}
            disabled={busy}
            onClick={() => void save(false)}
          >
            {busy ? "Saving…" : "Save group"}
          </button>
        </>
      }
    >
      <fieldset disabled={busy}>
        <label>
          Customer-facing group name
          <input
            maxLength={100}
            value={draft.title}
            onChange={(e) => setDraft({ ...draft, title: e.target.value })}
          />
        </label>
        {draft.choices.map((c, i) => (
          <div className={styles.groupRow} key={c.productId}>
            <label>
              Option · SKU #{c.productId}
              <input
                maxLength={30}
                value={c.label}
                onChange={(e) =>
                  setDraft({
                    ...draft,
                    choices: draft.choices.map((x, n) =>
                      n === i ? { ...x, label: e.target.value } : x,
                    ),
                  })
                }
              />
            </label>
            <div className={styles.row}>
              <button
                aria-label={`Move ${c.label} up`}
                disabled={i === 0}
                onClick={() => move(i, -1)}
              >
                ↑
              </button>
              <button
                aria-label={`Move ${c.label} down`}
                disabled={i === draft.choices.length - 1}
                onClick={() => move(i, 1)}
              >
                ↓
              </button>
              <button
                aria-label={`Remove ${c.label} from group`}
                onClick={() =>
                  setDraft({
                    ...draft,
                    choices: draft.choices.filter(
                      (x) => x.productId !== c.productId,
                    ),
                  })
                }
              >
                Remove
              </button>
            </div>
          </div>
        ))}
        <label>
          Link existing count-based SKU
          <input
            type="search"
            placeholder="Search product name or code"
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setCandidatePage(0);
              setCandidates([]);
              setCandidateLoading(true);
            }}
          />
        </label>
        {candidates
          .filter(
            (p) => !draft.choices.some((c) => c.productId === p.productId),
          )
          .map((p) => (
            <button
              className={styles.candidate}
              key={p.productId}
              disabled={candidateLoading || draft.choices.length >= 6}
              onClick={() =>
                setDraft({
                  ...draft,
                  choices: [
                    ...draft.choices,
                    { productId: p.productId, label: p.name.slice(0, 30) },
                  ],
                })
              }
            >
              <span>
                {p.name}
                <small className={styles.muted}>
                  {" "}
                  · {p.code} · {p.categoryName} · ₹{p.effectivePrice}
                </small>
              </span>
              <span>+ Link</span>
            </button>
          ))}
        <p className={styles.muted}>
          <button
            disabled={candidateLoading || candidatePage === 0}
            onClick={() => {
              setCandidateLoading(true);
              setCandidates([]);
              setCandidatePage((n) => n - 1);
            }}
          >
            Previous SKUs
          </button>{" "}
          Page {candidatePage + 1} of {Math.max(1, candidateTotalPages)}{" "}
          <button
            disabled={
              candidateLoading || candidatePage + 1 >= candidateTotalPages
            }
            onClick={() => {
              setCandidateLoading(true);
              setCandidates([]);
              setCandidatePage((n) => n + 1);
            }}
          >
            Next SKUs
          </button>
          Options must share a category; products already in another group
          cannot be linked.
        </p>
        <div className={styles.preview}>
          <h3>{draft.title || "Customer card preview"}</h3>
          <div className={styles.row}>
            {draft.choices.map((c) => (
              <span className={styles.badge} key={c.productId}>
                {c.label}
              </span>
            ))}
          </div>
        </div>
        <p className={styles.notice}>
          Ungrouping or removing an option never deletes its product.
          Loose-weight products retain their weight selector.
        </p>
        {confirm && (
          <div role="alert" className={styles.notice}>
            Ungroup {draft.title}? The products remain available individually.
            <div className={styles.row}>
              <button onClick={() => setConfirm(false)}>Keep group</button>
              <button onClick={() => void save(false, true)}>
                Yes, ungroup
              </button>
            </div>
          </div>
        )}
      </fieldset>
      {error && (
        <p role="alert" className={styles.error}>
          {error}
        </p>
      )}
    </WorkspaceDialog>
  );
}
