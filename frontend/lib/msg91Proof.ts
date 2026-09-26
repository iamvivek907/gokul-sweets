/** The provider proof still needs server-side MSG91 verification before session issuance. */
export function proofFromWidget(result: unknown): string | null {
    const value = typeof result === "string" ? result :
        result && typeof result === "object" ?
            (result as Record<string, unknown>).accessToken ??
            (result as Record<string, unknown>)["access-token"] : null;
    return typeof value === "string" && value.length > 0 && value.length <= 4096
        ? value : null;
}
