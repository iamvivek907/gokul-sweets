"use client";
import DishFacts from "./DishFacts";
import {useMenuExperience} from "./MenuExperience";
import {dishPhotoPosition} from "@/types/menuDetails";
import Image from "next/image";
import {useState} from "react";
import {T} from "@/lib/language";
import {comparisonPrice, menuMoney, menuDescription} from "@/lib/menuPresentationDetails";
import type {MenuProduct} from "@/types/menu";
import MenuDiscoverySheet from "./MenuDiscoverySheet";
import DietaryLabel from "./DietaryLabel";
import FoodPlaceholder from "./FoodPlaceholder";

export default function ProductDetailsSheet({product,onClose}:{product:MenuProduct;onClose:()=>void}) {
    const [failed,setFailed]=useState(false);
    const {details}=useMenuExperience();
    const previous=comparisonPrice(product);
    return <MenuDiscoverySheet title={product.name} className="menu-product-details" onClose={onClose}>
        <div className="menu-details-photo">{product.imageUrl&&!failed?<Image style={{objectPosition:dishPhotoPosition(details[product.id])}} src={product.imageUrl} alt={product.name} fill sizes="(max-width:640px) 90vw, 480px" className="object-cover" onError={()=>setFailed(true)}/>:<FoodPlaceholder/>}</div>
        <DietaryLabel vegetarian={product.vegetarian}/>
        <p className="menu-details-category">{product.categoryName}</p>
        <p>{menuDescription(product)||<T text="Ask the branch about this dish's ingredients and preparation."/>}</p>
        <DishFacts productId={product.id}/>
        <dl className="menu-details-facts"><div><dt><T text="Selling price"/></dt><dd>{previous!==null&&<><span className="sr-only"><T text="Regular price"/> </span><s className="menu-comparison-price">{menuMoney(previous)}</s><span className="sr-only"> <T text="Selling price"/> </span></>}<strong className={previous!==null?"menu-sale-price":undefined}>{menuMoney(product.price)}</strong> <T text={product.saleMode==="WEIGHT"?"/kg":"per item"}/></dd></div>
        {product.saleMode==="WEIGHT"&&<div><dt><T text="Minimum weight"/></dt><dd>{product.minimumWeightGrams??250} g · {menuMoney(product.price*(product.minimumWeightGrams??250)/1000)}</dd></div>}</dl>
        <p className="menu-details-help"><T text="Dietary needs or allergies? Check with the branch before ordering. Pickup and the final total are confirmed before payment."/></p>
    </MenuDiscoverySheet>;
}
