/** Device-only branch preferences, independent of carts, identity and pickup. */
export const favouriteKey = "gokul-menu-favourites-v1",
  recentSearchKey = "gokul-menu-searches-v1",
  MENU_PREFERENCE_EVENT = "gokul-menu-preferences";
type Storage = Pick<globalThis.Storage, "getItem" | "setItem">;
const positive = (n: unknown): n is number =>
  typeof n === "number" && Number.isSafeInteger(n) && n > 0;
function object(raw: string | null): Record<string, unknown> {
  try {
    const v = JSON.parse(raw ?? "{}");
    return v && typeof v === "object" && !Array.isArray(v) ? v : {};
  } catch {
    return {};
  }
}
export function favouriteIds(raw: string | null, branchId?: number): number[] {
  if (!positive(branchId)) return [];
  const v = object(raw)[String(branchId)];
  return Array.isArray(v) ? [...new Set(v.filter(positive))].slice(0, 50) : [];
}
export function toggleFavourites(
  storage: Storage,
  branchId: number,
  ids: number[],
): "saved" | "limit" | "unavailable" {
  if (
    !positive(branchId) ||
    !ids.length ||
    ids.length > 20 ||
    !ids.every(positive)
  )
    return "unavailable";
  try {
    const raw = storage.getItem(favouriteKey),
      all = object(raw),
      current = favouriteIds(raw, branchId),
      remove = ids.every((id) => current.includes(id));
    const next = remove
      ? current.filter((id) => !ids.includes(id))
      : [...new Set([...current, ...ids])];
    if (next.length > 50) return "limit";
    const bounded: Record<string, number[]> = {};
    for (const key of Object.keys(all)
      .filter((k) => positive(Number(k)) && k !== String(branchId))
      .slice(-9))
      bounded[key] = favouriteIds(raw, Number(key));
    bounded[String(branchId)] = next;
    storage.setItem(favouriteKey, JSON.stringify(bounded));
    return "saved";
  } catch {
    return "unavailable";
  }
}
export function clearFavourites(storage: Storage, branchId: number): boolean {
  if (!positive(branchId)) return false;
  try {
    const all = object(storage.getItem(favouriteKey));
    delete all[String(branchId)];
    storage.setItem(favouriteKey, JSON.stringify(all));
    return true;
  } catch {
    return false;
  }
}
export function recentSearches(raw: string | null, branchId: number): string[] {
  const v = object(raw)[String(branchId)];
  return Array.isArray(v)
    ? v
        .filter(
          (s): s is string =>
            typeof s === "string" && s.trim().length >= 2 && s.length <= 60,
        )
        .slice(0, 8)
    : [];
}
export function rememberMenuSearch(
  storage: Storage,
  branchId: number,
  text: string,
): boolean {
  text = text.trim();
  if (!positive(branchId) || text.length < 2 || text.length > 60) return false;
  try {
    const raw = storage.getItem(recentSearchKey),
      all = object(raw),
      result: Record<string, string[]> = {};
    for (const key of Object.keys(all)
      .filter((k) => positive(Number(k)) && k !== String(branchId))
      .slice(-9))
      result[key] = recentSearches(raw, Number(key));
    result[String(branchId)] = [
      text,
      ...recentSearches(raw, branchId).filter(
        (s) => s.toLocaleLowerCase() !== text.toLocaleLowerCase(),
      ),
    ].slice(0, 8);
    storage.setItem(recentSearchKey, JSON.stringify(result));
    return true;
  } catch {
    return false;
  }
}
export function clearRecentSearches(
  storage: Storage,
  branchId: number,
): boolean {
  try {
    const all = object(storage.getItem(recentSearchKey));
    delete all[String(branchId)];
    storage.setItem(recentSearchKey, JSON.stringify(all));
    return true;
  } catch {
    return false;
  }
}
