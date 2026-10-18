# Reactive Java Guide

This project is a high-performance, contract-first reactive Web API built with Spring Boot WebFlux, Spring Data R2DBC, PostgreSQL, OpenAPI Generator, MapStruct, and Testcontainers. It supports full CRUD operations on payments, custom dynamic search filters, Prometheus metrics, and end-to-end workflow testing.

## Tech Stack
- **Language**: Java 25
- **Framework**: Spring Boot 3.5.16 (WebFlux)
- **Data Access**: Spring Data R2DBC
- **Database**: PostgreSQL (`r2dbc-postgresql` & `postgresql` drivers)
- **API Contract & Code Generation**: OpenAPI 3.0 (`openapi-generator-maven-plugin` 7.26.0), Lombok 1.18.48, MapStruct 1.6.3
- **API Documentation**: Springdoc OpenAPI WebFlux UI 2.8.17 (Swagger UI)
- **Observability**: Spring Boot Actuator & Micrometer Prometheus Registry
- **Testing & Quality**: JUnit 5, Reactor Test, BlockHound, Testcontainers 1.21.4, WebTestClient, JaCoCo 0.8.15 (95% line coverage enforcement), [Ramjam](https://github.com/michaelmccabe/ramjam) E2E workflow testing
- **CI/CD**: GitHub Actions (`.github/workflows/maven.yml`, `.github/workflows/integration-tests.yml`) & Dependabot (`.github/dependabot.yml`)

## Features
- **Contract-First Reactive API**: Reactive controller interfaces (`PaymentsApi`) are generated at build time from `src/main/resources/api/openapi.yaml`.
- **Non-blocking Pipelines**: Fully reactive request handling from the controller down to the database using Project Reactor (`Mono` / `Flux`), verified at test time with Reactor **BlockHound**.
- **Non-blocking Logging**: Uses Logback's `AsyncAppender` with a `neverBlock` strategy to ensure console logging never blocks the reactive Netty event loop threads.
- **Dynamic Search Filtering**: Query payments using combined query parameters (date range, amount, payment status) via `R2dbcEntityTemplate`.
- **Actuator & Prometheus Metrics**: Exposes `/health` for Kubernetes liveness/readiness probes and `/prometheus` for Micrometer metrics scraping.
- **Isolated Integration Testing**: Integration tests run against a real PostgreSQL instance started in a Docker container via Testcontainers, with JaCoCo enforcing a minimum 95% line coverage ratio.
- **Ramjam API Workflow Testing**: Declarative End-to-End API workflow testing using [Ramjam](https://github.com/michaelmccabe/ramjam), executed automatically in GitHub Actions CI prior to merging PRs (`.github/workflows/integration-tests.yml`). See [How to Test with Ramjam](ramjam-test/how-to-test-with-ramjam.md).

---

## Directory & Package Structure

```text
webflux-guide/
├── .github/
│   ├── dependabot.yml                         # Automated Maven & GitHub Actions dependency updates
│   └── workflows/
│       ├── maven.yml                          # Build & unit/integration test CI workflow (JDK 25)
│       └── integration-tests.yml              # Ramjam E2E integration test CI workflow
├── .mvn/
│   └── wrapper/
│       └── maven-wrapper.properties           # Maven Wrapper version & distribution settings
├── lombok.config                              # Lombok annotation processor configuration
├── mvnw                                       # Maven Wrapper script (Unix)
├── mvnw.cmd                                   # Maven Wrapper script (Windows)
├── pom.xml                                    # Maven configuration
├── README.md                                  # Application documentation
├── deployment_guide.md                        # Kubernetes & GCP Cloud Deployment Guide
├── guides/                                    # Detailed developer guides
│   ├── guide-to-Payment.md                    # Guide for Payment Entity
│   ├── guide-to-PaymentStatus.md              # Guide for PaymentStatus Enum
│   ├── guide-to-PaymentRepository.md          # Guide for PaymentRepository Interface
│   ├── guide-to-PaymentRequest.md             # Guide for PaymentRequest DTO
│   ├── guide-to-PaymentResponse.md            # Guide for PaymentResponse DTO
│   ├── guide-to-PaymentMapper.md              # Guide for PaymentMapper interface
│   ├── guide-to-PaymentService.md             # Guide for PaymentService class
│   ├── guide-to-PaymentController.md          # Guide for PaymentController REST Controller
│   ├── guide-to-debugging-reactive-code.md    # Guide for debugging reactive streams and pipelines
│   └── guide-to-kafka-event-publishing.md     # Guide for reactive Kafka event publishing
├── ramjam-test/                               # Declarative Ramjam E2E HTTP test workflows
│   ├── how-to-test-with-ramjam.md             # Guide for running Ramjam tests locally & in CI
│   ├── health-check.yaml                      # Health endpoint check workflow
│   ├── payments-crud.yaml                     # Full Payments CRUD lifecycle workflow
│   ├── payments-search-filter.yaml            # Dynamic search & filter workflow
│   ├── payment-not-found.yaml                 # 404 Not Found validation workflow
│   ├── file-reference.yaml                    # External JSON payload test workflow
│   ├── full-workflow.yaml                     # End-to-end multi-payment status transition workflow
│   └── payloads/
│       └── create-payment.json                # External JSON payload fixture
└── src/
    ├── main/
    │   ├── java/com/example/reactiveguide/
    │   │   ├── PaymentServiceApplication.java # Spring Boot application entry point
    │   │   ├── config/
    │   │   │   └── OpenApiConfig.java         # OpenAPI / Swagger configuration
    │   │   ├── controller/
    │   │   │   └── PaymentController.java     # Reactive REST controller implementing PaymentsApi
    │   │   ├── domain/
    │   │   │   ├── Payment.java               # R2DBC Payment entity record
    │   │   │   └── PaymentStatus.java         # Payment lifecycle status enum
    │   │   ├── dto/
    │   │   │   ├── PaymentRequest.java        # Request payload DTO
    │   │   │   └── PaymentResponse.java       # Response payload DTO
    │   │   ├── mapper/
    │   │   │   └── PaymentMapper.java         # MapStruct entity/DTO mapper interface
    │   │   ├── repository/
    │   │   │   └── PaymentRepository.java     # Reactive R2DBC CRUD repository interface
    │   │   └── service/
    │   │       └── PaymentService.java        # Payment business logic & dynamic query building
    │   └── resources/
    │       ├── api/
    │       │   └── openapi.yaml               # OpenAPI 3.0 contract specification
    │       ├── application.yml                # Application configuration
    │       ├── logback-spring.xml             # Non-blocking async Logback configuration
    │       └── schema.sql                     # PostgreSQL table creation schema
    └── test/
        ├── java/com/example/reactiveguide/
        │   ├── PaymentServiceApplicationTest.java # Application context & startup test
        │   ├── controller/
        │   │   └── PaymentControllerIT.java   # Full lifecycle integration tests (Testcontainers + BlockHound)
        │   └── service/
        │       └── PaymentServiceTest.java    # Unit tests for PaymentService
        └── resources/
            └── docker-java.properties         # Testcontainers Docker client configuration
```

---

## REST Endpoints

### 1. Interactive API Documentation (OpenAPI / Swagger)
- **Swagger UI**: **`GET /swagger-ui.html`**
  - Interactive browser UI to explore, test, and execute API calls directly.
- **OpenAPI Spec**: **`GET /v3/api-docs`**
  - Machine-readable JSON specification of all endpoints, parameters, and models.

### 2. Actuator Endpoints
- **`GET /health`**
  - Returns Spring Boot Actuator health status (e.g. `{"status": "UP"}`) with `200 OK`.
- **`GET /prometheus`**
  - Exposes Micrometer-formatted Prometheus metrics for monitoring CPU, memory, thread pool, R2DBC connection pool, and HTTP request latency/RPS.

### 3. Payments CRUD
- **`POST /api/payments`**
  - Creates a new payment.
  - Body: `PaymentRequest` (`amount`, `currency`, `status`, `paymentDate`).
- **`GET /api/payments/{id}`**
  - Retrieves a specific payment by ID.
- **`PUT /api/payments/{id}`**
  - Updates an existing payment by ID.
  - Body: `PaymentRequest` (`amount`, `currency`, `status`, `paymentDate`).
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

## Non-blocking Logging

To ensure the high performance of our reactive streams, logging is designed to be non-blocking. This is achieved using Logback's `AsyncAppender` in [logback-spring.xml](src/main/resources/logback-spring.xml):

* **Asynchronous Queueing**: Log events are buffered into an in-memory queue (capacity `2048`) and processed on a background thread.
* **No-block Policy (`neverBlock = true`)**: If the queue fills up under heavy load, log messages are discarded rather than blocking the critical reactive Netty event loop threads.
* **No Caller Data Retrieval**: Stack trace generation (needed for method name and line number logs) is a blocking operation and is disabled (`includeCallerData = false`) to maximize throughput.

---

## Run and Verify Locally

### Prerequisites
- Docker (required for Testcontainers integration tests and local PostgreSQL)
- Java 25 SDK
- Maven Wrapper (`./mvnw`) or Apache Maven

### 1. Build and Run Unit & Integration Tests
Run the Maven test suite to verify OpenAPI code generation, MapStruct compilation, unit tests, BlockHound checks, Testcontainers integration tests, and JaCoCo coverage:
```bash
./mvnw clean test
```

### 2. Running Locally (Development)
1. Ensure you have a running PostgreSQL database at `localhost:5432` with database `paymentdb`, user `postgres`, and password `password`:
   ```bash
   docker run -d --name local-payment-postgres \
     -e POSTGRES_USER=postgres \
     -e POSTGRES_PASSWORD=password \
     -e POSTGRES_DB=paymentdb \
     -p 5432:5432 \
     postgres:16
   ```
2. Run the application using the Spring Boot plugin (or packaged JAR):
   ```bash
   ./mvnw spring-boot:run
   ```
3. The database table `payments` will be created automatically on startup from `schema.sql`.

### 3. Running Ramjam E2E Workflow Tests
With the application running on `http://127.0.0.1:8080`, execute the Ramjam test suite:
```bash
ramjam run ramjam-test/ --verbose
```
See [How to Test with Ramjam](ramjam-test/how-to-test-with-ramjam.md) for installation instructions and details on individual test workflows.
