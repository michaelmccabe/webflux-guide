# How to Test Payment Service with Ramjam

This guide explains how to use [ramjam](https://github.com/michaelmccabe/ramjam) to execute end-to-end integration and workflow tests against the Reactive Payment Service (`webflux-guide`).

## What is Ramjam?

Ramjam is a declarative CLI testing tool written in Go that executes HTTP API workflows defined in YAML files. Key features include:

- **Declarative YAML DSL**: Clean and human-readable test scenarios.
- **Variable Capture & Substitution**: Extract IDs, tokens, and fields from responses (`capture`) and inject them into subsequent steps using `${variable_name}`.
- **JSONPath Matchers**: Validate response payload structures and specific fields (`expect.json_path_match`).
- **External Payload Files**: Reference reusable JSON payloads with `body_file`.
- **Lightweight CI Integration**: Pre-compiled single binary without external runtime requirements.

---

## Prerequisites

1. **PostgreSQL Database** running on port 5432 with database `paymentdb`, username `postgres`, and password `password`:
   ```bash
   docker run -d --name local-payment-postgres \
     -e POSTGRES_USER=postgres \
     -e POSTGRES_PASSWORD=password \
     -e POSTGRES_DB=paymentdb \
     -p 5432:5432 \
     postgres:16
   ```
2. **Java 25** & **Maven Wrapper** (`./mvnw`).
3. **Ramjam CLI**:
   - Install using Go:
     ```bash
     go install github.com/michaelmccabe/ramjam/cmd/ramjam@latest
     ```
   - Or download pre-built binary from [Ramjam Releases](https://github.com/michaelmccabe/ramjam/releases):
     ```bash
     curl -L -o ramjam https://github.com/michaelmccabe/ramjam/releases/download/v1.0.0-beta.1/ramjam-linux-amd64 # (or darwin-arm64 / darwin-amd64)
     chmod +x ramjam
     sudo mv ramjam /usr/local/bin/
     ```

---

## Starting the Application

Build and start the Payment Service:

```bash
# 1. Package the service
./mvnw clean package -DskipTests

# 2. Run the application
java -jar target/reactive-guide-0.0.1-SNAPSHOT.jar
```

Verify that the service is running:
```bash
curl http://127.0.0.1:8080/health
# Output: {"status":"UP", ...}
```

---

## Running Ramjam Tests

### Run All Tests in the Test Suite

```bash
ramjam run ramjam-test/
```

### Run with Verbose Output

```bash
ramjam run ramjam-test/ --verbose
```

### Run Specific Test Files

```bash
# Run Health Check
ramjam run ramjam-test/health-check.yaml

# Run Payments CRUD
ramjam run ramjam-test/payments-crud.yaml

# Run Payments Search & Filter
ramjam run ramjam-test/payments-search-filter.yaml

# Run Payment Not Found Validation
ramjam run ramjam-test/payment-not-found.yaml

# Run External Payload File Reference
ramjam run ramjam-test/file-reference.yaml

# Run Full End-to-End Workflow
ramjam run ramjam-test/full-workflow.yaml
```

---

## Test Files Overview

| File | Description |
|---|---|
| `health-check.yaml` | Verifies Spring Boot Actuator `/health` endpoint. |
| `payments-crud.yaml` | Complete CRUD test suite for `/api/payments` (Create, Read, List, Update, Delete, Verify 404). |
| `payments-search-filter.yaml` | Tests dynamic query filtering on `/api/payments` by `amount`, `status`, and `startDate`/`endDate`. |
| `payment-not-found.yaml` | Verifies `404 Not Found` handling for `GET`, `PUT`, and `DELETE` on non-existent payment IDs. |
| `file-reference.yaml` | Tests payload loading from external file (`payloads/create-payment.json`). |
| `full-workflow.yaml` | Comprehensive end-to-end payment lifecycle across `PENDING`, `COMPLETED`, and `CANCELLED` states with search verification and cleanup. |

---

## GitHub Actions CI/CD Integration

Ramjam integration tests are run automatically in GitHub Actions on every push and pull request against `main` via `.github/workflows/integration-tests.yml`.

### Workflow Structure

1. Spins up a PostgreSQL 16 service container (`paymentdb`).
2. Builds the project (`./mvnw -B package -DskipTests --file pom.xml`).
3. Installs the `ramjam` CLI binary.
4. Starts the Spring Boot WebFlux application in the background.
5. Polls `/health` until ready.
6. Executes `ramjam run ramjam-test/ --verbose`.
7. Tears down the background process.

---

## Troubleshooting

### Connection Refused
Ensure the application is running and listening on port 8080:
```bash
curl http://127.0.0.1:8080/health
```

### Clearing Local Database State
If re-running tests against a persistent local database and you want a clean slate:
```bash
docker exec -i local-payment-postgres psql -U postgres -d paymentdb -c "TRUNCATE payments RESTART IDENTITY CASCADE;"
```

### Debugging Failures
Use the `--verbose` flag to view exact request payloads, response codes, headers, and JSON evaluation steps:
```bash
ramjam run ramjam-test/<test-file>.yaml --verbose
```
