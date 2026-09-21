package com.mortgage.rate_concession_service.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A mortgage application against which pricing exception requests can be raised. */
@Entity
@Table(name = "mortgage_application")
public class MortgageApplication {

    @Id
    private String id;

    private String applicantName;

    private int standardRateBps;

    protected MortgageApplication() {
        // JPA
    }

    public MortgageApplication(String id, String applicantName, int standardRateBps) {
        this.id = id;
        this.applicantName = applicantName;
        this.standardRateBps = standardRateBps;
    }

    public String getId() {
        return id;
    }

    public String getApplicantName() {
        return applicantName;
    }

    public int getStandardRateBps() {
        return standardRateBps;
    }
}
