"use client";
import {T} from "@/lib/language";

import Image from "next/image";
import Link from "next/link";
import {useEffect,useState} from "react";
import {apiClient} from "@/services/apiClient";
import type {BrandContent} from "@/types/brandCareers";
import styles from "./BrandAbout.module.css";
const sections=[{key:"FOUNDER",eyebrow:"Where it began",title:"Meet our founders",copy:"The people behind our story."},{key:"TEAM",eyebrow:"Made together",title:"Our people",copy:"The coworkers who make every day at Gokul possible."},{key:"DEVELOPER",eyebrow:"Built with care",title:"Behind the digital experience",copy:"Meet the people bringing Gokul to your screen."}];
export default function BrandAbout(){
 const [content,setContent]=useState<BrandContent|null>(null),[error,setError]=useState(""),[revision,setRevision]=useState(0);
 useEffect(()=>{const controller=new AbortController();apiClient<BrandContent>("/api/storefront/about",{signal:controller.signal}).then(setContent).catch(e=>{if(!controller.signal.aborted)setError(e.message);});return()=>controller.abort();},[revision]);
 const story=content?.story;
 return <div className={styles.brand}>
 <section className={styles.hero}><div className={styles.heroCopy}><p className={styles.eyebrow}>The Gokul story</p><h1>{story?.title??"Made for moments worth sharing."}</h1><p className={styles.intro}>{story?.subtitle??"Discover the people and care behind Gokul Sweets."}</p><div className={styles.actions}><Link href="/menu" className={styles.primary}>Explore our menu <span aria-hidden="true">↗</span></Link><a href="#our-branches" className={styles.secondary}>Visit a branch</a></div><a href="#our-people" className={styles.explore}>Our story · Our people · Your next opportunity ↓</a></div>
 <div className={styles.heroArt}>{story?.imageUrl?<Image unoptimized fill sizes="(max-width:768px) 100vw, 45vw" src={story.imageUrl} alt="Gokul Sweets" className={styles.cover}/>:<div className={styles.monogram} aria-hidden="true"><span>G</span><p>GOKUL SWEETS</p><small>For everyday joy.<br/>For every celebration.</small></div>}</div></section>
 {error&&<p role="status" className={styles.error}>Our story details couldn’t load. <button type="button" onClick={()=>{setError("");setRevision(v=>v+1);}}><T text="Try again" /></button></p>}
 <section className={styles.story} id="our-people"><div><p className={styles.eyebrow}>From our kitchen to your moments</p><h2>{story?.storyTitle??"Our story"}</h2></div><p>{story?.storyBody??"We prepare sweets, savouries and food for moments shared with family and friends. Explore the menu and collect your favourites from your chosen branch."}</p></section>
 {sections.map(section=>{const people=content?.people.filter(p=>p.section===section.key)??[];return people.length?<section className={styles.peopleSection} key={section.key} aria-label={section.title}><div className={styles.sectionHeading}><div><p className={styles.eyebrow}>{section.eyebrow}</p><h2>{section.title}</h2></div><p>{section.copy}</p></div><div className={styles.people}>{people.map(person=><article className={styles.person} key={person.id}><div className={styles.portrait}>{person.photoUrl?<Image unoptimized fill sizes="(max-width:768px) 100vw, 33vw" src={person.photoUrl} alt={person.name}/>:<span aria-hidden="true">{person.name.split(" ").slice(0,2).map(n=>n[0]).join("")}</span>}</div><div className={styles.personCopy}><p className={styles.role}>{person.role}</p><h3>{person.name}</h3><p>{person.bio}</p></div></article>)}</div></section>:null;})}
 <section className={styles.careers}><div><p className={styles.eyebrow}>Be part of us</p><h2>Bring your talent.<br/>Help make someone’s day.</h2><p>Explore opportunities at our branches, or tell us how you’d like to contribute.</p></div><Link href="/careers" className={styles.primary}>Explore opportunities <span aria-hidden="true">↗</span></Link></section>
 </div>;
}
