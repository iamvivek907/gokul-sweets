import type {MenuCategory,MenuProduct} from "../types/menu";
/** Keep backend category order and product order; search results stay in their own sections. */
export function groupMenuProducts(categories:MenuCategory[],products:MenuProduct[]) {
 return categories.map(category=>({...category,products:products.filter(product=>product.categoryId===category.id)})).filter(category=>category.products.length>0);
}
