package com.example.idempotent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoucherRedeemResponse {
    private String redemptionId;
    private String voucherCode;
    private String userId;
    private BigDecimal discountAmount;
    private String status;
    private Instant redeemedAt;
}
