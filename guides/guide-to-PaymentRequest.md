# Guide to PaymentRequest DTO

The `PaymentRequest` class is a Data Transfer Object (DTO) used to capture client input when creating or updating a payment.

## Definition
Located at: [PaymentRequest.java](file:///Users/michaelmccabe/local-code/java/reactive-guide/src/main/java/com/example/reactiveguide/dto/PaymentRequest.java)

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
public class PaymentRequest {
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private Instant paymentDate;
}
```

## Description and Purpose
- **API Boundary Protection**: It decouples the API contract from the persistent database entity (`Payment`), preventing clients from setting internal fields like `id`, `createdAt`, or `updatedAt` directly.
- **Payload Deserialization**: Jackson uses this class to deserialize incoming JSON payloads in `POST` and `PUT` request bodies.
- **Convenient Fields**: Includes only the client-updatable properties required to initialize or modify a payment.

## Usage Example
```json
{
  "amount": 250.50,
  "currency": "EUR",
  "status": "PENDING",
  "paymentDate": "2026-08-07T15:46:19Z"
}
```
In the controller:
```java
@PostMapping
public Mono<PaymentResponse> createPayment(@RequestBody PaymentRequest request) {
    return paymentService.createPayment(request);
}
```
