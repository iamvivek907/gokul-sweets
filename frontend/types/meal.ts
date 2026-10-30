import type {MenuProduct} from './menu';
export interface MealOption {key:string;productId:number;quantity:number;weightGrams:number|null;upgrade:number;}
export interface MealGroup {key:string;title:string;titleHi?:string|null;minimum:number;maximum:number;options:MealOption[];}
export interface MealDefinition {id:number;branchId:number;version:number;title:string;titleHi?:string|null;description?:string|null;imageUrl?:string|null;basePrice:number;pricingMode?:'FIXED'|'SELECTED_ITEMS';maximumDiscount?:number|null;discountType:'NONE'|'FIXED'|'PERCENT';discountValue:number;estimatedCost?:number|null;active:boolean;groups:MealGroup[];}
export interface MealClaim {instanceId:string;mealId:number;version:number;quantity:number;optionIds:string[];}
export interface MealComponent {productId:number;quantity:number;weightGrams:number|null;regularTotal:number;unitPrice?:number|null;}
export interface MealQuote {mealId:number;version:number;title:string;quantity:number;regularTotal:number;baseTotal:number;discount:number;upgrades:number;total:number;components:MealComponent[];}
export interface CartMeal extends MealClaim {title:string;total:number;regularTotal:number;components:MealComponent[];}
export type MealProducts=Map<number,MenuProduct>;
