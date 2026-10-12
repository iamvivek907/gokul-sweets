import type {CustomerDetails} from "@/types/customer";
export function verifiedCheckoutContact(session: {authenticated:boolean;phone?:string;name?:string}): CustomerDetails | null {
 if (!session.authenticated) return null;
 let phone=(session.phone??"").replace(/\D/g, "");
 if(phone.length===12&&phone.startsWith("91"))phone=phone.slice(2);
 if(!/^[6-9]\d{9}$/.test(phone))return null;
 return {name:session.name?.trim()||"GOKUL_GUEST",phone};
}
