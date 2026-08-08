package com.example.reactiveguide.dto;

import com.example.reactiveguide.domain.PaymentStatus;
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
public class PaymentResponse {
    private Long id;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private Instant paymentDate;
    private Instant createdAt;
    private Instant updatedAt;
}
