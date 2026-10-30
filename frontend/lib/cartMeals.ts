import type {CartState,CartItem} from '@/types/cart';
import type {CartMeal,MealClaim,MealDefinition,MealProducts} from '@/types/meal';
const cents=(v:number)=>Math.round(v*100);
export function mealSavings(meals:CartMeal[]|undefined){return (meals??[]).reduce((n,m)=>n+Math.max(0,cents(m.regularTotal)-cents(m.total)),0)/100;}
/** Claims share actual component demand; a changed included dish removes its deal visibly. */
export function reconcileCartMeals(cart:CartState):CartState {
 const budget=new Map(cart.items.map(i=>[i.product.id,i.product.saleMode==='WEIGHT'?i.weightGrams??0:i.quantity]));
 const valid:CartMeal[]=[],ids=new Set<string>();let removed=false;
 for(const meal of (Array.isArray(cart.meals)?cart.meals:[]).slice(0,10)){
  if(!meal||!Number.isInteger(meal.mealId)||meal.mealId<=0||!Number.isInteger(meal.version)||meal.version<1||!Number.isInteger(meal.quantity)||meal.quantity<1||meal.quantity>10||typeof meal.instanceId!=='string'||!/^[A-Za-z0-9_-]{1,60}$/.test(meal.instanceId)||ids.has(meal.instanceId)||typeof meal.title!=='string'||meal.title.length>80||!Number.isFinite(meal.total)||!Number.isFinite(meal.regularTotal)||meal.total<=0||meal.total>meal.regularTotal||!Array.isArray(meal.optionIds)||meal.optionIds.length>30||!meal.optionIds.every(o=>typeof o==="string"&&/^[A-Za-z0-9_-]{1,40}$/.test(o))||!Array.isArray(meal.components)||!meal.components.length||meal.components.length>30){removed=true;continue;}
  const demand=new Map<number,number>();let okay=true;
  for(const p of meal.components){const q=p?.weightGrams??p?.quantity;if(!p||!Number.isInteger(p.productId)||!Number.isSafeInteger(q)||q<=0){okay=false;break;}demand.set(p.productId,(demand.get(p.productId)??0)+q);}
  for(const [id,q] of demand)if(q>(budget.get(id)??0))okay=false;
  const regularTotal=meal.components.reduce((sum,p)=>{const product=cart.items.find(i=>i.product.id===p.productId)?.product;return sum+Math.round(Number(product?.price)*(p.weightGrams==null?p.quantity:p.weightGrams/1000)*100);},0)/100;
  if(!okay||!Number.isFinite(regularTotal)||meal.total>regularTotal){removed=true;continue;}
  for(const [id,q] of demand)budget.set(id,(budget.get(id)??0)-q);
  ids.add(meal.instanceId);valid.push({...meal,regularTotal});
 }
 const identity=valid.length?JSON.stringify(valid.map(mealClaim)):undefined;
 return {...cart,meals:valid.length?valid:undefined,mealNotice:removed?'A meal deal was removed because its included dishes changed. Review your total.':cart.mealNotice,items:cart.items.map(i=>{const {mealIdentity:old,...plain}=i;void old;return identity?{...plain,mealIdentity:identity}:plain;})};
}
/** Checkout recovery changes quantities, never silently discards complete meal selections. */
export function adjustCartItems(cart:CartState,branchId:number,items:CartItem[]):CartState {
 if(cart.branchId!==branchId)throw Error('Your cart branch changed. Reopen checkout adjustments.');
 return reconcileCartMeals({...cart,items});
}
/** Branch-specific deals require explicit consent before keeping components at regular prices. */
export function switchCartBranch(cart:CartState,branchId:number,items:CartItem[],allowRegularPrices=false):CartState {
 if(cart.branchId===branchId)return adjustCartItems(cart,branchId,items);
 const hasMeals=!!cart.meals?.length;
 if(hasMeals&&!allowRegularPrices)throw Error('Meal deals cannot transfer between branches. Review the regular prices before switching.');
 return reconcileCartMeals({branchId,items,mealNotice:hasMeals?'Your meal dishes were kept at regular prices for this branch. Build a new meal from its menu to use a meal deal.':undefined});
}
export function mealClaim(m:CartMeal):MealClaim {return {instanceId:m.instanceId,mealId:m.mealId,version:m.version,quantity:m.quantity,optionIds:[...m.optionIds].sort()};}
/** An atomic builder edit preserves independently ordered quantities of identical dishes. */
export function removeMeal(cart:CartState,id:string):CartState {
 const meal=cart.meals?.find(m=>m.instanceId===id);if(!meal)return cart;
 const demand=new Map<number,number>();for(const p of meal.components)demand.set(p.productId,(demand.get(p.productId)??0)+(p.weightGrams??p.quantity));
 const items=cart.items.flatMap(i=>{const q=(i.product.saleMode==='WEIGHT'?i.weightGrams??0:i.quantity)-(demand.get(i.product.id)??0);return q<=0?[]:[i.product.saleMode==='WEIGHT'?{...i,weightGrams:q}:{...i,quantity:q}];});
 return reconcileCartMeals({...cart,items,meals:cart.meals?.filter(m=>m.instanceId!==id),mealNotice:undefined});
}
export function addMeal(cart:CartState,meal:CartMeal,products:MealProducts,branchId:number,replaceId?:string):CartState {
 if(cart.items.length&&cart.branchId!==branchId)throw Error('Your cart belongs to another branch. Change branch before building a meal.');
 const current=replaceId?removeMeal(cart,replaceId):cart;if((current.meals?.length??0)>=10)throw Error('You can add up to 10 customized meal selections per order.');
 const items:CartItem[]=current.items.map(i=>({...i}));
 for(const part of meal.components){const original=products.get(part.productId),product=original?{...original,price:part.unitPrice==null?original.price:Number(part.unitPrice)}:undefined;if(!product||!product.available)throw Error('A meal dish is no longer available.');const existing=items.find(i=>i.product.id===part.productId);
  if(existing){existing.product=product;if(product.saleMode==='WEIGHT')existing.weightGrams=(existing.weightGrams??0)+(part.weightGrams??0);else existing.quantity+=part.quantity;}
  else items.push({product,quantity:product.saleMode==='WEIGHT'?1:part.quantity,weightGrams:part.weightGrams});
 }
 const labels=new Map<number,string[]>();for(const chosen of [...(current.meals??[]),meal])for(const part of chosen.components){const text=`${chosen.title} #${chosen.instanceId.slice(-6)} ×${chosen.quantity} (${part.weightGrams==null?`${part.quantity} units`:`${part.weightGrams} g`})`;labels.set(part.productId,[...(labels.get(part.productId)??[]),text]);}if([...labels.values()].some(v=>v.join('; ').length>500))throw Error('Use fewer meal selections sharing the same dish in this order.');
 if(items.length>50)throw Error('This cart has too many different dishes. Use fewer meal choices.');
 return reconcileCartMeals({branchId,items,meals:[...(current.meals??[]),meal]});
}
export function mealEstimate(meal:MealDefinition,selected:string[],quantity:number,products:MealProducts){
 const set=new Set(selected);let upgrades=0,regular=0,valid=quantity>=1&&quantity<=10;
 for(const group of meal.groups){const choices=group.options.filter(o=>set.has(o.key));if(choices.length<group.minimum||choices.length>group.maximum)valid=false;
  for(const option of choices){const p=products.get(option.productId);if(!p||!p.available)valid=false;upgrades+=Number(option.upgrade);regular+=(p?.price??0)*(option.weightGrams!==null?option.weightGrams/1000:option.quantity);}}
 const discount=meal.discountType==='FIXED'?Number(meal.discountValue):meal.discountType==='PERCENT'?Math.round(cents(Number(meal.basePrice))*Math.round(Number(meal.discountValue)*100)/10000)/100:0;
 return {valid,total:Math.round((Number(meal.basePrice)-discount+upgrades)*100)*quantity/100,regularTotal:Math.round(regular*100)*quantity/100,discount:Math.round(discount*100)*quantity/100};
}
