package com.gokulsweets.restaurant.menu.workspace;

/**
 * Compile-time constants used by the com.gokulsweets.restaurant.menu.workspace package. Existing
 * declarations retain aliases for compatibility.
 */
public final class AppConstant {

    private AppConstant() {}

    /** Original MenuWorkspaceService.BALANCE value; unchanged during extraction. */
    public static final String MENU_WORKSPACE_SERVICE_BALANCE =
            "GREATEST((CASE WHEN pol.ready_stock_required THEN"
                + " LEAST(al.ready_quantity,al.approved_quantity) ELSE al.approved_quantity"
                + " END)-al.safety_buffer_quantity-al.held_quantity-al.committed_quantity-al.wasted_quantity,0)";
}
