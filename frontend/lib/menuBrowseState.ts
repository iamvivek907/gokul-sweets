export type MenuBrowseState={savedAt:number;search:string;categoryId:number|null;categories:number[]|null;maximum:number|null;portions:boolean;browseId:number|null;scrollY:number};
const prefix="gokul-menu-browse-v1:";
export function readMenuBrowse(storage:Pick<Storage,"getItem">|null,branchId:number,now=Date.now()):MenuBrowseState|null{
 try{if(!storage)return null;const value=JSON.parse(storage.getItem(prefix+branchId)??"null") as MenuBrowseState|null;
  if(!value||!Number.isFinite(value.savedAt)||now-value.savedAt<0||now-value.savedAt>1800000||typeof value.search!=="string"||value.search.length>200)return null;
  const id=(n:unknown)=>n===null||Number.isInteger(n)&&Number(n)>0;
  if(!id(value.categoryId)||!id(value.browseId)||value.categories!==null&&(!Array.isArray(value.categories)||value.categories.length>500||!value.categories.every(n=>Number.isInteger(n)&&n>0))||value.maximum!==null&&(!Number.isFinite(value.maximum)||value.maximum<0)||typeof value.portions!=="boolean"||!Number.isFinite(value.scrollY)||value.scrollY<0)return null;
  return value;
 }catch{return null;}
}
export function writeMenuBrowse(storage:Pick<Storage,"setItem">|null,branchId:number,value:MenuBrowseState){try{storage?.setItem(prefix+branchId,JSON.stringify(value));}catch{/* Browsing remains usable with storage blocked/full. */}}

export function browserMenuStorage():Storage|null{try{return window.sessionStorage;}catch{return null;}}
