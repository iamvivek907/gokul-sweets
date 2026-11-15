type Audience = "staff" | "customer";
type Binding = {owner: string; registrationId: string | null; pending?: true};
const cacheName = "gokul-order-push-binding-v1";
const bindingKey = (audience: Audience) => `/.__gokul-order-push/${audience}`;

export async function readOrderPushBinding(audience: Audience): Promise<Binding | null> {
    try {
        const cache = await caches.open(cacheName);
        const entry = await cache.match(bindingKey(audience));
        return entry ? await entry.json() as Binding : null;
    } catch {return null;}
}

async function updateBinding(audience: Audience, update: (previous: Binding | null) => Binding | null, signal?: AbortSignal, afterWrite?: () => Promise<void>) {
    const write = async () => {
        signal?.throwIfAborted();
        const key = bindingKey(audience);
        const cache = await caches.open(cacheName);
        const existing = await cache.match(key);
        const previous = existing ? await existing.json() as Binding : null;
        signal?.throwIfAborted();
        const next = update(previous);
        if (!next || (previous?.owner === next.owner && previous.registrationId === next.registrationId
            && previous.pending === next.pending)) return;
        try {
            await cache.put(key, new Response(JSON.stringify(next)));
        } catch (error) {
            // When storage is full, deleting our empty marker allows the worker's generic fallback.
            // Preserve an existing registration or another account's marker.
            if (audience === "staff" && previous && !previous.registrationId &&
                (previous.owner === "" || (next.pending && previous.owner === next.owner))) {
                await cache.delete(key).catch(() => {});
            }
            throw error;
        }
        await afterWrite?.();
    };
    return typeof navigator !== "undefined" && navigator.locks
        ? navigator.locks.request(`${cacheName}:${audience}`, {signal}, write)
        : write();
}

export async function setOrderPushBinding(audience: Audience, owner: string, registrationId: string | null, signal?: AbortSignal) {
    await updateBinding(audience, () => ({owner, registrationId}), signal);
}

/** Returns false for a known account mismatch. Storage errors still permit a server-verified restore. */
export async function bindStaffOrderPush(owner: string, registrationId: string, signal?: AbortSignal, pending = false) {
    let matches = true;
    try {
        await updateBinding("staff", previous => {
            if (previous && previous.owner !== owner) {
                matches = false;
                return null;
            }
            if (pending && previous?.registrationId === registrationId && !previous.pending) return null;
            return {owner, registrationId, ...(pending ? {pending: true} : {})};
        }, signal);
    } catch {
        signal?.throwIfAborted();
    }
    return matches;
}

async function closeOrderNotifications(audience: Audience) {
    const worker = await navigator.serviceWorker?.getRegistration();
    const notifications = await worker?.getNotifications?.();
    for (const notice of notifications ?? []) {
        if (notice.tag.startsWith(`gokul-${audience}-`)) notice.close();
    }
}

/** Changes the device owner before restoring notifications for a different account. */
export async function switchOrderPushOwner(audience: Audience, owner: string, signal: AbortSignal, keepCurrent = true, expectedOwner?: string | null) {
    if (typeof caches === "undefined") return;
    await updateBinding(audience, previous => {
        if (previous?.owner === owner && (keepCurrent || !previous.registrationId)) return null;
        if (expectedOwner !== undefined && (previous?.owner ?? null) !== expectedOwner) return null;
        return {owner, registrationId: null};
    }, signal, () => closeOrderNotifications(audience).catch(() => {}));
}

/** Stop displaying this account’s queued notifications after confirmed logout. */
export async function clearOrderPushBinding(audience: Audience, owner?: string) {
    try {
        await updateBinding(audience, previous =>
            owner && previous && previous.owner !== owner ? null : {owner: "", registrationId: null},
            undefined, () => closeOrderNotifications(audience));
    } catch { /* The server has already stopped delivery. */ }
}
