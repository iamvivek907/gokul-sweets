/** The provider proof still needs server-side MSG91 verification before session issuance. */
export function proofFromWidget(result: unknown): string | null {
    const data = result && typeof result === "object" ? result as Record<string, unknown> : null;
    const nested = data?.data && typeof data.data === "object"
        ? data.data as Record<string, unknown> : null;
    // MSG91's success callback may return the JWT as `message`. Never treat a
    // failure message as proof; the backend independently verifies every token.
    const value = typeof result === "string" ? result :
        data?.accessToken ?? data?.["access-token"] ?? nested?.accessToken ??
        nested?.["access-token"] ?? (data?.type === "success" ? data.message : null);
    return typeof value === "string" && value.length > 0 && value.length <= 4096
        ? value : null;
}
