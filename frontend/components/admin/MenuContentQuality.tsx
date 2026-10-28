"use client";
import "./MenuContentDetails.css";
import { menuDescription } from "@/lib/menuPresentationDetails";
import type { WorkspaceItem } from "@/services/menuWorkspaceApi";
export default function MenuContentQuality({
  items,
  onEdit,
}: {
  items: WorkspaceItem[];
  onEdit: (item: WorkspaceItem) => void;
}) {
  const missingPhotos = items.filter((item) => !item.imageUrl),
    missingCopy = items.filter((item) => !menuDescription(item));
  const incomplete = items.filter(
    (item) => !item.imageUrl || !menuDescription(item),
  );
  return (
    <details className="admin-menu-quality">
      <summary>Menu content checklist · this page</summary>
      <p>
        {items.length} dishes on this page · {missingPhotos.length} without a
        photo · {missingCopy.length} without a useful description.
      </p>
      <p>
        Describe taste, portions and preparation accurately. Add confirmed
        serving, ingredient and allergen facts in each dish’s details. Preview
        photo crops on phone and desktop.
      </p>
      {incomplete.length > 0 ? (
        <ul>
          {incomplete.slice(0, 8).map((item) => (
            <li key={item.productId}>
              <span>
                {item.name} ·{" "}
                {!item.imageUrl ? "Photo needed" : "Description needed"}
              </span>
              <button type="button" onClick={() => onEdit(item)}>
                Edit {item.name}
              </button>
            </li>
          ))}
        </ul>
      ) : (
        <p>
          Every dish on this page has a photo and useful description. Check
          crops and factual details before publishing.
        </p>
      )}
    </details>
  );
}
