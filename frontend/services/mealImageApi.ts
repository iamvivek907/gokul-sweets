import {adminFetch} from './adminApi';
export const MEAL_IMAGE_LIMIT=5*1024*1024;
export function validateMealImage(file:File){
 if(!['image/jpeg','image/png','image/webp'].includes(file.type.toLowerCase()))throw new Error('Choose a JPG, PNG or WebP photo.');
 if(!file.size||file.size>MEAL_IMAGE_LIMIT)throw new Error('Choose a non-empty photo up to 5 MB.');
}
export async function uploadMealImage(branchId:number,file:File,signal:AbortSignal):Promise<string>{
 validateMealImage(file);const form=new FormData();form.append('image',file);
 const response=await adminFetch(`/api/admin/branches/${branchId}/meals/image`,'staff-session',{method:'POST',body:form,signal});
 if(!response.ok){let message='Could not upload the meal photo. Your previous photo is unchanged.';try{const body=await response.json();if(typeof body.message==='string')message=body.message;}catch{}throw new Error(message);}
 const result=await response.json();if(typeof result.url!=='string'||!result.url.startsWith('https://'))throw new Error('The upload did not return a valid photo URL. Try again.');return result.url;
}
