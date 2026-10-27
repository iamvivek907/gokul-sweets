import type {MetadataRoute} from "next";
export default function sitemap(): MetadataRoute.Sitemap {
    if (process.env.NEXT_PUBLIC_API_URL?.includes("api-dev.gokulsweets.in")) return [];
    return ["", "/branches", "/menu", "/about", "/careers"].map(path => ({url:`https://gokulsweets.in${path}`}));
}
