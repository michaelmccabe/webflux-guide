# Reactive Java Guide

This project is a high-performance, reactive Web API built with Spring Boot, WebFlux, Spring Data R2DBC, PostgreSQL, MapStruct, and Testcontainers. It supports full CRUD operations on payments, custom dynamic filters, and health monitoring.

## Tech Stack
- **Language**: Java 21
- **Framework**: Spring Boot 3.3.2 (WebFlux)
- **Data Access**: Spring Data R2DBC
- **Database**: PostgreSQL (reactive driver)
- **Mapping**: MapStruct
- **Code Generation**: Lombok
- **Testing**: JUnit 5, Testcontainers, WebTestClient

## Features
- **Non-blocking Pipelines**: Fully reactive request handling from the controller down to the database using Project Reactor (`Mono` / `Flux`).
- **Dynamic Search Filtering**: Query payments using combined query parameters (date range, amount, payment status).
- **Health Check Endpoint**: `/health` API for Kubernetes liveness/readiness probes.
- **Isolated Testing**: Integration tests run against a real PostgreSQL instance started in a Docker container via Testcontainers.

---

## Directory & Package Structure

```text
reactive-guide/
├── pom.xml                                   # Maven configuration
├── README.md                                 # Application documentation
├── deployment_guide.md                        # Kubernetes & GCP Cloud Deployment Guide
├── guide-to-Payment.md                       # Guide for Payment Entity
├── guide-to-PaymentStatus.md                 # Guide for PaymentStatus Enum
├── guide-to-PaymentRepository.md             # Guide for PaymentRepository Interface
├── guide-to-PaymentRequest.md                # Guide for PaymentRequest DTO
├── guide-to-PaymentResponse.md               # Guide for PaymentResponse DTO
├── guide-to-PaymentMapper.md                 # Guide for PaymentMapper interface
├── guide-to-PaymentService.md                # Guide for PaymentService class
├── guide-to-PaymentController.md             # Guide for PaymentController REST Controller
├── guide-to-debugging-reactive-code.md       # Guide for debugging reactive streams and pipelines
└── src/

    ├── main/
    │   ├── java/com/example/reactiveguide/
    │   │   ├── PaymentServiceApplication.java # Spring Boot application entry point
    │   │   ├── domain/                       # Entity models & enums
    │   │   ├── repository/                   # Database CRUD interfaces
    │   │   ├── dto/                          # Data Transfer Objects
    │   │   ├── mapper/                       # MapStruct interface mappers
    │   │   ├── service/                      # Payment business logic & dynamic query building
    │   │   └── controller/                   # REST request routes
    │   └── resources/
    │       ├── application.yml               # Application configuration
    │       └── schema.sql                    # Postgres table creation schema
    └── test/
        └── java/com/example/reactiveguide/
            └── controller/
                └── PaymentControllerIT.java  # Full lifecycle integration tests (Testcontainers)
```

---

## REST Endpoints

### 1. Actuator Endpoints
- **`GET /health`**
  - Returns Spring Boot Actuator health status (e.g. `{"status": "UP"}`) with `200 OK`.
- **`GET /prometheus`**
  - Exposes Micrometer-formatted Prometheus metrics for monitoring CPU, memory, thread pool, R2DBC connection pool, etc.


### 2. Payments CRUD
- **`POST /api/payments`**
  - Creates a new payment.
  - Body: `PaymentRequest` (amount, currency, status, paymentDate).
- **`GET /api/payments/{id}`**
  - Retrieves a specific payment by ID.
- **`PUT /api/payments/{id}`**
  - Updates an existing payment by ID.
  - Body: `PaymentRequest` (amount, currency, status, paymentDate).
- **`DELETE /api/payments/{id}`**
  - Deletes a payment by ID.
- **`GET /api/payments`**
  - Lists and filters payments.
  - Query parameters (optional):
    - `startDate`: ISO-8601 string (e.g. `2026-08-07T00:00:00Z`)
    - `endDate`: ISO-8601 string (e.g. `2026-08-07T23:59:59Z`)
    - `amount`: Number (e.g. `150.00`)
    - `status`: One of `PENDING`, `COMPLETED`, `FAILED`, `CANCELLED`

---

## Run and Verify Locally

### Prerequisites
- Docker (required for Testcontainers integration tests)
- Java 21 SDK
- Apache Maven

### 1. Build and Run Tests
Run the Maven test suite to verify the application code, MapStruct compilation, and Testcontainers setup:
```bash
mvn clean test
```

### 2. Running Locally (Development)
1. Ensure you have a running PostgreSQL database at `localhost:5432` with database `paymentdb`, user `postgres`, and password `password`.
2. Run the application using the Spring Boot plugin:
   ```bash
   mvn spring-boot:run
   ```
3. The database table `payments` will be created automatically on startup from `schema.sql`.
