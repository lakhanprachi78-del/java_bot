package com.ugrocapital.appstatus.model;

import jakarta.persistence.*;

/**
 * Matches dmcredit.application_applicant_email. appapplicantkey is both
 * this table's primary key AND its foreign key back to
 * application_applicant (a true 1:1, though the Python ORM models it as a
 * one-item list — see ApplicationApplicant.emails in models.py).
 */
@Entity
@Table(name = "application_applicant_email", schema = "dmcredit")
public class ApplicationApplicantEmail {

    @Id
    @Column(name = "appapplicantkey")
    private Long appApplicantKey;

    @Column(name = "emailaddr")
    private String emailAddr;

    public Long getAppApplicantKey() {
        return appApplicantKey;
    }

    public String getEmailAddr() {
        return emailAddr;
    }
}
