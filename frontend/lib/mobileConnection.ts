/** Avoid optional media downloads on a constrained phone connection. */
export function constrainedPhoneConnection():boolean {
 if(typeof navigator==="undefined"||typeof matchMedia==="undefined"||!matchMedia("(max-width: 640px)").matches)return false;
 const connection=(navigator as Navigator&{connection?:{saveData?:boolean;effectiveType?:string}}).connection;
 return connection?.saveData===true||connection?.effectiveType==="slow-2g"||connection?.effectiveType==="2g"||connection?.effectiveType==="3g";
}
