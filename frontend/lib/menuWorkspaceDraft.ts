export function readWorkspaceDraft<T>(key: string): T | null {
  try {
    return JSON.parse(sessionStorage.getItem(key) || "null") as T | null;
  } catch {
    return null;
  }
}
export function writeWorkspaceDraft(key: string, value: unknown) {
  try {
    sessionStorage.setItem(key, JSON.stringify(value));
  } catch {}
}
export function clearWorkspaceDraft(key: string) {
  try {
    sessionStorage.removeItem(key);
  } catch {}
}
