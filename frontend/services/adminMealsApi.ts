import {adminFetch} from './adminApi';
import type {MealDefinition} from '@/types/meal';

/** All meal mutations require the CSRF token held by the verified staff session. */
export async function saveAdminMeal(branchId:number,meal:MealDefinition,signal:AbortSignal):Promise<MealDefinition>{
 const response=await adminFetch(`/api/admin/branches/${branchId}/meals${meal.id?`/${meal.id}`:''}`,'staff-session',{
  method:meal.id?'PUT':'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(meal),signal
 });
 if(!response.ok){let message='Could not save this meal. Your draft is unchanged.';try{const body=await response.json();if(typeof body.message==='string')message=body.message;}catch{}throw new Error(message);}
 return response.json();
}
