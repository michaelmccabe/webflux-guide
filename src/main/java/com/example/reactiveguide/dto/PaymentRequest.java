package com.example.reactiveguide.dto;

import com.example.reactiveguide.domain.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request payload for creating or updating a payment")
public class PaymentRequest {

    @Schema(description = "The amount of the payment", example = "150.00", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal amount;

    @Schema(description = "The ISO-4217 three-letter currency code", example = "USD", requiredMode = Schema.RequiredMode.REQUIRED)
    private String currency;

    @Schema(description = "The current status of the payment", example = "PENDING", requiredMode = Schema.RequiredMode.REQUIRED)
    private PaymentStatus status;

    @Schema(description = "The date and time when the payment was initiated (ISO-8601 format)", example = "2026-08-08T12:00:00Z")
    private Instant paymentDate;
}
