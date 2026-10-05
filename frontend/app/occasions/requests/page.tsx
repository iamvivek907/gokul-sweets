"use client";
import {Suspense} from "react";
import OccasionRequests from "@/components/occasion/OccasionRequests";
export default function OccasionRequestsPage(){return <Suspense fallback={<p role="status" className="p-6">Loading requests…</p>}><OccasionRequests/></Suspense>;}
