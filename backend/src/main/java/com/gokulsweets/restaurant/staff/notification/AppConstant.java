package com.gokulsweets.restaurant.staff.notification;

/**
 * Compile-time constants used by the com.gokulsweets.restaurant.staff.notification package.
 * Existing declarations retain aliases for compatibility.
 */
public final class AppConstant {

    /** Creates a app constant instance. */
    private AppConstant() {}

    /** Original StaffOrderAlerts.ELIGIBLE value; unchanged during extraction. */
    public static final String STAFF_ORDER_ALERTS_ELIGIBLE =
            """
u.active AND (EXISTS (SELECT 1 FROM roles r WHERE r.id = u.role_id AND r.name = 'OWNER_ADMIN')
  OR EXISTS (SELECT 1 FROM staff_branch_access b WHERE b.staff_user_id = u.id AND b.branch_id = e.branch_id))
AND EXISTS (SELECT 1 FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
  WHERE rp.role_id = u.role_id AND p.name = 'ORDER_VIEW')
AND EXISTS (SELECT 1 FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
  WHERE rp.role_id = u.role_id AND p.name = e.required_permission)
""";
}
