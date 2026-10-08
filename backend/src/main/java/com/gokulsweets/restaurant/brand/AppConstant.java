package com.gokulsweets.restaurant.brand;

/**
 * Compile-time constants used by the com.gokulsweets.restaurant.brand package. Existing
 * declarations retain aliases for compatibility.
 */
public final class AppConstant {

    /** Creates a app constant instance. */
    private AppConstant() {}

    /** Original CareerService.JOBS value; unchanged during extraction. */
    public static final String CAREER_SERVICE_JOBS =
            "SELECT j.*,b.name branch_name FROM career_jobs j JOIN branches b ON b.id=j.branch_id";

    /** Original CareerService.APPLICANTS value; unchanged during extraction. */
    public static final String CAREER_SERVICE_APPLICANTS =
            "SELECT a.*,b.name branch_name,COALESCE(j.title,'General interest') job_title FROM"
                + " career_applications a JOIN branches b ON b.id=a.branch_id LEFT JOIN career_jobs"
                + " j ON j.id=a.job_id";
}
