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
@Schema(description = "Response payload representing a payment record")
public class PaymentResponse {

    @Schema(description = "The unique identifier of the payment", example = "1")
    private Long id;

    @Schema(description = "The amount of the payment", example = "150.00")
    private BigDecimal amount;

    @Schema(description = "The ISO-4217 three-letter currency code", example = "USD")
    private String currency;

    @Schema(description = "The status of the payment", example = "PENDING")
    private PaymentStatus status;

    @Schema(description = "The date and time when the payment was initiated (ISO-8601 format)", example = "2026-08-08T12:00:00Z")
    private Instant paymentDate;

    @Schema(description = "The system timestamp when the record was created", example = "2026-08-08T12:00:00Z")
    private Instant createdAt;

    @Schema(description = "The system timestamp when the record was last updated", example = "2026-08-08T12:00:00Z")
    private Instant updatedAt;
}
