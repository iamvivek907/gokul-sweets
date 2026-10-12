"use client";
import {usePwaInstall} from "@/hooks/usePwaInstall";
/** Capture the browser event even before Home/Profile or a banner mounts. */
export default function PwaInstallRuntime() {usePwaInstall(); return null;}
