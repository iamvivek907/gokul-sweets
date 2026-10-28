import type {MenuProduct} from '@/types/menu';
export type PortionGroup={key:string;title:string;choices:{productId:number;label:string}[]};
export function mobileMenuRows(products:MenuProduct[],groups:PortionGroup[],catalog:MenuProduct[]=products){
 const result:{product:MenuProduct;group?:PortionGroup;products?:MenuProduct[]}[]=[],shown=new Set<string>();
 for(const product of products){
  const group=groups.find(g=>g.choices.some(c=>c.productId===product.id));
  if(!group){result.push({product});continue;}
  if(shown.has(group.key))continue;shown.add(group.key);
  const options=group.choices.flatMap(c=>catalog.filter(p=>p.id===c.productId));
  result.push({product:options[0],group,products:options});
 }
 return result;
}
export function matchesMobileFilters(product:MenuProduct,categories:number[]|null,maximum:number|null,portionsOnly:boolean,groups:PortionGroup[]){
 return (!categories?.length||categories.includes(product.categoryId))&&(maximum===null||product.price<=maximum)&&(!portionsOnly||groups.some(g=>g.choices.some(c=>c.productId===product.id)));
}

/** Keep complete size groups together when introducing campaigns after leading dishes. */
export function splitMenuLead(products:MenuProduct[],groups:PortionGroup[],count=4):{lead:MenuProduct[];rest:MenuProduct[]}{
 const selected=new Set(mobileMenuRows(products,groups).slice(0,count).flatMap(row=>row.products?.map(p=>p.id)??[row.product.id]));
 return {lead:products.filter(p=>selected.has(p.id)),rest:products.filter(p=>!selected.has(p.id))};
}
