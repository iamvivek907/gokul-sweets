"use client";

import { useRef, useState } from "react";
import { adminManagementApi } from "@/services/adminManagementApi";
import { jsonRequest } from "@/services/menuWorkspaceApi";

export type MenuCategory = {
  id: number;
  code: string;
  name: string;
  description: string | null;
  displayOrder: number;
  active: boolean;
};

/** Shared by category management and the product editor; never submits a parent form. */
export default function MenuCategoryCreate({
  onCreated,
  onBusyChange,
}: {
  onCreated: (category: MenuCategory) => void;
  onBusyChange?: (busy: boolean) => void;
}) {
  const lock = useRef(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [order, setOrder] = useState("0");

  async function create() {
    if (lock.current) return;
    const normalizedCode = code.trim().toUpperCase();
    if (
      !/^[A-Z0-9][A-Z0-9_-]{0,79}$/.test(normalizedCode) ||
      !name.trim() ||
      !order.trim() ||
      !Number.isInteger(Number(order)) ||
      Number(order) < 0 ||
      Number(order) > 10000
    ) {
      setError(
        "Enter a name, a code using letters, numbers, hyphens or underscores, and a display order from 0 to 10000.",
      );
      return;
    }
    lock.current = true;
    setBusy(true);
    onBusyChange?.(true);
    setError("");
    try {
      const created = await adminManagementApi<MenuCategory>(
        "/api/admin/categories",
        "staff-session",
        jsonRequest("POST", {
          code: normalizedCode,
          name: name.trim(),
          description: description.trim(),
          displayOrder: Number(order),
        }),
      );
      setCode("");
      setName("");
      setDescription("");
      setOrder("0");
      onCreated(created);
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : "Unable to create category. Please retry.",
      );
    } finally {
      lock.current = false;
      setBusy(false);
      onBusyChange?.(false);
    }
  }

  return (
    <fieldset
      disabled={busy}
      className="my-4 min-w-0 space-y-3 rounded-xl border border-[#c4d4c9] bg-[#fffaf3] p-4 text-[#173a37]"
    >
      <legend className="px-2 font-bold">New menu category</legend>
      <p className="text-sm">
        Shared across all branches. This creates an empty category; products,
        prices and stock are added separately.
      </p>
      <label className="block">
        Category name
        <input
          className="mt-1 block min-h-11 w-full rounded-lg border bg-white p-2 text-[#173a37]"
          maxLength={100}
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Breakfast & Parathas"
        />
      </label>
      <label className="block">
        Category code
        <input
          className="mt-1 block min-h-11 w-full rounded-lg border bg-white p-2 text-[#173a37]"
          maxLength={80}
          value={code}
          onChange={(e) => setCode(e.target.value)}
          placeholder="BREAKFAST_PARATHAS"
          autoCapitalize="characters"
          spellCheck={false}
        />
      </label>
      <p className="text-sm">
        Use a unique, permanent code. Existing import codes use the same
        identity.
      </p>
      <label className="block">
        Description (optional)
        <textarea
          className="mt-1 block w-full rounded-lg border bg-white p-2 text-[#173a37]"
          maxLength={500}
          value={description}
          onChange={(e) => setDescription(e.target.value)}
        />
      </label>
      <label className="block">
        Display order
        <input
          className="mt-1 block min-h-11 w-full rounded-lg border bg-white p-2 text-[#173a37]"
          type="number"
          min={0}
          max={10000}
          step={1}
          value={order}
          onChange={(e) => setOrder(e.target.value)}
        />
      </label>
      <p className="text-sm">
        Lower numbers appear first wherever the menu uses category display
        order.
      </p>
      {error && (
        <p role="alert" className="text-sm text-red-800">
          {error}
        </p>
      )}
      <button
        type="button"
        disabled={busy}
        onClick={() => void create()}
        className="min-h-11 rounded-lg bg-[#7a1625] px-4 font-semibold text-white disabled:opacity-60"
      >
        {busy ? "Creating category…" : "Create category"}
      </button>
    </fieldset>
  );
}
