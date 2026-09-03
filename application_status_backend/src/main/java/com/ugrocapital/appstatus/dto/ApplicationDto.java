package com.ugrocapital.appstatus.dto;

/**
 * The exact field set repository.py's _serialize() returns as a dict.
 * Note "created_by" is intentionally carried here but NOT shown by
 * FormattingService — that mirrors formatting.py's _FIELD_ORDER, which
 * omits it too (only last_updated_by is ever displayed). Don't "fix"
 * that; it's how the original behaves.
 */
public class ApplicationDto {
    public String applicationId;
    public String applicantName;      // comma-joined APPLICANT-type names, or null
    public String coApplicantName;    // comma-joined COAPPLICANT-type names, or null
    public String applicantEmail;     // comma-joined emails for APPLICANT-type applicants, or null
    public String status;
    public String createdBy;
    public String createdAt;          // pre-formatted "dd MMM yyyy, HH:mm:ss", or null
    public String lastUpdatedBy;
    public String lastUpdatedAt;      // pre-formatted "dd MMM yyyy, HH:mm:ss", or null
    public String remark;             // already redacted + length-capped
}
