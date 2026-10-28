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
 return terms.every(term=>text.includes(term)||text.split(" ").some(word=>nearWord(term,word)));
}

/** Bounded Damerau edit distance; short queries remain exact and long input stays cheap. */
export function nearWord(a:string,b:string):boolean{
 if(a.length<4||a.length>40||b.length>40)return false;
 const budget=a.length>=8?2:1;if(Math.abs(a.length-b.length)>budget)return false;
 const rows=Array.from({length:a.length+1},()=>Array<number>(b.length+1).fill(0));
 for(let i=0;i<=a.length;i++)rows[i][0]=i;for(let j=0;j<=b.length;j++)rows[0][j]=j;
 for(let i=1;i<=a.length;i++)for(let j=1;j<=b.length;j++){rows[i][j]=Math.min(rows[i-1][j]+1,rows[i][j-1]+1,rows[i-1][j-1]+(a[i-1]===b[j-1]?0:1));if(i>1&&j>1&&a[i-1]===b[j-2]&&a[i-2]===b[j-1])rows[i][j]=Math.min(rows[i][j],rows[i-2][j-2]+1);}
 return rows[a.length][b.length]<=budget;
}
export function menuSearchSuggestions(products:{name:string;description?:string|null;categoryName?:string}[],query:string):string[]{
 const text=normalizeMenuSearch(query);if(text.length<2||text.length>60)return [];
 const score=(name:string)=>{const n=normalizeMenuSearch(name);return n===text?0:n.startsWith(text)?1:n.includes(text)?2:3;};
 return [...new Set(products.filter(p=>matchesMenuSearch(p,query)).map(p=>p.name))].sort((a,b)=>score(a)-score(b)||a.localeCompare(b)).slice(0,4);
}
