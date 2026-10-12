/** Reorder only relevant backend suggestions; never add products or alter portion/price. */
export function rankAddOns<T extends {portionTotal:number}>(suggestions:T[],needed:number|null) {
 if(needed==null || needed<=0)return suggestions;
 return suggestions.map((item,index)=>({item,index})).sort((a,b)=>{
  const aReaches=a.item.portionTotal>=needed,bReaches=b.item.portionTotal>=needed;
  if(aReaches!==bReaches)return aReaches?-1:1;
  return (aReaches?a.item.portionTotal-b.item.portionTotal:b.item.portionTotal-a.item.portionTotal)||a.index-b.index;
 }).map(entry=>entry.item);
}
