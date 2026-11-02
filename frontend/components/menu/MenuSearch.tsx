"use client";
import {
  useEffect,
  useId,
  useRef,
  useState,
  useSyncExternalStore,
} from "react";
import Image from "next/image";
import MenuDiscoverySheet from "./MenuDiscoverySheet";
import { T, useLanguage, useTranslation } from "@/lib/language";
import { menuSearchSuggestions, matchesMenuSearch } from "@/lib/menuSearch";
import {
  MENU_PREFERENCE_EVENT,
  recentSearchKey,
  recentSearches,
  rememberMenuSearch,
  clearRecentSearches,
} from "@/lib/menuPreferences";
import type { MenuProduct } from "@/types/menu";
const read = () => {
  try {
    return localStorage.getItem(recentSearchKey) ?? "";
  } catch {
    return "";
  }
};
const server = () => "";
const subscribe = (changed: () => void) => {
  window.addEventListener("storage", changed);
  window.addEventListener(MENU_PREFERENCE_EVENT, changed);
  return () => {
    window.removeEventListener("storage", changed);
    window.removeEventListener(MENU_PREFERENCE_EVENT, changed);
  };
};
interface MenuSearchProps {
  detached?: boolean;
  onSubmit?: (value:string)=>void;
  onDismiss?: ()=>void;
  enhanced?: boolean;
  refined?: boolean;
  premium?: boolean;
  value: string;
  onChange: (value: string) => void;
  products?: MenuProduct[];
  branchId?: number;
}
export default function MenuSearch({
  detached = false,
  onSubmit,
  onDismiss,
  enhanced = false,
  refined = false,
  premium = false,
  value,
  onChange,
  products = [],
  branchId = 0,
}: MenuSearchProps) {
  const enabled = enhanced && refined,
    t = useTranslation(),
    locale = useLanguage(),
    listId = useId(),
    raw = useSyncExternalStore(subscribe, read, server);
  const [focused, setFocused] = useState(false),
    [active, setActive] = useState(-1);
  const [overlay,setOverlay]=useState(false),[draft,setDraft]=useState(value);
  const [searchTrigger,setSearchTrigger]=useState<HTMLElement|null>(null);
  const inputId=detached ? `menu-search-${listId}` : "gokul-menu-search";
  useEffect(()=>{
    if(detached){const frame=requestAnimationFrame(()=>document.getElementById(inputId)?.focus({preventScroll:true}));return()=>cancelAnimationFrame(frame);}
    if(!premium)return;
    const open=(event:Event)=>{setSearchTrigger((event as CustomEvent<HTMLElement>).detail??document.activeElement as HTMLElement|null);setDraft(value);setOverlay(true);};
    window.addEventListener('gokul-menu-search-open',open);
    return()=>window.removeEventListener('gokul-menu-search-open',open);
  },[detached,premium,value,inputId]);
  const surface = useRef<HTMLDivElement>(null),
    pointerActive = useRef(false);
  const [hintIndex,setHintIndex]=useState(0);
  // Use this branch's real catalogue; no extra requests or invented dishes.
  const examples:string[]=[];
  const categories=new Set<number>();
  for(const product of products){
    if(!categories.has(product.categoryId)&&product.name.trim()){
      examples.push(product.name);categories.add(product.categoryId);
      if(examples.length===4)break;
    }
  }
  if(examples.length<4)for(const product of products){
    if(product.name.trim()&&!examples.includes(product.name))examples.push(product.name);
    if(examples.length===4)break;
  }
  const exampleKey=JSON.stringify(examples),hint=examples[hintIndex%Math.max(1,examples.length)];
  useEffect(()=>{
    const count=JSON.parse(exampleKey).length;
    if(!premium||overlay||focused||value||count<2)return;
    const motion=window.matchMedia('(prefers-reduced-motion: reduce)');
    let visible=true,timer:ReturnType<typeof setInterval>|undefined;
    const sync=()=>{
      if(timer)clearInterval(timer);timer=undefined;
      if(visible&&document.visibilityState==='visible'&&!motion.matches)
        timer=setInterval(()=>setHintIndex(index=>(index+1)%count),4000);
    };
    const observer=new IntersectionObserver(entries=>{visible=entries[0]?.isIntersecting??false;sync();});
    if(surface.current)observer.observe(surface.current);
    motion.addEventListener('change',sync);document.addEventListener('visibilitychange',sync);sync();
    return()=>{if(timer)clearInterval(timer);observer.disconnect();motion.removeEventListener('change',sync);document.removeEventListener('visibilitychange',sync);};
  },[premium,overlay,focused,value,exampleKey]);
  function clearSearch(){
    onChange('');setActive(-1);setFocused(true);
    surface.current?.querySelector('input')?.focus({preventScroll:true});
  }
  function highlight(text:string){
    const query=value.trim(),at=query?text.toLocaleLowerCase().indexOf(query.toLocaleLowerCase()):-1;
    return at<0?text:<>{text.slice(0,at)}<mark>{text.slice(at,at+query.length)}</mark>{text.slice(at+query.length)}</>;
  }
  useEffect(() => {
    // Keep the in-flow suggestion height until the tap has identified its click target.
    // Keyboard focus leaving the search closes immediately through onBlur.
    const down = () => {
      pointerActive.current = true;
    };
    const click = (event: MouseEvent) => {
      pointerActive.current = false;
      if (!surface.current?.contains(event.target as Node)) setFocused(false);
    };
    const cancel = () => {
      pointerActive.current = false;
      if (!surface.current?.contains(document.activeElement)) setFocused(false);
    };
    document.addEventListener("pointerdown", down, true);
    document.addEventListener("click", click, true);
    document.addEventListener("pointercancel", cancel, true);
    return () => {
      document.removeEventListener("pointerdown", down, true);
      document.removeEventListener("click", click, true);
      document.removeEventListener("pointercancel", cancel, true);
    };
  }, []);
  function submitOverlay(text:string){
    onChange(text);
    setOverlay(false);
    // Opening/cancelling preserves the position; explicit submission reveals the results.
    requestAnimationFrame(()=>document.getElementById("gokul-menu-items")?.scrollIntoView({block:"start",behavior:"instant"}));
  }
  const history = recentSearches(raw, branchId),
    recent = !value.trim() && history.length > 0;
  const options =
    enabled && focused
      ? recent
        ? history.slice(0, 4)
        : menuSearchSuggestions(products, value)
      : [];
  const expanded = options.length > 0,
    activeIndex = Math.min(active, options.length - 1);
  function remember(text: string) {
    try {
      if (rememberMenuSearch(localStorage, branchId, text))
        window.dispatchEvent(new Event(MENU_PREFERENCE_EVENT));
    } catch {}
  }
  function choose(text: string) {
    onChange(text);
    onSubmit?.(text);
    remember(text);
    setFocused(false);
    setActive(-1);
  }
  useEffect(() => {
    if (
      detached ||
      !enabled ||
      !branchId ||
      value.trim().length < 2 ||
      !products.some((product) => matchesMenuSearch(product, value))
    )
      return;
    const timer = setTimeout(() => {
      try {
        if (rememberMenuSearch(localStorage, branchId, value))
          window.dispatchEvent(new Event(MENU_PREFERENCE_EVENT));
      } catch {}
    }, 800);
    return () => clearTimeout(timer);
  }, [value, branchId, products, enabled, detached]);
  return (
    <>
    <div ref={surface} className={`menu-search relative w-full ${premium?"menu-search-premium":""}`}>
      {refined && (
        <label
          htmlFor={inputId}
          className="mb-2 block text-sm font-semibold text-[#241715]"
        >
          <T text="Find a favourite" />
        </label>
      )}
      <div className="relative menu-search-input">
        <span className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-lg text-[#756763]">
          <span className="menu-search-legacy-icon" aria-hidden="true">
            ⌕
          </span>
          <svg
            className="menu-search-modern-icon"
            aria-hidden="true"
            viewBox="0 0 24 24"
            width="22"
            height="22"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
          >
            <circle cx="10.5" cy="10.5" r="7.5" />
            <path d="m16 16 5 5" />
          </svg>
        </span>
        <input
          id={inputId}
          aria-label={refined ? undefined : t("Search the menu")}
          type="search"
          role={enabled ? "combobox" : undefined}
          aria-autocomplete={enabled ? "list" : undefined}
          aria-expanded={enabled ? expanded : undefined}
          aria-controls={expanded ? listId : undefined}
          aria-activedescendant={
            expanded && activeIndex >= 0
              ? `${listId}-${activeIndex}`
              : undefined
          }
          aria-describedby={premium?`${inputId}-hint`:undefined}
          autoComplete="off"
          maxLength={60}
          value={value}
          onFocus={() => setFocused(true)}
          onBlur={(event) => {
            if (
              !pointerActive.current &&
              !event.currentTarget
                .closest(".menu-search")
                ?.contains(event.relatedTarget)
            )
              setFocused(false);
          }}
          onChange={(e) => {
            onChange(e.target.value);
            setActive(-1);
            setFocused(true);
          }}
          onKeyDown={(event) => {
            if (event.key === "Escape") {
              if(onDismiss){event.preventDefault();event.stopPropagation();onDismiss();}
              setFocused(false);
              setActive(-1);
            } else if (
              expanded &&
              (event.key === "ArrowDown" || event.key === "ArrowUp")
            ) {
              event.preventDefault();
              setActive((index) =>
                index < 0
                  ? event.key === "ArrowDown"
                    ? 0
                    : options.length - 1
                  : (index +
                      (event.key === "ArrowDown" ? 1 : -1) +
                      options.length) %
                    options.length,
              );
            } else if (event.key === "Enter") {
              event.preventDefault();
              if (expanded && activeIndex >= 0) choose(options[activeIndex]);
              else {
                remember(value);
                onSubmit?.(value);
                setFocused(false);
              }
            }
          }}
          placeholder={premium&&hint?"":t(
            premium
              ? "Search your favourites"
              : "Search sweets, snacks & meals",
          )}
          className="min-h-13 w-full rounded-2xl border border-[#eadfd6] bg-white py-3 pl-12 pr-4 text-sm outline-none shadow-sm transition focus:border-[#c88a20] focus:ring-2 focus:ring-[#f6dfad]"
        />
        {premium&&<span id={`${inputId}-hint`} className="sr-only">{locale==='hi'?'नाम या श्रेणी से व्यंजन खोजें।':'Search dishes by name or category.'}</span>}
        {premium&&!value&&hint&&<span className="menu-search-example" aria-hidden="true"><span>{locale==='hi'?'खोजें':'Search'}</span> <span key={hint} className="menu-search-example-word">“{hint}”</span></span>}
        {premium&&value&&<button type="button" className="menu-search-clear" aria-label={locale==='hi'?'खोज साफ़ करें':'Clear search'} onMouseDown={event=>event.preventDefault()} onClick={clearSearch}>
          <svg aria-hidden="true" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.8"><path d="m6 6 12 12M18 6 6 18"/></svg>
        </button>}
      </div>
      {expanded && (
        <div className="menu-search-suggestions">
          <p>
            <T text={recent ? "Recent searches" : "Menu suggestions"} />
          </p>
          <ul
            id={listId}
            role="listbox"
            aria-label={t(recent ? "Recent searches" : "Menu suggestions")}
          >
            {options.map((text, index) => {
              const product=products.find(product=>product.name===text);
              return <li key={text} role="presentation">
                <button
                  type="button"
                  role="option"
                  aria-label={premium?text:undefined}
                  id={`${listId}-${index}`}
                  aria-selected={index === activeIndex}
                  tabIndex={-1}
                  onMouseDown={(e) => e.preventDefault()}
                  onClick={() => choose(text)}
                >
                  {premium?<>
                    <span className="menu-search-result-icon" aria-hidden="true">
                      {!recent&&product?.imageUrl?<Image src={product.imageUrl} alt="" width={40} height={40} sizes="40px"/>:<svg viewBox="0 0 24 24" width="19" height="19" fill="none" stroke="currentColor" strokeWidth="1.7">{recent?<><circle cx="12" cy="12" r="8"/><path d="M12 7v5l3 2"/></>:<><circle cx="10" cy="10" r="6"/><path d="m15 15 5 5"/></>}</svg>}
                    </span>
                    <span className="menu-search-result-copy"><strong>{highlight(text)}</strong>{!recent&&product?.categoryName&&<small>{product.categoryName}</small>}</span>
                    <svg className="menu-search-result-arrow" aria-hidden="true" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.6"><path d="M7 17 17 7M7 7h10v10"/></svg>
                  </>:<>{text}<span aria-hidden="true">↗</span></>}
                </button>
              </li>;
            })}
          </ul>
          {recent && (
            <button
              type="button"
              className="menu-clear-search-history"
              onMouseDown={(e) => e.preventDefault()}
              onClick={() => {
                try {
                  if (clearRecentSearches(localStorage, branchId))
                    window.dispatchEvent(new Event(MENU_PREFERENCE_EVENT));
                } catch {}
                surface.current
                  ?.querySelector("input")
                  ?.focus({ preventScroll: true });
                setFocused(false);
              }}
            >
              <T text="Clear recent searches" />
            </button>
          )}
        </div>
      )}
    </div>
    {overlay&&<MenuDiscoverySheet returnFocusId="gokul-menu-header-search" restoreFocus={searchTrigger} title="Search the menu" className="menu-search-dialog" onClose={()=>setOverlay(false)}>
      <p className="menu-search-hint"><T text="Find a dish without losing your place."/></p>
      <MenuSearch detached enhanced={enhanced} refined={refined} premium={premium} value={draft} onChange={setDraft} products={products} branchId={branchId} onSubmit={submitOverlay} onDismiss={()=>setOverlay(false)}/>
      <button type="button" className="menu-search-show-results" onClick={()=>submitOverlay(draft)}><T text="Show results"/></button>
    </MenuDiscoverySheet>}
    </>
  );
}
