/** Select only the rendered page, retaining other explicitly selected pages. */
export function selectWorkspacePage<T extends {productId:number}>(selected:T[],page:T[]):T[]{
 const merged=new Map(selected.map(item=>[item.productId,item]));
 for(const item of page)merged.set(item.productId,item);
 return [...merged.values()];
}
export function workspacePageSelected(selected:{productId:number}[],page:{productId:number}[]){
 const ids=new Set(selected.map(item=>item.productId));
 return page.length>0&&page.every(item=>ids.has(item.productId));
}
