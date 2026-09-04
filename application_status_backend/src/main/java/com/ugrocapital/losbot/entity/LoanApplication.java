package com.ugrocapital.losbot.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "application", schema = "dmcredit", indexes = {
    @Index(name = "ix_application_applicationid", columnList = "applicationid"),
    @Index(name = "ix_application_prodcatcode", columnList = "prodcatcode"),
    @Index(name = "ix_application_statuscode", columnList = "statuscode"),
    @Index(name = "ix_application_createdby", columnList = "createdby"),
    @Index(name = "ix_application_createddt", columnList = "createddt"),
    @Index(name = "ix_application_lstupdatedby", columnList = "lstupdatedby")
})
public class LoanApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "applicationkey", nullable = false)
    private Long applicationKey;

    @Column(name = "applicationid", length = 25, nullable = false)
    private String applicationId;

    @Column(name = "prodcatcode", length = 20, nullable = false)
    private String productCategoryCode;

    @Column(name = "productcode", length = 20)
    private String productCode;

    @Column(name = "statuscode", length = 25, nullable = false)
    private String statusCode;

    @Column(name = "loanpurposecode", length = 20, nullable = false)
    private String loanPurposeCode;

    @Column(name = "isactive", nullable = false)
    private Boolean active;

    @Column(name = "createdby", length = 80, nullable = false)
    private String createdBy;

    @Column(name = "createddt", nullable = false)
    private LocalDateTime createdDate;

    @Column(name = "lstupdatedby", length = 80, nullable = false)
    private String lastUpdatedBy;

    @Column(name = "lstupdateddt", nullable = false)
    private LocalDateTime lastUpdatedDate;

    @Column(name = "workflowcode", length = 20)
    private String workflowCode;

    @Column(name = "isnotinterested")
    private Boolean notInterested;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "remarks", length = 5000)
    private String remarks;

    @Column(name = "requiredloanamount")
    private Double requiredLoanAmount;

    @Column(name = "isstagereversed")
    private Boolean stageReversed;

    @Column(name = "transactioncode", length = 20)
    private String transactionCode;

    @Column(name = "preferredprogram", length = 40)
    private String preferredProgram;

    @Column(name = "parentapplicationkey")
    private Long parentApplicationKey;

    @Column(name = "journeytype", length = 150)
    private String journeyType;

    @Column(name = "copyprofileinitiated")
    private Boolean copyProfileInitiated;

    @Column(name = "crmcopyprofileinitiated")
    private Boolean crmCopyProfileInitiated;

    @Column(name = "monitoringreportgenerated")
    private Boolean monitoringReportGenerated;

    @Column(name = "leadid", length = 150)
    private String leadId;

    @Column(name = "leaddetailsupdated")
    private Boolean leadDetailsUpdated;

    @Column(name = "ismonitoringreportcompliant", nullable = false)
    private Boolean monitoringReportCompliant;

    @Column(name = "leadsource", length = 150)
    private String leadSource;

    @Column(name = "subproductcatcode", length = 20)
    private String subProductCategoryCode;

    @Column(name = "ispropertyidentified")
    private Boolean propertyIdentified;

    @Column(name = "applicationtype", length = 30)
    private String applicationType;

    @OneToMany(mappedBy = "application", fetch = FetchType.EAGER)
    private List<ApplicationApplicant> applicants = new ArrayList<>();

    protected LoanApplication() {
    }

    public Long getApplicationKey() {
        return applicationKey;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public String getProductCategoryCode() {
        return productCategoryCode;
    }

    public String getProductCode() {
        return productCode;
    }

    public String getStatusCode() {
        return statusCode;
    }

    public String getLoanPurposeCode() {
        return loanPurposeCode;
    }

    public Boolean getActive() {
        return active;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public String getLastUpdatedBy() {
        return lastUpdatedBy;
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public LocalDateTime getLastUpdatedDate() {
        return lastUpdatedDate;
    }

    public String getRemarks() {
        return remarks;
    }

    public List<ApplicationApplicant> getApplicants() {
        return applicants;
    }
}