package com.ugrocapital.losbot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "application_applicant_email", schema = "dmcredit")
public class ApplicationApplicantEmail {

    @Id
    @Column(name = "appapplicantkey", nullable = false)
    private Long applicantKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appapplicantkey", nullable = false, insertable = false, updatable = false)
    private ApplicationApplicant applicant;

    @Column(name = "emailaddr", length = 150)
    private String emailAddress;

    protected ApplicationApplicantEmail() {
    }

    public Long getApplicantKey() {
        return applicantKey;
    }

    public ApplicationApplicant getApplicant() {
        return applicant;
    }

    public String getEmailAddress() {
        return emailAddress;
    }
}