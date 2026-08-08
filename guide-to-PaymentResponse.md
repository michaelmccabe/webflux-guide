# Guide to PaymentResponse DTO

The `PaymentResponse` class is a Data Transfer Object (DTO) used to represent the output format of a payment in API responses.

## Definition
Located at: [PaymentResponse.java](file:///Users/michaelmccabe/local-code/java/reactive-guide/src/main/java/com/example/reactiveguide/dto/PaymentResponse.java)

```java
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
```

## Description and Purpose
- **API Contract stability**: Represents the structured payload serialized to JSON and sent back to clients. It contains additional system-generated read-only fields like `id`, `createdAt`, and `updatedAt` that are not present in `PaymentRequest`.
- **Encapsulation**: Prevents direct exposure of the database model, allowing internal entity attributes to be modified without breaking client contracts.

## Usage Example
Expected output JSON:
```json
{
  "id": 1,
  "amount": 250.50,
  "currency": "EUR",
  "status": "PENDING",
  "paymentDate": "2026-08-07T15:46:19Z",
  "createdAt": "2026-08-07T15:48:00Z",
  "updatedAt": "2026-08-07T15:48:00Z"
}
```
In the controller:
```java
@GetMapping("/{id}")
public Mono<PaymentResponse> getPaymentById(@PathVariable Long id) {
    return paymentService.getPaymentById(id);
}
```
