"use client";
import Link from "next/link";
import BranchSelector from "@/components/branch/BranchSelector";
import styles from "./HomeQuickActions.module.css";
import { useEffect, useState } from "react";
import dynamic from "next/dynamic";
import { useSelectedBranch } from "@/hooks/useSelectedBranch";
import { useStorefrontFeatures } from "@/hooks/useStorefrontFeatures";
import { useMenuFavourites } from "@/hooks/useMenuFavourites";
import { useCart } from "@/hooks/useCart";
import { apiClient } from "@/services/apiClient";
import { getVerifiedOrderPage } from "@/services/orderApi";
import { subscribeCustomerIdentityChanges } from "@/lib/customerIdentityEvents";
import { getPendingOrderSnapshot } from "@/lib/pendingOrderStorage";
import { formatOrderCurrency } from "@/lib/orderTracking";
import type { CustomerOrderSummaryResponse } from "@/types/order";
import type { CustomerSession } from "./CustomerIdentityPanel";
import { T, useTranslation } from "@/lib/language";
const ReorderDialog = dynamic(() => import("./ReorderDialog"));
const unfinished = () => {
  try {
    return (
      !!getPendingOrderSnapshot() ||
      !!sessionStorage.getItem("gokul-mobile-order-attempt")
    );
  } catch {
    return true;
  }
};
export default function HomeQuickActions() {
  const t = useTranslation(),
    { branch } = useSelectedBranch(),
    features = useStorefrontFeatures(),
    saved = useMenuFavourites(branch?.id),
    cart = useCart();
  const enabled =
    !!features?.contextualStorefrontV2 &&
    (!!features.futuristicStorefrontV2 || !!features.checkoutExperienceV2);
  const [revision, setRevision] = useState(0),
    [snapshot, setSnapshot] = useState<{
      branchId: number;
      revision: number;
      order: CustomerOrderSummaryResponse;
      phone?: string;
    } | null>(null),
    [reorder, setReorder] = useState(false);
  useEffect(
    () =>
      subscribeCustomerIdentityChanges(
        () => {
          setSnapshot(null);
          setReorder(false);
          setRevision((n) => n + 1);
        },
        { revalidateOnResume: false },
      ),
    [],
  );
  useEffect(() => {
    if (!branch || !enabled) return;
    const controller = new AbortController(),
      signal = AbortSignal.any([controller.signal, AbortSignal.timeout(10000)]);
    void apiClient<CustomerSession>("/api/customer/identity/me", {
      credentials: "include",
      signal,
    })
      .then(async (session) => {
        if (!session.authenticated || signal.aborted) return;
        const page = await getVerifiedOrderPage(null, signal, {
          status: "COMPLETED",
          limit: "1",
          branchId: String(branch.id),
        });
        const order = page.orders?.[0];
        if (
          !signal.aborted &&
          order?.branchId === branch.id &&
          ["PICKED_UP", "DELIVERED"].includes(order.orderStatus)
        )
          setSnapshot({
            branchId: branch.id,
            revision,
            phone: session.phone,
            order,
          });
      })
      .catch(() => {});
    return () => controller.abort();
  }, [branch, enabled, revision]);
  if (!branch || !features || !enabled) return null;
  const previous =
    snapshot?.branchId === branch.id && snapshot.revision === revision
      ? snapshot
      : null;
  return (
    <section
      className={`home-quick-actions ${styles.panel}`}
      aria-label={t("Continue with Gokul")}
    >
      <div className={styles.branch}>
        <div className={styles.identity}>
          <p><T text="Selected pickup branch" /></p>
          <h2>{branch.name}</h2>
          {branch.city && <small>{branch.city}</small>}
        </div>
        <div className={styles.actions}>
          <Link className={`home-continue ${styles.primary}`} href={cart.isEmpty ? "/menu" : "/cart"}>
            <T text={cart.isEmpty ? "View menu" : "Review saved cart"} />
            <span aria-hidden="true">→</span>
          </Link>
          <div className={styles.switch}><BranchSelector compact /></div>
        </div>
      </div>
      {saved.ids.length > 0 && (
        <Link href="/menu?saved=1">
          <T text="Saved dishes" /> · {saved.ids.length}
          <small>
            <T text="Saved on this device for this branch" />
          </small>
        </Link>
      )}
      {previous && (
        <div className="home-order-again">
          <p>
            <T text="Your last completed order" /> ·{" "}
            {formatOrderCurrency(previous.order.totalAmount)}
          </p>
          <button
            type="button"
            disabled={unfinished()}
            onClick={() => {
              if (!unfinished()) setReorder(true);
            }}
          >
            <T text="Review and order again" />
          </button>
          <small>
            <T text="Current prices and pickup availability are checked before you add." />
          </small>
        </div>
      )}
      {reorder && previous && (
        <ReorderDialog
          key={`${previous.phone}:${previous.order.orderNumber}`}
          order={previous.order}
          phone={previous.phone}
          features={features}
          compact
          onClose={() => setReorder(false)}
        />
      )}
    </section>
  );
}
