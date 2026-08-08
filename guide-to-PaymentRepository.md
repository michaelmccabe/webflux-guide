# Guide to PaymentRepository Interface

The `PaymentRepository` interface handles the database interactions for the `Payment` entity.

## Definition
Located at: [PaymentRepository.java](file:///Users/michaelmccabe/local-code/java/reactive-guide/src/main/java/com/example/reactiveguide/repository/PaymentRepository.java)

```java
package com.example.reactiveguide.repository;

import com.example.reactiveguide.domain.Payment;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends ReactiveCrudRepository<Payment, Long> {
}
```

## Description and Purpose
- **Reactive Database Access**: By extending `ReactiveCrudRepository`, the repository inherits asynchronous, non-blocking CRUD operations on the `payments` table, returning Project Reactor types (`Mono` and `Flux`).
- **Underlying Driver**: Uses R2DBC (Reactive Relational Database Connectivity) instead of JDBC to ensure the entire execution chain remains non-blocking.
- **Inherited Methods**:
  - `save(Payment entity)`: Saves or updates the entity, returning `Mono<Payment>`.
  - `findById(Long id)`: Finds an entity by primary key, returning `Mono<Payment>`.
  - `delete(Payment entity)`: Deletes an entity, returning `Mono<Void>`.
  - `findAll()`: Returns all entities as a `Flux<Payment>`.

## Usage Example
```java
@Autowired
private PaymentRepository repository;

public Mono<Payment> findAndLogPayment(Long id) {
    return repository.findById(id)
            .doOnNext(payment -> System.out.println("Retrieved: " + payment));
}
```
