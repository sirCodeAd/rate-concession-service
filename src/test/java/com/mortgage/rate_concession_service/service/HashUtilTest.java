package com.mortgage.rate_concession_service.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure unit test for the SHA-256 normalization helper used by the idempotency mechanism. */
class HashUtilTest {

    private final HashUtil hashUtil = new HashUtil();

    @Test
    void sameInput_producesSameHash() {
        String a = hashUtil.sha256Hex("{\"applicationId\":\"app-1001\",\"requestedDiscountBps\":25}");
        String b = hashUtil.sha256Hex("{\"applicationId\":\"app-1001\",\"requestedDiscountBps\":25}");
        assertThat(a).isEqualTo(b);
        assertThat(a).hasSize(64);
    }

    @Test
    void differentInput_producesDifferentHash() {
        String a = hashUtil.sha256Hex("{\"requestedDiscountBps\":25}");
        String b = hashUtil.sha256Hex("{\"requestedDiscountBps\":26}");
        assertThat(a).isNotEqualTo(b);
    }
}
