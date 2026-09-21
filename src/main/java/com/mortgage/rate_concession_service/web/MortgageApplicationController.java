package com.mortgage.rate_concession_service.web;

import com.mortgage.rate_concession_service.domain.AppUser;
import com.mortgage.rate_concession_service.security.CurrentUser;
import com.mortgage.rate_concession_service.service.PricingExceptionRequestService;
import com.mortgage.rate_concession_service.web.dto.ApprovedDiscountResponseDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/applications")
public class MortgageApplicationController {

    private final PricingExceptionRequestService service;

    public MortgageApplicationController(PricingExceptionRequestService service) {
        this.service = service;
    }

    @GetMapping("/{applicationId}/approved-discount")
    public ApprovedDiscountResponseDto approvedDiscount(
            @PathVariable String applicationId, @CurrentUser AppUser currentUser) {
        return service.approvedDiscount(applicationId);
    }
}
