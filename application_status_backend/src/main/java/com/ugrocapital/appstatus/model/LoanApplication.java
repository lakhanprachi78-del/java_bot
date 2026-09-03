package com.ugrocapital.appstatus.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Matches the real dmcredit.application table exactly (only the columns
 * this app actually reads — see repository.py's _APPLICATION_COLUMNS
 * comment for why the rest of the real table's ~30 other columns are
 * deliberately left unmapped here). Read-only: this project never writes
 * to this table, so no cascade/persist concerns.
 */
@Entity
@Table(name = "application", schema = "dmcredit")
public class LoanApplication {

    @Id
    @Column(name = "applicationkey")
    private Long applicationKey;

    @Column(name = "applicationid", nullable = false)
    private String applicationId;

    @Column(name = "statuscode", nullable = false)
    private String statusCode;

    @Column(name = "createdby", nullable = false)
    private String createdBy;

    @Column(name = "createddt", nullable = false)
    private LocalDateTime createdDt;

    @Column(name = "lstupdatedby", nullable = false)
    private String lastUpdatedBy;

    @Column(name = "lstupdateddt", nullable = false)
    private LocalDateTime lastUpdatedDt;

    @Column(name = "remarks")
    private String remarks;

    public Long getApplicationKey() {
        return applicationKey;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public String getStatusCode() {
        return statusCode;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public LocalDateTime getCreatedDt() {
        return createdDt;
    }

    public String getLastUpdatedBy() {
        return lastUpdatedBy;
    }

    public LocalDateTime getLastUpdatedDt() {
        return lastUpdatedDt;
    }

    public String getRemarks() {
        return remarks;
    }
}
