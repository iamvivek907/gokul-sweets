import type {Branch} from "@/types/branch";
export const STORE_ORIGIN="https://gokulsweets.in";
export function branchPath(id:number):string|null {
    return Number.isSafeInteger(id)&&id>0?`/branches/${id}`:null;
}
export function branchDescription(branch:Branch):string {
    const location=[branch.address,branch.city,branch.state].filter(Boolean).join(", ");
    return (branch.description?.trim()||`Explore the menu and pickup options at ${branch.name}${location?`, ${location}`:""}. Check branch details before ordering.`).slice(0,300);
}
export function branchSchema(branch:Branch):Record<string,unknown>|null {
    const path=branchPath(branch.id);
    if(!path||!branch.active||!branch.name?.trim()||!branch.address?.trim())return null;
    const schema:Record<string,unknown>={"@context":"https://schema.org","@type":"Restaurant",name:branch.name,url:STORE_ORIGIN+path,description:branchDescription(branch),address:{"@type":"PostalAddress",streetAddress:branch.address,addressCountry:"IN",...(branch.city?{addressLocality:branch.city}:{}),...(branch.state?{addressRegion:branch.state}:{}),...(branch.pincode?{postalCode:branch.pincode}:{})}};
    if(branch.phone?.trim())schema.telephone=branch.phone;
    if(typeof branch.latitude==="number"&&Number.isFinite(branch.latitude)&&Math.abs(branch.latitude)<=90&&typeof branch.longitude==="number"&&Number.isFinite(branch.longitude)&&Math.abs(branch.longitude)<=180)schema.geo={"@type":"GeoCoordinates",latitude:branch.latitude,longitude:branch.longitude};
    try{const image=new URL(branch.coverImageUrl??"");if(image.protocol==="https:"&&!image.username&&!image.password)schema.image=image.href;}catch{/* Missing images do not prevent valid business details. */}
    return schema;
}
export function safeStructuredData(value:unknown):string {
    return JSON.stringify(value).replace(/</g,"\\u003c");
}
