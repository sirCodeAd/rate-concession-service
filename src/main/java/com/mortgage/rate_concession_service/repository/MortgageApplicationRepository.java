package com.mortgage.rate_concession_service.repository;

import com.mortgage.rate_concession_service.domain.MortgageApplication;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MortgageApplicationRepository extends JpaRepository<MortgageApplication, String> {
}
