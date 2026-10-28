"use client";
import {useState} from "react";
import {useMenuExperience} from "./MenuExperience";
import MenuDiscoverySheet from "./MenuDiscoverySheet";
import {T} from "@/lib/language";
export default function SavedDishesHelp(){
 const {products,savedIds,clearSaved}=useMenuExperience();const [confirm,setConfirm]=useState(false),[error,setError]=useState(false);
 const missing=savedIds.some(id=>!products.some(product=>product.id===id));
 return <div className="menu-saved-help"><p><T text="Saved on this device for this branch"/></p>{missing&&<p><T text="Some saved dishes are no longer on this menu."/></p>}{savedIds.length>0&&<button type="button" onClick={()=>setConfirm(true)}><T text="Clear saved dishes"/></button>}{confirm&&<MenuDiscoverySheet title="Clear saved dishes?" onClose={()=>setConfirm(false)}><p><T text="Remove saved dishes from this branch on this device?"/></p><button type="button" onClick={()=>setConfirm(false)}><T text="Keep saved dishes"/></button><button type="button" onClick={()=>{if(clearSaved()){setError(false);setConfirm(false);}else setError(true);}}><T text="Clear saved dishes"/></button>{error&&<p role="alert"><T text="Favourites could not be saved on this device."/></p>}</MenuDiscoverySheet>}</div>;
}
