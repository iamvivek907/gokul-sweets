"use client";
import {useEffect} from "react";
import {apiClient} from "@/services/apiClient";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";

/** A successfully loaded order acknowledges its existing updates, including an OS-notification deep link. */
export default function NotificationReadOnOpen({orderNumber,targetType="ORDER"}: {orderNumber: string;targetType?:"ORDER"|"OCCASION"}) {
    const features=useStorefrontFeatures();
    useEffect(()=>{
        if(!features?.notificationInbox)return;
        const controller=new AbortController();
        void apiClient<void>("/api/customer/identity/notifications/read-target",{
            method:"PUT",credentials:"include",signal:controller.signal,
            body:JSON.stringify({targetType,targetId:orderNumber})
        }).then(()=>{if(!controller.signal.aborted)window.dispatchEvent(new Event("gokul-inbox-changed"));})
            .catch(()=>{/* Order details remain usable for guests/offline; never claim acknowledgement succeeded. */});
        return()=>controller.abort();
    },[features?.notificationInbox,orderNumber,targetType]);
    return null;
}
