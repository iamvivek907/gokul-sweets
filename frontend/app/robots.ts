import type {MetadataRoute} from "next";
export default function robots(): MetadataRoute.Robots {
    if (process.env.NEXT_PUBLIC_API_URL?.includes("api-dev.gokulsweets.in")) return {rules:{userAgent:"*",disallow:"/"}};
    return {rules:{userAgent:"*",allow:"/",disallow:["/admin", "/staff", "/checkout", "/orders", "/profile", "/cart"]}, sitemap:"https://gokulsweets.in/sitemap.xml"};
}
