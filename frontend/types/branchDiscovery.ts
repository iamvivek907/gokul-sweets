export interface BranchOffering {title:string;description:string}
export interface BranchOfferingsSnapshot {version:number;draft:BranchOffering[];published:BranchOffering[]}
export interface BranchDiscovery {
 offerings:BranchOffering[];
 overallExperience:{average:number;count:number};
 topRatedItems:{productId:number;name:string;imageUrl:string|null;categoryId:number;average:number;count:number;review:{comment:string;overallRating:number}|null}[];
}
