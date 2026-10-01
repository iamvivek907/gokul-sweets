"use client";
import {useLinkStatus} from "next/link";
import {translate,useLanguage} from "@/lib/language";
/** Mounted inside Link; Next owns the pending state, including slow navigation. */
export default function LinkFeedback(){useLanguage();const {pending}=useLinkStatus();return pending?<span role="status" className="ml-2 inline-flex items-center gap-1 text-xs"><span aria-hidden="true" className="inline-block h-3 w-3 animate-spin rounded-full border-2 border-current border-r-transparent motion-reduce:animate-none"/>{translate("Opening…")}</span>:null;}
