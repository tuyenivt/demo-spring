package com.example.idempotent.controller;

import com.example.idempotent.dto.VoucherRedeemRequest;
import com.example.idempotent.dto.VoucherRedeemResponse;
import com.example.idempotent.idempotent.Idempotent;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/demo/vouchers")
public class VoucherDemoController {

    /**
     * Business-meaningful idempotent key example:
     * Idempotent-Key: {voucherCode}_{userId}
     */
    @PostMapping("/redeem")
    @Idempotent
    public ResponseEntity<VoucherRedeemResponse> redeemVoucher(
            @RequestHeader("Idempotent-Key") String idempotentKey,
            @Valid @RequestBody VoucherRedeemRequest request
    ) {
        log.info("Redeeming voucher with key={}, voucherCode={}, userId={}",
                idempotentKey, request.getVoucherCode(), request.getUserId());

        var response = VoucherRedeemResponse.builder()
                .redemptionId(UUID.randomUUID().toString())
                .voucherCode(request.getVoucherCode())
                .userId(request.getUserId())
                .discountAmount(new BigDecimal("10.00"))
                .status("REDEEMED")
                .redeemedAt(Instant.now())
                .build();

        log.info("Voucher redeemed successfully: redemptionId={}", response.getRedemptionId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
