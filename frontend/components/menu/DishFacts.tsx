"use client";
import {useMenuExperience} from "./MenuExperience";
import {T} from "@/lib/language";
export default function DishFacts({productId}:{productId:number}){
 const {details,detailsFailedIds}=useMenuExperience(),facts=details[productId];
 const hasFacts=facts&&([facts.servingInfo,facts.ingredients,facts.allergens].some(text=>!!text?.trim())||!!facts.spiceLevel);
 return <>{facts?.chefPick&&<p className="menu-chef-pick"><T text="Chef’s pick"/></p>}{hasFacts&&facts&&<dl className="menu-dish-facts">{facts.servingInfo?.trim()&&<div><dt><T text="Serving information"/></dt><dd>{facts.servingInfo}</dd></div>}{facts.ingredients?.trim()&&<div><dt><T text="Ingredients"/></dt><dd>{facts.ingredients}</dd></div>}{facts.allergens?.trim()&&<div><dt><T text="Allergen information"/></dt><dd>{facts.allergens}</dd></div>}{facts.spiceLevel&&<div><dt><T text="Spice level"/></dt><dd><T text={{MILD:"Mild",MEDIUM:"Medium",HOT:"Hot"}[facts.spiceLevel]}/></dd></div>}</dl>}{detailsFailedIds.includes(productId)&&<p role="status"><T text="Extra dish details are temporarily unavailable. Check with the branch about ingredients and allergies."/></p>}</>;
}
