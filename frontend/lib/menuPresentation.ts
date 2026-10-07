import type {MenuCategory,MenuProduct} from "@/types/menu";
export function menuFamily(name:string):string {
 if(/sweet|mithai|मिठाई/i.test(name))return "Sweets";
 if(/bakery|cake|pastr|बेकरी/i.test(name))return "Bakery";
 if(/snack|dairy|drink|beverage|biscuit|chips|namkeen|नमकीन|पेय/i.test(name))return "Snacks";
 if(/food|meal|lunch|dinner|thali|breakfast|starter|भोजन/i.test(name))return "Food";
 return name;
}
export function menuShortcuts(categories:MenuCategory[]):MenuCategory[]{
 const groups=new Map<string,MenuCategory>();
 for(const category of categories){const name=menuFamily(category.name),group=groups.get(name);if(group)group.products.push(...category.products);else groups.set(name,{...category,name,products:[...category.products]});}
 const order=["Food","Sweets","Bakery","Snacks"];
 return [...groups.values()].sort((a,b)=>(order.indexOf(a.name)<0?4:order.indexOf(a.name))-(order.indexOf(b.name)<0?4:order.indexOf(b.name)));
}

export function retailCollections(categories:MenuCategory[],products:MenuProduct[]):MenuCategory[]{
 const photo=products.filter(p=>/dairy|drink|beverage/i.test(p.categoryName));
 const photoIds=new Set(photo.map(p=>p.id));
 const compact=products.filter(p=>!photoIds.has(p.id));
 const collections:MenuCategory[]=[];
 if(photo.length)collections.push({id:photo[0].categoryId,name:"Everyday favourites",description:null,displayOrder:0,products:[...photo].sort((a,b)=>Number(/drink|beverage/i.test(a.categoryName))-Number(/drink|beverage/i.test(b.categoryName)))});
 for(const category of categories){const matches=compact.filter(p=>p.categoryId===category.id);if(matches.length)collections.push({...category,products:matches});}
 return collections;
}
