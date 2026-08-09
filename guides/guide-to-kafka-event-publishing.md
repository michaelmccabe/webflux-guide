# Guide to Kafka Event Publishing for Payment Events

This guide explains how to add Kafka-based event publishing to the reactive payment service so that downstream consumers are notified whenever a payment is created or updated. It covers the required dependencies, configuration, Kafka topic tuning, and provides a detailed comparison of two integration patterns — **Transactional Outbox** and **Event Sourcing** — along with a final recommendation.

---

## Table of Contents

1. [What Needs to Be Added](#1-what-needs-to-be-added)
2. [Kafka Topic Configuration — Local vs Production](#2-kafka-topic-configuration--local-vs-production)
3. [Pattern 1 — Transactional Outbox](#3-pattern-1--transactional-outbox)
4. [Pattern 2 — Event Sourcing](#4-pattern-2--event-sourcing)
5. [Recommendation](#5-recommendation)

---

## 1. What Needs to Be Added

### 1.1 Maven Dependencies

Add to [pom.xml](file:///home/michael/code/java/webflux-guide/pom.xml):

```xml
<!-- Reactive Kafka -->
<dependency>
    <groupId>io.projectreactor.kafka</groupId>
    <artifactId>reactor-kafka</artifactId>
</dependency>

<!-- Jackson for serializing event payloads (already transitively available, but declare explicitly) -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
```

`reactor-kafka` is the natural choice for this project because the entire stack is reactive (WebFlux + R2DBC). It wraps the Apache Kafka client with `Flux`/`Mono` types and plugs directly into the Reactor event loop without blocking.

> **Note:** `spring-kafka` would also work, but its `KafkaTemplate` is blocking under the hood. Wrapping it in `Mono.fromFuture()` is viable but less idiomatic than using `reactor-kafka` natively.

### 1.2 Event Payload Model

Create a new package `com.example.reactiveguide.event`:

```java
package com.example.reactiveguide.event;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentEvent(
    String eventId,       // UUID — idempotency key for consumers
    String eventType,     // "PAYMENT_CREATED" or "PAYMENT_UPDATED"
    Long paymentId,
    BigDecimal amount,
    String currency,
    String status,
    Instant paymentDate,
    Instant occurredAt    // when the event was produced
) {}
```

Using a `record` keeps the event immutable and mirrors the existing `Payment` domain style. The `eventId` field (a UUID) enables consumer-side idempotent processing.

### 1.3 Kafka Producer Configuration

Create `com.example.reactiveguide.config.KafkaProducerConfig`:

```java
package com.example.reactiveguide.config;

import com.example.reactiveguide.event.PaymentEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.serializer.JsonSerializer;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public KafkaSender<String, PaymentEvent> kafkaSender(ObjectMapper objectMapper) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        SenderOptions<String, PaymentEvent> senderOptions =
            SenderOptions.<String, PaymentEvent>create(props)
                .maxInFlight(1024);

        return KafkaSender.create(senderOptions);
    }
}
```

Key producer settings explained:
- **`acks=all`** — the broker waits for all in-sync replicas to acknowledge. Prevents data loss.
- **`enable.idempotence=true`** — the Kafka client deduplicates retries at the broker level.
- **`retries=3`** — automatically retries transient network failures.
- **`maxInFlight(1024)`** — reactor-kafka back-pressure setting; controls how many sends can be in flight concurrently.

### 1.4 Event Publisher Service

```java
package com.example.reactiveguide.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentEventPublisher {

    private final KafkaSender<String, PaymentEvent> kafkaSender;

    @Value("${app.kafka.topic.payments}")
    private String topic;

    /**
     * Publishes a payment event to Kafka.
     * Uses the paymentId as the message key so all events for the same
     * payment land on the same partition (preserving per-payment ordering).
     */
    public Mono<Void> publish(PaymentEvent event) {
        ProducerRecord<String, PaymentEvent> record =
            new ProducerRecord<>(topic, String.valueOf(event.paymentId()), event);

        return kafkaSender.send(Mono.just(SenderRecord.create(record, event.eventId())))
            .doOnNext(result -> log.info("Published event [{}] for payment [{}] to partition [{}] at offset [{}]",
                event.eventType(), event.paymentId(),
                result.recordMetadata().partition(), result.recordMetadata().offset()))
            .doOnError(e -> log.error("Failed to publish event [{}] for payment [{}]",
                event.eventType(), event.paymentId(), e))
            .then();
    }
}
```

### 1.5 Integrating into PaymentService

The simplest (non-outbox) integration chains the publish call after the database save in [PaymentService.java](file:///home/michael/code/java/webflux-guide/src/main/java/com/example/reactiveguide/service/PaymentService.java):

```java
// New dependency injection
private final PaymentEventPublisher eventPublisher;

public Mono<PaymentResponse> createPayment(PaymentRequest request) {
    log.info("Creating new payment: {}", request);
    Payment payment = paymentMapper.toEntity(request);
    Instant now = Instant.now();
    Payment updatedPayment = payment.toBuilder()
            .createdAt(now)
            .updatedAt(now)
            .paymentDate(payment.paymentDate() != null ? payment.paymentDate() : now)
            .build();
    return paymentRepository.save(updatedPayment)
            .flatMap(saved -> {
                PaymentEvent event = new PaymentEvent(
                    UUID.randomUUID().toString(),
                    "PAYMENT_CREATED",
                    saved.id(), saved.amount(), saved.currency(),
                    saved.status().name(), saved.paymentDate(),
                    Instant.now()
                );
                return eventPublisher.publish(event).thenReturn(saved);
            })
            .map(paymentMapper::toResponse)
            .doOnSuccess(saved -> log.info("Created payment successfully with ID: {}", saved.getId()));
}
```

> **Warning:** This "publish inline" approach has a consistency gap — the DB save could succeed but the Kafka publish could fail (or vice versa if you publish first). The Transactional Outbox pattern (Section 3) addresses this.

### 1.6 Application YAML Additions

Add to [application.yml](file:///home/michael/code/java/webflux-guide/src/main/resources/application.yml):

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092

app:
  kafka:
    topic:
      payments: payment-events
```

### 1.7 Docker Compose for Local Kafka

Add a `docker-compose-kafka.yml` at the project root:

```yaml
services:
  kafka:
    image: apache/kafka:3.9.0
    ports:
      - "9092:9092"
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS: 0
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
```

This uses **KRaft mode** (no Zookeeper required) — a single-node broker ideal for local development.

---

## 2. Kafka Topic Configuration — Local vs Production

### 2.1 Local Development

| Setting | Value | Why |
|---|---|---|
| `partitions` | `1` | Single partition is sufficient; no need to parallelise locally. |
| `replication.factor` | `1` | Only one broker; replication is impossible. |
| `min.insync.replicas` | `1` | Matches the single replica. |
| `retention.ms` | `86400000` (1 day) | Keep messages short to save disk. |
| `cleanup.policy` | `delete` | Simple deletion; no compaction needed. |
| `auto.create.topics.enable` | `true` | Convenience for dev — topics are created on first produce. |

Local topic creation (optional — will auto-create if enabled):
```bash
kafka-topics.sh --create \
  --bootstrap-server localhost:9092 \
  --topic payment-events \
  --partitions 1 \
  --replication-factor 1
```

### 2.2 Production — High Throughput

| Setting | Value | Why |
|---|---|---|
| `partitions` | `12–30` | Allows parallel consumption across multiple consumer instances. Choose based on expected peak TPS. One consumer thread per partition. |
| `replication.factor` | `3` | Tolerate up to 2 broker failures without data loss. |
| `min.insync.replicas` | `2` | Combined with `acks=all`, guarantees at least 2 replicas have the data before acknowledging. |
| `retention.ms` | `604800000` (7 days) | Gives consumers a week to catch up after outages. |
| `cleanup.policy` | `delete` (or `compact,delete`) | `compact` is useful if consumers only care about latest state per key. |
| `auto.create.topics.enable` | `false` | Topics should be pre-provisioned via IaC / Terraform. Prevents accidental topic creation from typos. |
| `max.message.bytes` | `1048576` (1 MB) | Default is fine for payment events; increase only if payloads are larger. |
| `compression.type` | `lz4` or `zstd` | Reduces network and disk I/O significantly at high throughput. |

Additional production **producer** tuning:
```yaml
# application-prod.yml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BROKERS}
    producer:
      acks: all
      retries: 5
      properties:
        enable.idempotence: true
        max.in.flight.requests.per.connection: 5
        linger.ms: 10            # batch sends for 10ms to improve throughput
        batch.size: 65536        # 64KB batch size
        compression.type: lz4
```

Production topic creation:
```bash
kafka-topics.sh --create \
  --bootstrap-server $KAFKA_BROKERS \
  --topic payment-events \
  --partitions 18 \
  --replication-factor 3 \
  --config min.insync.replicas=2 \
  --config retention.ms=604800000 \
  --config compression.type=lz4
```

### 2.3 Summary of Differences

| Concern | Local | Production |
|---|---|---|
| **Goal** | Fast feedback loop | Durability, throughput, fault tolerance |
| **Brokers** | 1 (KRaft, no Zookeeper) | 3+ (dedicated cluster) |
| **Partitions** | 1 | 12–30+ (tuned to TPS) |
| **Replication** | 1 | 3 |
| **ISR** | 1 | 2 |
| **Retention** | 1 day | 7 days |
| **Compression** | None | lz4 / zstd |
| **Auto-create** | ✅ | ❌ |
| **Producer batching** | Off (low latency) | On (`linger.ms`, `batch.size`) |

---

## 3. Pattern 1 — Transactional Outbox

### 3.1 What Is It?

The Transactional Outbox pattern solves the **dual-write problem**: when you need to update a database *and* publish a message, and you need both to succeed or both to effectively not happen. It works by writing the event into an `outbox` table within the same database transaction as the business data, then a separate process reads the outbox and publishes to Kafka.

```
┌─────────────────────────────────────────────────┐
│          Single Database Transaction            │
│                                                 │
│  1. INSERT/UPDATE payments table                │
│  2. INSERT into outbox table                    │
│                                                 │
└─────────────────────────────────────────────────┘
         │
         │ (async, separate process)
         ▼
┌─────────────────────────────────────────────────┐
│  Outbox Poller / CDC                            │
│                                                 │
│  3. Read outbox rows                            │
│  4. Publish to Kafka                            │
│  5. Mark rows as published / delete them        │
│                                                 │
└─────────────────────────────────────────────────┘
```

### 3.2 What Changes Are Needed

#### 3.2.1 Database — New Outbox Table

Add to [schema.sql](file:///home/michael/code/java/webflux-guide/src/main/resources/schema.sql):

```sql
CREATE TABLE IF NOT EXISTS outbox_events (
    id            BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(100)  NOT NULL,   -- e.g. 'Payment'
    aggregate_id   BIGINT        NOT NULL,   -- e.g. the payment ID
    event_type     VARCHAR(100)  NOT NULL,   -- e.g. 'PAYMENT_CREATED'
    payload        JSONB         NOT NULL,   -- the full event serialized as JSON
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    published      BOOLEAN       NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_outbox_unpublished
    ON outbox_events (published, created_at)
    WHERE published = FALSE;
```

The partial index on unpublished rows ensures the poller query remains fast even as the table grows.

#### 3.2.2 Domain & Repository

```java
// com.example.reactiveguide.domain.OutboxEvent
package com.example.reactiveguide.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.Instant;

@Table("outbox_events")
public record OutboxEvent(
    @Id Long id,
    String aggregateType,
    Long aggregateId,
    String eventType,
    String payload,       // JSON string
    Instant createdAt,
    boolean published
) {}
```

```java
// com.example.reactiveguide.repository.OutboxEventRepository
package com.example.reactiveguide.repository;

import com.example.reactiveguide.domain.OutboxEvent;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface OutboxEventRepository extends ReactiveCrudRepository<OutboxEvent, Long> {

    @Query("SELECT * FROM outbox_events WHERE published = FALSE ORDER BY created_at LIMIT :limit")
    Flux<OutboxEvent> findUnpublished(int limit);

    @Query("UPDATE outbox_events SET published = TRUE WHERE id = :id")
    Mono<Void> markPublished(Long id);
}
```

#### 3.2.3 PaymentService — Write to Outbox in the Same Transaction

The key change is that the service **no longer publishes to Kafka directly**. Instead it inserts into the outbox table alongside the payment save. Because R2DBC does not support distributed/XA transactions, both writes must target the **same database** — which is inherently the case here.

However, a critical consideration: **R2DBC and `@Transactional`**. Spring's `@Transactional` with R2DBC works via `ReactiveTransactionManager`. Both the payment save and outbox insert will share the same reactive transaction if they use the same `ConnectionFactory`.

```java
import org.springframework.transaction.annotation.Transactional;

@Transactional
public Mono<PaymentResponse> createPayment(PaymentRequest request) {
    log.info("Creating new payment: {}", request);
    Payment payment = paymentMapper.toEntity(request);
    Instant now = Instant.now();
    Payment prepared = payment.toBuilder()
            .createdAt(now)
            .updatedAt(now)
            .paymentDate(payment.paymentDate() != null ? payment.paymentDate() : now)
            .build();

    return paymentRepository.save(prepared)
            .flatMap(saved -> {
                PaymentEvent event = new PaymentEvent(
                    UUID.randomUUID().toString(), "PAYMENT_CREATED",
                    saved.id(), saved.amount(), saved.currency(),
                    saved.status().name(), saved.paymentDate(), Instant.now()
                );
                OutboxEvent outbox = new OutboxEvent(
                    null, "Payment", saved.id(), "PAYMENT_CREATED",
                    objectMapper.writeValueAsString(event),
                    Instant.now(), false
                );
                return outboxEventRepository.save(outbox).thenReturn(saved);
            })
            .map(paymentMapper::toResponse);
}
```

> **Note**: `objectMapper.writeValueAsString()` throws a checked `JsonProcessingException`. In practice, wrap this in a helper that converts to a `RuntimeException`, or use `Mono.fromCallable()`.

#### 3.2.4 Outbox Poller — Relay Events to Kafka

A `@Scheduled` (or reactive interval-based) component polls the outbox table and publishes events:

```java
package com.example.reactiveguide.event;

import com.example.reactiveguide.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPoller {

    private final OutboxEventRepository outboxRepository;
    private final KafkaSender<String, PaymentEvent> kafkaSender;
    private final ObjectMapper objectMapper;

    /**
     * Polls every 500ms locally. In production, consider Debezium CDC instead.
     */
    @Scheduled(fixedDelay = 500)
    public void pollAndPublish() {
        outboxRepository.findUnpublished(100)
            .flatMap(outboxEvent -> {
                PaymentEvent event = objectMapper.readValue(outboxEvent.payload(), PaymentEvent.class);
                ProducerRecord<String, PaymentEvent> record =
                    new ProducerRecord<>("payment-events",
                        String.valueOf(outboxEvent.aggregateId()), event);

                return kafkaSender.send(Mono.just(SenderRecord.create(record, outboxEvent.id())))
                    .next()
                    .flatMap(result ->
                        outboxRepository.markPublished(outboxEvent.id())
                            .doOnSuccess(v -> log.info("Published outbox event [{}] at offset [{}]",
                                outboxEvent.id(), result.recordMetadata().offset()))
                    );
            })
            .doOnError(e -> log.error("Outbox polling error", e))
            .subscribe();
    }
}
```

> **Note**: `@Scheduled` uses a thread-pool-based scheduler. Because the actual work is delegated to reactive operators (`flatMap`, `kafkaSender.send`), the scheduled thread only triggers the pipeline — it doesn't block on I/O. However, you must add `@EnableScheduling` to your application class or a config class.

#### 3.2.5 Alternative: Debezium CDC Instead of Polling

For production, **Debezium** (a Change Data Capture connector running on Kafka Connect) can tail the PostgreSQL WAL (Write-Ahead Log) and automatically stream outbox rows to Kafka. This eliminates polling and reduces latency to near-real-time.

The Debezium approach:
1. Deploy Debezium as a Kafka Connect connector.
2. Configure it to watch the `outbox_events` table.
3. Use the `outbox.EventRouter` SMT (Single Message Transform) to route events to the correct topic.
4. Delete outbox rows after they've been published (or let Debezium handle it).

This removes all polling code from the application — the app only writes to the outbox table.

### 3.3 Pros and Cons

| Pros | Cons |
|---|---|
| **Atomicity** — event and data in same DB transaction, no dual-write risk. | Adds an outbox table and poller (or CDC connector) — more infrastructure. |
| **Simple mental model** — the service layer just writes to the DB; publishing is a separate concern. | Polling adds latency (hundreds of ms). CDC (Debezium) removes this but adds operational complexity. |
| **Works with existing R2DBC** — no need to change the persistence layer. | Outbox table grows; needs cleanup (via `DELETE` or `retention` policies). |
| **Events are replayable** — if Kafka publish fails, the event stays in the outbox. | Consumers must still handle **at-least-once** semantics (deduplication). |

---

## 4. Pattern 2 — Event Sourcing

### 4.1 What Is It?

In Event Sourcing, the **events themselves are the source of truth**. Instead of storing the current state of a payment in a mutable row, you store an append-only sequence of events (e.g., `PaymentCreated`, `PaymentAmountUpdated`, `PaymentStatusChanged`). The current state is derived by replaying all events for a given aggregate (payment).

```
┌──────────────────────────────────────────────┐
│         Event Store (payment_events table)    │
│                                               │
│  event_id | aggregate_id | type            | payload           | version | timestamp      │
│  ─────────┼──────────────┼─────────────────┼───────────────────┼─────────┼──────────────  │
│  uuid-1   | 42           | PAYMENT_CREATED | {amount:100, ...} | 1       | 2026-08-09...  │
│  uuid-2   | 42           | STATUS_CHANGED  | {status:COMPLETED}| 2       | 2026-08-09...  │
│                                               │
└──────────────────────────────────────────────┘
           │
           │ (replay / project)
           ▼
┌──────────────────────────────────────────────┐
│       Read Model (payments table, optional)   │
│       Materialized view of current state      │
└──────────────────────────────────────────────┘
           │
           │ (publish to Kafka from event store)
           ▼
┌──────────────────────────────────────────────┐
│       Kafka: payment-events topic             │
└──────────────────────────────────────────────┘
```

### 4.2 What Changes Are Needed

This is a **fundamental architectural change**. Unlike the outbox pattern (an additive enhancement), event sourcing replaces the core persistence model.

#### 4.2.1 Database — Event Store Table

Replace or supplement the `payments` table with an events table:

```sql
CREATE TABLE IF NOT EXISTS payment_events (
    event_id       UUID PRIMARY KEY,
    aggregate_id   BIGINT       NOT NULL,   -- the payment ID
    event_type     VARCHAR(100) NOT NULL,
    payload        JSONB        NOT NULL,
    version        INTEGER      NOT NULL,   -- optimistic concurrency control
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    UNIQUE (aggregate_id, version)           -- enforces event ordering per aggregate
);

CREATE INDEX IF NOT EXISTS idx_payment_events_aggregate
    ON payment_events (aggregate_id, version);
```

Optionally keep the `payments` table as a **read-model projection** (CQRS):

```sql
-- The existing payments table becomes a read model, updated asynchronously
-- or inline after event persistence.
```

#### 4.2.2 Event Types — Fine-Grained Events

Event sourcing encourages **granular, semantic events** rather than generic CRUD events:

```java
package com.example.reactiveguide.event;

import java.math.BigDecimal;
import java.time.Instant;

public sealed interface PaymentDomainEvent {

    String eventId();
    Long aggregateId();
    Instant occurredAt();

    record PaymentCreated(
        String eventId, Long aggregateId,
        BigDecimal amount, String currency, String status,
        Instant paymentDate, Instant occurredAt
    ) implements PaymentDomainEvent {}

    record PaymentAmountUpdated(
        String eventId, Long aggregateId,
        BigDecimal oldAmount, BigDecimal newAmount,
        Instant occurredAt
    ) implements PaymentDomainEvent {}

    record PaymentStatusChanged(
        String eventId, Long aggregateId,
        String oldStatus, String newStatus,
        Instant occurredAt
    ) implements PaymentDomainEvent {}
}
```

Using Java's `sealed interface` gives exhaustive pattern matching in `switch` expressions — the compiler ensures every event type is handled.

#### 4.2.3 Event Store Repository

```java
package com.example.reactiveguide.repository;

import com.example.reactiveguide.domain.PaymentEventEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PaymentEventStoreRepository extends ReactiveCrudRepository<PaymentEventEntity, String> {

    @Query("SELECT * FROM payment_events WHERE aggregate_id = :aggregateId ORDER BY version ASC")
    Flux<PaymentEventEntity> findByAggregateIdOrderByVersion(Long aggregateId);

    @Query("SELECT MAX(version) FROM payment_events WHERE aggregate_id = :aggregateId")
    Mono<Integer> findLatestVersion(Long aggregateId);
}
```

#### 4.2.4 Aggregate — Rebuild State from Events

```java
package com.example.reactiveguide.domain;

import com.example.reactiveguide.event.PaymentDomainEvent;
import com.example.reactiveguide.event.PaymentDomainEvent.*;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentAggregate(
    Long id,
    BigDecimal amount,
    String currency,
    PaymentStatus status,
    Instant paymentDate,
    Instant createdAt,
    Instant updatedAt,
    int version
) {
    public static PaymentAggregate empty(Long id) {
        return new PaymentAggregate(id, null, null, null, null, null, null, 0);
    }

    /**
     * Applies a single event to produce the next state.
     * This is a pure function — no side effects.
     */
    public PaymentAggregate apply(PaymentDomainEvent event) {
        return switch (event) {
            case PaymentCreated e -> new PaymentAggregate(
                e.aggregateId(), e.amount(), e.currency(),
                PaymentStatus.valueOf(e.status()), e.paymentDate(),
                e.occurredAt(), e.occurredAt(), version + 1
            );
            case PaymentAmountUpdated e -> new PaymentAggregate(
                id, e.newAmount(), currency, status,
                paymentDate, createdAt, e.occurredAt(), version + 1
            );
            case PaymentStatusChanged e -> new PaymentAggregate(
                id, amount, currency, PaymentStatus.valueOf(e.newStatus()),
                paymentDate, createdAt, e.occurredAt(), version + 1
            );
        };
    }
}
```

#### 4.2.5 PaymentService — Rewritten for Event Sourcing

```java
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentEventStoreRepository eventStore;
    private final PaymentEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public Mono<PaymentAggregate> createPayment(PaymentRequest request) {
        PaymentCreated event = new PaymentCreated(
            UUID.randomUUID().toString(),
            null, // aggregate ID assigned by DB sequence
            request.getAmount(), request.getCurrency(),
            request.getStatus().name(),
            request.getPaymentDate() != null ? request.getPaymentDate() : Instant.now(),
            Instant.now()
        );

        // 1. Persist the event
        // 2. Publish to Kafka
        // 3. Update the read model (projection)
        return persistEvent(event, 1)
            .flatMap(saved -> eventPublisher.publish(toKafkaEvent(saved)).thenReturn(saved))
            .map(entity -> PaymentAggregate.empty(entity.aggregateId()).apply(event));
    }

    public Mono<PaymentAggregate> updatePaymentStatus(Long id, PaymentStatus newStatus) {
        return loadAggregate(id)
            .flatMap(aggregate -> {
                PaymentStatusChanged event = new PaymentStatusChanged(
                    UUID.randomUUID().toString(), id,
                    aggregate.status().name(), newStatus.name(),
                    Instant.now()
                );
                return persistEvent(event, aggregate.version() + 1)
                    .flatMap(saved -> eventPublisher.publish(toKafkaEvent(saved)).thenReturn(saved))
                    .map(entity -> aggregate.apply(event));
            });
    }

    /**
     * Rebuilds the aggregate by replaying all events.
     */
    public Mono<PaymentAggregate> loadAggregate(Long aggregateId) {
        return eventStore.findByAggregateIdOrderByVersion(aggregateId)
            .map(this::toDomainEvent)
            .reduce(PaymentAggregate.empty(aggregateId), PaymentAggregate::apply)
            .filter(agg -> agg.status() != null) // empty means no events found
            .switchIfEmpty(Mono.error(
                new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Payment not found: " + aggregateId)));
    }

    // ... helper methods for serialization, persistence, projection updates
}
```

#### 4.2.6 Read Model Projection (CQRS)

Queries (like `findPayments`) cannot efficiently run against an event store. You need a **read model** — the existing `payments` table — updated by a projector:

```java
@Component
@RequiredArgsConstructor
public class PaymentProjector {

    private final PaymentRepository paymentRepository;

    /**
     * Called after each event is persisted to update the read model.
     * In a full CQRS system, this might be an async Kafka consumer
     * that rebuilds the projection from the event stream.
     */
    public Mono<Void> project(PaymentDomainEvent event) {
        return switch (event) {
            case PaymentCreated e -> paymentRepository.save(
                Payment.builder()
                    .amount(e.amount())
                    .currency(e.currency())
                    .status(PaymentStatus.valueOf(e.status()))
                    .paymentDate(e.paymentDate())
                    .createdAt(e.occurredAt())
                    .updatedAt(e.occurredAt())
                    .build()
            ).then();
            case PaymentAmountUpdated e -> paymentRepository.findById(e.aggregateId())
                .flatMap(p -> paymentRepository.save(
                    p.toBuilder().amount(e.newAmount()).updatedAt(e.occurredAt()).build()
                )).then();
            case PaymentStatusChanged e -> paymentRepository.findById(e.aggregateId())
                .flatMap(p -> paymentRepository.save(
                    p.toBuilder().status(PaymentStatus.valueOf(e.newStatus()))
                        .updatedAt(e.occurredAt()).build()
                )).then();
        };
    }
}
```

### 4.3 Pros and Cons

| Pros | Cons |
|---|---|
| **Full audit trail** — every change is recorded forever. Perfect for financial domains. | **Major architectural overhaul** — essentially a rewrite of the persistence layer. |
| **Temporal queries** — "what was the state of payment X at time T?" is trivial. | **Increased complexity** — aggregate replay, projections, eventual consistency between write and read models. |
| **Natural fit for Kafka** — events are the primary data model; publishing is a direct consequence. | **Read model latency** — queries against the projection may be slightly stale (eventual consistency). |
| **Event replay** — rebuild projections, create new read models, feed new downstream systems. | **Aggregate replay cost** — loading an aggregate with many events is slow without snapshotting. |
| **Debugging** — "how did we get here?" is answered by reading the event log. | **Schema evolution** — changing event shapes over time requires careful versioning (e.g., upcasters). |

---

## 5. Recommendation

### The Verdict: Transactional Outbox

For this project, **the Transactional Outbox pattern is the right choice**. Here's why:

#### Why Not Event Sourcing?

Event sourcing is a powerful pattern, but it's **disproportionate** for this use case:

1. **Scope of change**: The project is a straightforward CRUD payment service using R2DBC records. Event sourcing would require rewriting the entire persistence and service layer — the `Payment` entity, `PaymentRepository`, `PaymentService`, and all query logic.

2. **Operational complexity**: You'd need to maintain a read model projection, handle aggregate replay performance (snapshotting), and manage event schema evolution. These are significant ongoing costs.

3. **The requirement is "publish events"**, not "change the source of truth to events". Event sourcing answers a different question than what's being asked.

4. **YAGNI**: Unless there's a concrete need for temporal queries, full audit logs, or aggregate replay, event sourcing adds complexity without proportional benefit.

#### Why Transactional Outbox?

1. **Minimal change**: Add one table (`outbox_events`), one repository, and a poller/publisher component. The existing `Payment` entity, `PaymentRepository`, and query logic remain **completely untouched**.

2. **Solves the real problem**: The dual-write consistency gap (DB succeeds, Kafka fails) is the primary risk. The outbox eliminates it with a single database transaction.

3. **Plays well with R2DBC**: Both writes (payment + outbox) use the same `ConnectionFactory` and participate in the same reactive transaction via `@Transactional`.

4. **Production path is clear**: Start with the poller for local dev and low-throughput production. When throughput demands grow, swap the poller for **Debezium CDC** — a configuration change, not a code change.

5. **At-least-once is achievable**: The outbox guarantees every event is eventually published. Consumers handle idempotency via the `eventId` UUID.

#### When *Would* You Choose Event Sourcing?

Consider event sourcing if any of these become true in the future:
- Regulatory requirements mandate a complete, immutable audit trail of every payment state change.
- You need temporal queries ("what was this payment's status at 3pm yesterday?").
- Multiple bounded contexts need to react to fine-grained payment mutations.
- The domain becomes complex enough to benefit from CQRS (separate read/write models).

At that point, the outbox table can serve as a stepping stone — it already contains the event data that an event store would hold.

### Implementation Priority

1. Add `reactor-kafka` dependency and `KafkaProducerConfig`.
2. Create the `PaymentEvent` record and `PaymentEventPublisher`.
3. Add the `outbox_events` table and `OutboxEventRepository`.
4. Update `PaymentService.createPayment()` and `updatePayment()` to write outbox rows transactionally.
5. Add the `OutboxPoller` to relay events to Kafka.
6. Add the Kafka Docker Compose for local development.
7. Test end-to-end: create a payment → verify the event appears on the Kafka topic.
