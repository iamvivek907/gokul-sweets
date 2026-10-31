"use client";
import {useEffect,useState} from 'react';
import {apiClient} from '@/services/apiClient';
import type {MealDefinition} from '@/types/meal';
/** One optional branch read shared by the shortcut and builder. No polling. */
export function useBranchMeals(branchId:number|undefined){
 const [result,setResult]=useState<{branchId:number;meals:MealDefinition[]}|null>(null);
 useEffect(()=>{if(!branchId)return;const c=new AbortController();apiClient<MealDefinition[]>(`/api/branches/${branchId}/meals`,{signal:AbortSignal.any([c.signal,AbortSignal.timeout(10000)])}).then(data=>{if(!c.signal.aborted)setResult({branchId,meals:Array.isArray(data)?data.filter(m=>m.active&&Array.isArray(m.groups)):[]});}).catch(()=>{});return()=>c.abort();},[branchId]);
 return result&&result.branchId===branchId?result.meals:[];
}
