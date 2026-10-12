"use client";
import { useEffect, useRef, type ReactNode } from "react";
import styles from "./MenuWorkspace.module.css";
export default function WorkspaceDialog({
  title,
  scope,
  busy,
  onClose,
  children,
  footer,
}: {
  title: string;
  scope: string;
  busy: boolean;
  onClose: () => void;
  children: ReactNode;
  footer: ReactNode;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    const dialog = ref.current;
    dialog?.showModal();
    const overflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      dialog?.close();
      document.body.style.overflow = overflow;
      if (previous?.isConnected) previous.focus();
      else document.getElementById("workspace-product-search")?.focus();
    };
  }, []);
  return (
    <dialog
      ref={ref}
      className={styles.dialog}
      aria-labelledby="workspace-dialog-title"
      onCancel={(e) => {
        e.preventDefault();
        if (!busy) onClose();
      }}
    >
      <div className={styles.dialogHeader}>
        <div>
          <small>{scope}</small>
          <h2 id="workspace-dialog-title">{title}</h2>
        </div>
        <button
          type="button"
          disabled={busy}
          onClick={onClose}
          aria-label="Close editor"
        >
          ✕
        </button>
      </div>
      <div className={styles.dialogBody}>{children}</div>
      <footer className={styles.dialogFooter}>{footer}</footer>
    </dialog>
  );
}
