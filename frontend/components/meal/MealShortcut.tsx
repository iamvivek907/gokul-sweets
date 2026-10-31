"use client";
import {useLanguage} from '@/lib/language';
import styles from './MealShortcut.module.css';
export default function MealShortcut({onClick}:{onClick:()=>void}){
 const hi=useLanguage()==='hi';
 return <button type="button" className={styles.shortcut} aria-label={hi?'अपना मील बनाएं':'Build your meal'} onClick={onClick}>
 <span className={styles.icon} aria-hidden="true"><svg viewBox="0 0 44 44" width="36" height="36" fill="none"><rect x="4" y="18" width="36" height="22" rx="7" fill="#147c61"/><rect x="7" y="21" width="30" height="16" rx="4" fill="#fff8d8"/><path d="M10 17a9 9 0 0 1 18 0H10Z" fill="#f2ae36"/><path d="M10 18h18v4H10z" fill="#a94036"/><path d="m10 17 4-2 5 2 5-2 4 2" stroke="#3c8b48" strokeWidth="3"/><circle cx="30" cy="29" r="5" fill="#ef8d41"/><circle cx="15" cy="29" r="5" fill="#f4cd5e"/><path d="m32 17 3-9h5l-2 10" fill="#ca709a"/><path d="m35 8 1-4h5" stroke="#7a2947" strokeWidth="2" strokeLinecap="round"/><path d="m7 6 1.5 3L12 10l-3.5 1L7 14l-1-3-3-1 3-1L7 6Z" fill="#e8b22b"/></svg></span>
 <span className={styles.copy}><strong>{hi?'मील बनाएं':'Build a meal'}</strong><span className={styles.switcher} aria-hidden="true"><span>{hi?'अपनी पसंद का कॉम्बो':'Mix. Match. Enjoy.'}</span><span>{hi?'केवल ऐप पर':'Only on the app'}</span></span></span>
 </button>;
}
