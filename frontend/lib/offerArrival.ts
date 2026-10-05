import {getCartSnapshot} from "./cartStorage";
import {getStoredBranchSnapshot} from "./branchStorage";
import {getPickupSlotSnapshot,parsePickupSlot} from "./checkoutStorage";
const KEY="gokul-offer-arrival";
export function offerArrivalContext(){if(typeof window==="undefined")return "";try{const p=parsePickupSlot(getPickupSlotSnapshot());return JSON.stringify([getCartSnapshot(),getStoredBranchSnapshot(),p?.date,p?.slot.id,p?.pickupType]);}catch{return "";}}
export function startOfferArrival(){try{sessionStorage.setItem(KEY,JSON.stringify({context:offerArrivalContext(),at:Date.now()}));}catch{/* Checkout must still open when storage is unavailable. */}}
export function takeOfferArrival(){try{const raw=sessionStorage.getItem(KEY);sessionStorage.removeItem(KEY);const value=raw?JSON.parse(raw):null;return value&&value.context===offerArrivalContext()&&Date.now()-value.at>=0&&Date.now()-value.at<30000?value as {context:string;at:number}:null;}catch{return null;}}
