export type PersonSection="FOUNDER"|"TEAM"|"DEVELOPER";
export interface BrandStory {title:string;subtitle:string;storyTitle:string;storyBody:string;imageUrl:string|null;published:boolean;version:number}
export interface BrandPerson {id:number;section:PersonSection;name:string;role:string;bio:string;photoUrl:string|null;displayOrder:number;published:boolean;version:number}
export interface BrandContent {story:BrandStory|null;people:BrandPerson[]}
export interface CareerJob {id:number;branchId:number;branchName:string;title:string;department:string;requirements:string;minimumExperience:number;active:boolean;version:number}
export type ApplicantStatus="NEW"|"REVIEWING"|"SHORTLISTED"|"INTERVIEW"|"HIRED"|"REJECTED";
export interface Applicant {id:string;jobId:number|null;branchId:number;branchName:string;jobTitle:string;name:string;phone:string;email:string|null;desiredRole:string;experience:number;qualifications:string;status:ApplicantStatus;staffNotes:string;createdAt:string;version:number}
export interface ApplicantPage {items:Applicant[];total:number;page:number;size:number;counts:Partial<Record<ApplicantStatus,number>>}
