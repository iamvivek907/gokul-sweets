export const REQUEST_MENU_PICKUP="gokul-request-menu-pickup";
/** Close the item dialog first, then open the existing explicit pickup chooser. */
export function requestMenuPickup(productId:number,close?:()=>void) {
 close?.();
 requestAnimationFrame(()=>requestAnimationFrame(()=>window.dispatchEvent(new CustomEvent(REQUEST_MENU_PICKUP,{detail:{productId}}))));
}
