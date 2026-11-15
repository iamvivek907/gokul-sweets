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

export async function setOrderPushBinding(audience: Audience, owner: string, registrationId: string | null, signal?: AbortSignal, pending = false) {
    const cache = await caches.open(cacheName);
    const existing = await cache.match(bindingKey(audience));
    const previous = existing ? await existing.json() as Binding : null;
    if (previous?.owner === owner && previous.registrationId === registrationId && (!previous.pending || pending)) return;
    signal?.throwIfAborted();
    await cache.put(bindingKey(audience), new Response(JSON.stringify({owner, registrationId, ...(pending ? {pending: true} : {})})));
}

async function closeOrderNotifications(audience: Audience) {
    const worker = await navigator.serviceWorker?.getRegistration();
    const notifications = await worker?.getNotifications?.();
    for (const notice of notifications ?? []) {
        if (notice.tag.startsWith(`gokul-${audience}-`)) notice.close();
    }
}

/** Changes the device owner before restoring notifications for a different account. */
export async function switchOrderPushOwner(audience: Audience, owner: string, signal: AbortSignal, keepCurrent = true) {
    if (typeof caches === "undefined") return;
    const previous = await readOrderPushBinding(audience);
    if (previous?.owner === owner && (keepCurrent || !previous.registrationId)) return;
    signal.throwIfAborted();
    await setOrderPushBinding(audience, owner, null, signal);
    await closeOrderNotifications(audience).catch(() => {});
}

/** Stop displaying this account’s queued notifications after confirmed logout. */
export async function clearOrderPushBinding(audience: Audience, owner?: string) {
    try {
        const binding = await readOrderPushBinding(audience);
        if (owner && binding && binding.owner !== owner) return;
        await setOrderPushBinding(audience, "", null);
        await closeOrderNotifications(audience);
    } catch { /* The server has already stopped delivery. */ }
}
