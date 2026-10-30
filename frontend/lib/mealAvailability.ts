import type {MenuProduct} from '@/types/menu';
import type {MealDefinition} from '@/types/meal';
import type {ItemAvailability} from '@/services/availabilityApi';
/** Reuse the menu's selected-pickup preview without creating another polling loop. */
export function mealProductsForPickup(meal:MealDefinition, products:MenuProduct[], quantity:number,
 items:ItemAvailability[]|undefined, checking:boolean, dated:boolean){
 const map=new Map(products.map(p=>[p.id,{...p,available:p.catalogAvailable??p.available}]));
 for(const option of meal.groups.flatMap(g=>g.options)){
  const p=map.get(option.productId);if(!p)continue;
  const state=items?.find(i=>i.productId===p.id);
  const demand=(option.weightGrams??option.quantity)*quantity;
  p.available=p.available&&(!dated||!checking&&!!state?.available&&(state.availableQuantity==null||state.availableQuantity>=demand));
 }
 return map;
}
export function mealCanBeBuilt(meal:MealDefinition,products:Map<number,MenuProduct>){
 return meal.groups.every(group=>group.options.filter(o=>products.get(o.productId)?.available).length>=group.minimum);
}
