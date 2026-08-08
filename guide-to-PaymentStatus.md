# Guide to PaymentStatus Enum

The `PaymentStatus` enum defines the list of valid states for a payment transaction.

## Definition
Located at: [PaymentStatus.java](file:///Users/michaelmccabe/local-code/java/reactive-guide/src/main/java/com/example/reactiveguide/domain/PaymentStatus.java)

```java
package com.example.reactiveguide.domain;

public enum PaymentStatus {
    PENDING,
    COMPLETED,
    FAILED,
    CANCELLED
}
```

## Description and Purpose
This enum is used across the system to enforce type safety on payment statuses:
- **`PENDING`**: The payment has been initiated but is not yet processed.
- **`COMPLETED`**: The payment was successful.
- **`FAILED`**: The payment processing failed (e.g. insufficient funds, network error).
- **`CANCELLED`**: The payment was cancelled before completion.

It is stored as a string in the database column `status` (e.g. `VARCHAR`), mapped by Spring Data R2DBC automatically.

## Usage Example
```java
PaymentStatus currentStatus = PaymentStatus.PENDING;

if (currentStatus == PaymentStatus.COMPLETED) {
    // Fulfil order/service
}
```
