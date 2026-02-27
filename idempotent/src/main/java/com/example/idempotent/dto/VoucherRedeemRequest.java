package com.example.idempotent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VoucherRedeemRequest {
    @NotBlank(message = "Voucher code is required")
    private String voucherCode;

    @NotBlank(message = "User id is required")
    private String userId;
}
