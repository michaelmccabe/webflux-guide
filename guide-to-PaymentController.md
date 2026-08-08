# Guide to PaymentController Class

The `PaymentController` is a REST controller that exposes the WebFlux HTTP API endpoints for payments.

## Definition
Located at: [PaymentController.java](file:///Users/michaelmccabe/local-code/java/reactive-guide/src/main/java/com/example/reactiveguide/controller/PaymentController.java)

## Description and Purpose
- **Reactive HTTP Handlers**: Exposes endpoints handling HTTP operations asynchronously with Project Reactor types (`Mono` and `Flux`).
- **Endpoint Operations**:
  - `POST /api/payments`: Accepts a JSON `PaymentRequest` and returns `201 Created` with a `PaymentResponse`.
  - `PUT /api/payments/{id}`: Accepts a JSON `PaymentRequest` and updating the matching payment returning `200 OK` with a `PaymentResponse`.
  - `DELETE /api/payments/{id}`: Deletes the matching payment returning `244 No Content`.
  - `GET /api/payments/{id}`: Fetches the payment matching the specified path ID returning `200 OK` with a `PaymentResponse`.
  - `GET /api/payments`: Queries/lists payments based on optional filter parameters.
- **DateTime Formatting**: Uses `@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)` to correctly parse ISO-8601 query parameter values (e.g. `2026-08-07T15:46:19Z`) into Java `Instant` types.

## Request/Response Contract

### POST /api/payments
Request body:
```json
{
  "amount": 100.00,
  "currency": "USD",
  "status": "PENDING"
}
```
Response body:
```json
{
  "id": 1,
  "amount": 100.00,
  "currency": "USD",
  "status": "PENDING",
  "paymentDate": "2026-08-07T14:48:38Z",
  "createdAt": "2026-08-07T14:48:38Z",
  "updatedAt": "2026-08-07T14:48:38Z"
}
```

### GET /api/payments (with query parameters)
Example request:
`GET /api/payments?status=COMPLETED&startDate=2026-08-07T00:00:00Z&endDate=2026-08-07T23:59:59Z`

Returns:
```json
[
  {
    "id": 1,
    "amount": 100.00,
    "currency": "USD",
    "status": "COMPLETED",
    "paymentDate": "2026-08-07T14:48:38Z",
    "createdAt": "2026-08-07T14:48:38Z",
    "updatedAt": "2026-08-07T15:02:11Z"
  }
]
```

## Observability and Autoscaling Metrics

Every HTTP request routed through `PaymentController` is automatically monitored by Spring WebFlux and Micrometer:

- **Metrics Collection**: Accessing endpoints generates metrics stored in the Micrometer registry.
- **Key Metric**: `http_server_requests_seconds_count` captures the count of requests per route, outcome, and HTTP status.
- **Autoscaling Relevance**: 
  > [!IMPORTANT]
  > Because reactive applications do not block thread execution, CPU usage remains low even when high-throughput incoming traffic saturates the request queues. Scaling on CPU utilization alone is unreliable. Instead, use custom Prometheus metrics like **Requests Per Second (RPS)** (calculated using `rate(http_server_requests_seconds_count[1m])`) to scale pods proactively under heavy traffic.

