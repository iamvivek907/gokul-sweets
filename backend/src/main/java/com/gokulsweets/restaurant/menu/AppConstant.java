package com.gokulsweets.restaurant.menu;

/**
 * Compile-time constants used by the com.gokulsweets.restaurant.menu package. Existing declarations
 * retain aliases for compatibility.
 */
public final class AppConstant {

    /** Fixed stripes bound coordination memory while allowing unrelated branches to rebuild. */
    public static final int MENU_CACHE_LOCK_STRIPES = 64;

    /** Creates a app constant instance. */
    private AppConstant() {}

    /**
     * Original StorefrontHighlightsController.MAX_CACHED_HIGHLIGHTS value; unchanged during
     * extraction.
     */
    public static final int STOREFRONT_HIGHLIGHTS_CONTROLLER_MAX_CACHED_HIGHLIGHTS = 128;

    /**
     * Original StorefrontHighlightsController.CACHE_TTL_SECONDS value; unchanged during extraction.
     */
    public static final int STOREFRONT_HIGHLIGHTS_CONTROLLER_CACHE_TTL_SECONDS = 30;
}
