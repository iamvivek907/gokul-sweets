/** Coalesce stock invalidations while current-context reads finish. Context changes still abort. */
export function createMenuPreviewRefresh(refresh: () => void) {
    const pending = new Set<symbol>();
    let dirty = false;
    return {
        request() {
            if (pending.size) dirty = true;
            else refresh();
        },
        acquire() {
            const token = Symbol();
            pending.add(token);
            return (publish = true) => {
                if (!pending.delete(token) || pending.size) return;
                const queued = dirty;
                dirty = false;
                if (publish && queued) refresh();
            };
        }
    };
}
