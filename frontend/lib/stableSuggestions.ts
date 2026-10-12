/** Keep existing visible products in place; append new candidates and refresh their data. */
export function stableSuggestions<T extends {product: {id: number}}>(previous: T[], next: T[]): T[] {
 const remaining = new Map(next.map(item => [item.product.id, item]));
 const kept = previous.flatMap(item => {
  const current = remaining.get(item.product.id);
  remaining.delete(item.product.id);
  return current ? [current] : [];
 });
 return [...kept, ...remaining.values()];
}
