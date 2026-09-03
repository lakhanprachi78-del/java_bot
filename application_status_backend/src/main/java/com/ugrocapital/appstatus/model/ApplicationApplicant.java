package com.ugrocapital.appstatus.model;

import jakarta.persistence.*;

/**
 * Matches dmcredit.application_applicant — only the columns this app
 * reads (applicationkey as the join key back to LoanApplication, name,
 * applicanttype), same trimmed-column approach as LoanApplication.
 * applicanttype is "APPLICANT", "COAPPLICANT", or "GUARANTOR".
 */
@Entity
@Table(name = "application_applicant", schema = "dmcredit")
public class ApplicationApplicant {

    @Id
    @Column(name = "appapplicantkey")
    private Long appApplicantKey;

    @Column(name = "applicationkey")
    private Long applicationKey;

    @Column(name = "name")
    private String name;

    @Column(name = "applicanttype")
    private String applicantType;

    public Long getAppApplicantKey() {
        return appApplicantKey;
    }

    public Long getApplicationKey() {
        return applicationKey;
    }

    public String getName() {
        return name;
    }

    public String getApplicantType() {
        return applicantType;
    }
}
