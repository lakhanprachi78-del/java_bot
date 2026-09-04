package com.ugrocapital.losbot.entity;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "application_applicant", schema = "dmcredit", indexes = {
        @Index(name = "uk_application_applicant_applicantid", columnList = "applicantid", unique = true)
})
public class ApplicationApplicant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "appapplicantkey", nullable = false)
    private Long applicantKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicationkey")
    private LoanApplication application;

    @Column(name = "applicantid", length = 25, unique = true)
    private String applicantId;

    @Column(name = "name", length = 150)
    private String name;

    @Column(name = "mobile", length = 12, nullable = false)
    private String mobile;

    @Column(name = "applicanttype")
    private String applicantType;

    @Column(name = "dob")
    private java.time.LocalDate dateOfBirth;

    @Column(name = "isactive", nullable = false)
    private Boolean active;

    @Column(name = "createdby", length = 80, nullable = false)
    private String createdBy;

    @Column(name = "createddt", nullable = false)
    private java.time.LocalDateTime createdDate;

    @Column(name = "lstupdatedby", length = 80, nullable = false)
    private String lastUpdatedBy;

    @Column(name = "lstupdateddt", nullable = false)
    private java.time.LocalDateTime lastUpdatedDate;

    @OneToMany(mappedBy = "applicant", fetch = FetchType.EAGER)
    private List<ApplicationApplicantEmail> emails = new ArrayList<>();

    protected ApplicationApplicant() {
    }

    public Long getApplicantKey() {
        return applicantKey;
    }

    public LoanApplication getApplication() {
        return application;
    }

    public String getApplicantType() {
        return applicantType;
    }

    public String getApplicantId() {
        return applicantId;
    }

    public String getName() {
        return name;
    }

    public String getMobile() {
        return mobile;
    }

    public java.time.LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public List<ApplicationApplicantEmail> getEmails() {
        return emails;
    }
}