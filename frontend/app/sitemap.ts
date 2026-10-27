import type {MetadataRoute} from "next";
import {serverBranches} from "@/lib/serverBranches";
import {branchPath,STORE_ORIGIN} from "@/lib/branchSeo";
export const revalidate=3600;
export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
    if (process.env.NEXT_PUBLIC_API_URL?.includes("api-dev.gokulsweets.in")) return [];
    const paths=["", "/branches", "/menu", "/about", "/careers",...(await serverBranches()).map(branch=>branchPath(branch.id)!)];
    return [...new Set(paths)].map(path=>({url:STORE_ORIGIN+path}));
}
