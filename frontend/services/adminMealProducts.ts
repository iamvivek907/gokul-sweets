import {adminFetch} from './adminApi';
import type {MenuProduct} from '@/types/menu';
type Dish={id:number;name:string;saleMode:MenuProduct['saleMode'];price:number;minimumGrams:number;stepGrams:number;available:boolean};
/** Load bounded cursor pages only while the branch's meal editor is mounted. */
export async function getAdminMealProducts(branchId:number,signal:AbortSignal):Promise<MenuProduct[]>{
 const products=new Map<number,MenuProduct>();let after=0;
 for(let page=0;page<100;page++){
  const response=await adminFetch(`/api/admin/branches/${branchId}/meals/products?afterId=${after}`,'staff-session',{signal});
  if(!response.ok)throw Error('Could not load branch dishes. Retry meals.');
  const data:{products:Dish[];nextAfter:number|null}=await response.json();
  if(!Array.isArray(data.products)||data.products.length>50)throw Error('Invalid branch dish page. Retry meals.');
  for(const p of data.products)products.set(p.id,{id:p.id,name:p.name,price:Number(p.price),available:p.available,catalogAvailable:p.available,saleMode:p.saleMode,minimumWeightGrams:p.minimumGrams,weightStepGrams:p.stepGrams,categoryId:0,categoryName:'',description:null,imageUrl:null});
  if(data.nextAfter===null)return [...products.values()];
  if(!Number.isSafeInteger(data.nextAfter)||data.nextAfter<=after)throw Error('Invalid branch dish cursor. Retry meals.');
  after=data.nextAfter;
 }
 throw Error('This branch has too many dishes to load safely. Narrow the catalogue before configuring meals.');
}
