"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useAdminAuth } from "@/contexts/AdminAuthContext";
import { adminManagementApi } from "@/services/adminManagementApi";
import MenuCategoryCreate, {
  type MenuCategory,
} from "@/components/admin/MenuCategoryCreate";

type CategoryPage = {
  content: MenuCategory[];
  number: number;
  totalPages: number;
};

/** Create categories before products; pagination bounds taxonomy reads. */
export default function MenuCategoriesPage() {
  const { authorization, hasPermission } = useAdminAuth();
  const allowed = hasPermission("MENU_MANAGE");
  const [page, setPage] = useState(0);
  const [revision, setRevision] = useState(0);
  const [result, setResult] = useState<CategoryPage | null>(null);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  useEffect(() => {
    if (!allowed || !authorization) return;
    const controller = new AbortController();
    adminManagementApi<CategoryPage>(
      `/api/admin/categories?page=${page}`,
      authorization,
      { signal: controller.signal },
    )
      .then((data) => {
        if (!controller.signal.aborted) setResult(data);
      })
      .catch((cause) => {
        if (!controller.signal.aborted)
          setError(
            cause instanceof Error
              ? cause.message
              : "Unable to load categories.",
          );
      });
    return () => controller.abort();
  }, [allowed, authorization, page, revision]);
  function navigate(next: number) {
    setResult(null);
    setError("");
    setPage(next);
  }
  if (!allowed) return <p>Menu management permission is required.</p>;
  return (
    <main className="mx-auto max-w-4xl space-y-5 p-4 text-[#173a37]">
      <Link href="/admin/menu" className="inline-block min-h-11 underline">
        Back to menu management
      </Link>
      <h1 className="text-3xl font-bold">Menu categories</h1>
      <p>
        Create your categories first, then add individual products in the{" "}
        <Link href="/admin/menu/workspace" className="underline">
          menu workspace
        </Link>
        . Categories are shared across branches. Empty categories do not make
        products available to customers.
      </p>
      {notice && (
        <p role="status" className="rounded-xl bg-[#e8f0eb] p-3">
          {notice}
        </p>
      )}
      <MenuCategoryCreate
        onCreated={(category) => {
          setNotice(
            `${category.name} created. You can now select it when adding products.`,
          );
          setResult(null);
          setError("");
          setPage(0);
          setRevision((n) => n + 1);
        }}
      />
      <section className="rounded-xl border bg-white p-4">
        <h2 className="text-xl font-bold">Existing categories</h2>
        <button
          type="button"
          className="min-h-11 underline"
          onClick={() => {
            setError("");
            setResult(null);
            setRevision((n) => n + 1);
          }}
        >
          Refresh categories
        </button>
        {error && (
          <p role="alert" className="text-red-800">
            {error}
          </p>
        )}
        {!result && !error && <p role="status">Loading categories…</p>}
        {result && (
          <>
            {!result.content.length && (
              <p>
                No categories on this page. Create your first category above.
              </p>
            )}
            <ul className="divide-y">
              {result.content.map((category) => (
                <li key={category.id} className="py-3">
                  <strong>{category.name}</strong>{" "}
                  <span className="text-sm">
                    ({category.code}) · Order {category.displayOrder} ·{" "}
                    {category.active ? "Active" : "Inactive"}
                  </span>
                  {category.description && (
                    <p className="mt-1 text-sm">{category.description}</p>
                  )}
                </li>
              ))}
            </ul>
            <nav
              aria-label="Category pages"
              className="mt-4 flex items-center gap-4"
            >
              <button
                type="button"
                disabled={page === 0}
                onClick={() => navigate(page - 1)}
                className="min-h-11 rounded-lg border px-3 disabled:opacity-50"
              >
                Previous
              </button>
              <span>
                Page {page + 1} of {Math.max(1, result.totalPages)}
              </span>
              <button
                type="button"
                disabled={page + 1 >= result.totalPages}
                onClick={() => navigate(page + 1)}
                className="min-h-11 rounded-lg border px-3 disabled:opacity-50"
              >
                Next
              </button>
            </nav>
          </>
        )}
      </section>
    </main>
  );
}
