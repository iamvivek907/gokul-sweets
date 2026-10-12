"use client";
import BrandLoading from "@/components/common/BrandLoading";
import {Suspense} from "react";
import OccasionRequests from "@/components/occasion/OccasionRequests";
export default function OccasionRequestsPage(){return <Suspense fallback={<BrandLoading label="Loading requests…" />}><OccasionRequests/></Suspense>;}
