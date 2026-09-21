package com.mortgage.rate_concession_service.repository;

import com.mortgage.rate_concession_service.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, String> {
}
