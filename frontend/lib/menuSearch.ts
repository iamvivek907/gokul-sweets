/** Local catalogue search: no per-keystroke API calls or changes to product names. */
const aliases: Record<string,string> = {
 panner:"paneer", panir:"paneer", पनीर:"paneer", मिठाई:"sweet", मिठाइयाँ:"sweet",
 समोसा:"samosa", जलेबी:"jalebi", लस्सी:"lassi", बर्गर:"burger", पिज्जा:"pizza",
 thumsup:"thums up", rasgulla:"rasgulla", रसगुल्ला:"rasgulla", गुलाब:"gulab", जामुन:"jamun"
};
export function normalizeMenuSearch(value:string):string {
 return value.normalize("NFKC").toLowerCase().replace(/[^\p{L}\p{M}\p{N}\s]/gu," ").split(/\s+/).filter(Boolean).map(word=>aliases[word]??word).join(" ");
}
export function matchesMenuSearch(product:{name:string;description?:string|null;categoryName?:string},query:string):boolean {
 const terms=normalizeMenuSearch(query).split(" ").filter(Boolean);
 const text=normalizeMenuSearch([product.name,product.description,product.categoryName].filter(Boolean).join(" "));
 return terms.every(term=>text.includes(term));
}
