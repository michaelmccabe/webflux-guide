# Guide to PaymentService Class

The `PaymentService` class implements the business logic and orchestrates database transactions for payments.

## Definition
Located at: [PaymentService.java](file:///Users/michaelmccabe/local-code/java/reactive-guide/src/main/java/com/example/reactiveguide/service/PaymentService.java)

## Description and Purpose
- **CRUD Operations**: Exposes reactive CRUD methods returning `Mono` and `Flux`.
- **Dynamic Search Filtering**:
  - Implements dynamic queries using `R2dbcEntityTemplate` to filter payments by date range, amount, and payment status.
  - Combines individual relational criteria using `Criteria.and()` logic.
- **Auditing Fields**: Manages system fields like `createdAt` and `updatedAt` on creation and modification.
- **Immutable Updates**: Leverages the copy-on-write capabilities of Java records (using the Lombok-generated `toBuilder()`) to create updated instances of entities rather than mutating state in-place.
- **Error Handling**: Throws a `ResponseStatusException(HttpStatus.NOT_FOUND)` if a requested payment ID does not exist in the database.
- **Non-blocking Logging**: Utilizes Logback's `AsyncAppender` with `neverBlock=true` to prevent service logger statements (e.g. `log.info`) from blocking the Reactor event loops.


## Key Methods

### 1. `createPayment(PaymentRequest)`
Constructs a new `Payment` entity by mapping the request, stamps `createdAt`/`updatedAt` timestamps using `.toBuilder()`, and persists the resulting immutable record to the database.
```java
public Mono<PaymentResponse> createPayment(PaymentRequest request) { ... }
```

### 2. `updatePayment(Long, PaymentRequest)`
Finds an existing payment, constructs an updated copy of the `Payment` record using `.toBuilder()` populated with values from the request and a new `updatedAt` timestamp, and saves the new record instance.
```java
public Mono<PaymentResponse> updatePayment(Long id, PaymentRequest request) { ... }
```

### 3. `findPayments(Instant, Instant, BigDecimal, PaymentStatus)`
Performs non-blocking dynamic queries based on combinations of the provided parameters using Spring Data Relational's programmatic query support.
```java
public Flux<PaymentResponse> findPayments(Instant startDate, Instant endDate, BigDecimal amount, PaymentStatus status) { ... }
```

## Usage Example
```java
@Autowired
private PaymentService service;

// Find all completed payments since yesterday
Instant yesterday = Instant.now().minus(1, ChronoUnit.DAYS);
Flux<PaymentResponse> completedPayments = service.findPayments(yesterday, null, null, PaymentStatus.COMPLETED);
```
