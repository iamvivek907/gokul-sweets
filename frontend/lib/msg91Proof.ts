/** The provider proof still needs server-side MSG91 verification before session issuance. */
export function proofFromWidget(result: unknown): string | null {
    const data = result && typeof result === "object" ? result as Record<string, unknown> : null;
    // A callback can include a human-readable message alongside a token. Only
    // use message as proof when it looks like the JWT issued by MSG91.
    if (data?.type !== undefined && data.type !== "success") return null;
    const nested = data?.data && typeof data.data === "object"
        ? data.data as Record<string, unknown> : null;
    const explicit = typeof result === "string" ? result :
        data?.accessToken ?? data?.["access-token"] ?? nested?.accessToken ??
        nested?.["access-token"];
    const message = data?.message;
    const value = explicit ?? (typeof message === "string" &&
        /^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$/.test(message)
        ? message : null);
    return typeof value === "string" && value.length > 0 && value.length <= 4096
        ? value : null;
}
