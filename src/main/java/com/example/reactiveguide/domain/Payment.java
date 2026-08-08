package com.example.reactiveguide.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table("payments")
public class Payment {
    @Id
    private Long id;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private Instant paymentDate;
    private Instant createdAt;
    private Instant updatedAt;
}
