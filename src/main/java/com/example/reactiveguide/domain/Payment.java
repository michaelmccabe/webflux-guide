package com.example.reactiveguide.domain;

import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Builder(toBuilder = true)
@Table("payments")
public record Payment(
    @Id Long id,
    BigDecimal amount,
    String currency,
    PaymentStatus status,
    Instant paymentDate,
    Instant createdAt,
    Instant updatedAt
) {}
