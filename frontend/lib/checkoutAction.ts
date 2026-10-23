export function checkoutAction({identityResolved,verificationBusy,contact,attempt,branchReviewed,pickupConfirmed,pickupNeedsChoice=false,ready}:{identityResolved:boolean;verificationBusy:boolean;contact:boolean;attempt:boolean;branchReviewed:boolean;pickupConfirmed:boolean;pickupNeedsChoice?:boolean;ready:boolean}):string {
    if(verificationBusy)return "Verifying phone…";
    if(!identityResolved)return "Checking…";
    if(!contact)return "Verify phone to continue";
    if(attempt)return "Retry checkout";
    if(!branchReviewed)return "Confirm pickup branch";
    if(!pickupConfirmed)return pickupNeedsChoice?"Choose pickup time":"Confirm pickup time";
    if(!ready)return "Checking total…";
    return "Pay now";
}
