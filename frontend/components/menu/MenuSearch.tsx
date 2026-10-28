"use client";
import {
  useEffect,
  useId,
  useRef,
  useState,
  useSyncExternalStore,
} from "react";
import { T, useTranslation } from "@/lib/language";
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
  enhanced?: boolean;
  refined?: boolean;
  premium?: boolean;
  value: string;
  onChange: (value: string) => void;
  products?: MenuProduct[];
  branchId?: number;
}
export default function MenuSearch({
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
    listId = useId(),
    raw = useSyncExternalStore(subscribe, read, server);
  const [focused, setFocused] = useState(false),
    [active, setActive] = useState(-1);
  const surface = useRef<HTMLDivElement>(null),
    pointerActive = useRef(false);
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
    remember(text);
    setFocused(false);
    setActive(-1);
  }
  useEffect(() => {
    if (
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
  }, [value, branchId, products, enabled]);
  return (
    <div ref={surface} className="menu-search relative w-full">
      {refined && (
        <label
          htmlFor="gokul-menu-search"
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
          id="gokul-menu-search"
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
                setFocused(false);
              }
            }
          }}
          placeholder={t(
            premium
              ? "Search your favourites"
              : "Search sweets, snacks & meals",
          )}
          className="min-h-13 w-full rounded-2xl border border-[#eadfd6] bg-white py-3 pl-12 pr-4 text-sm outline-none shadow-sm transition focus:border-[#c88a20] focus:ring-2 focus:ring-[#f6dfad]"
        />
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
            {options.map((text, index) => (
              <li key={text} role="presentation">
                <button
                  type="button"
                  role="option"
                  id={`${listId}-${index}`}
                  aria-selected={index === activeIndex}
                  tabIndex={-1}
                  onMouseDown={(e) => e.preventDefault()}
                  onClick={() => choose(text)}
                >
                  {text}
                  <span aria-hidden="true">↗</span>
                </button>
              </li>
            ))}
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
                setFocused(false);
              }}
            >
              <T text="Clear recent searches" />
            </button>
          )}
        </div>
      )}
    </div>
  );
}
