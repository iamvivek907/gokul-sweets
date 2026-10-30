"use client";
import {cloneElement,useId,type ReactElement} from 'react';
import styles from './AdminMeals.module.css';
/** Keep visible guidance separate from the accessible field name. */
export default function MealField({label,help,children}:{label:string;help:string;children:ReactElement<{id?:string;'aria-describedby'?:string}>}){
 const id=useId(),helpId=`${id}-help`;
 return <div className={styles.field}><label htmlFor={id}>{label}</label>{cloneElement(children,{id,'aria-describedby':helpId})}<p id={helpId} className={styles.fieldHelp}>{help}</p></div>;
}
