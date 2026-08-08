package com.example.reactiveguide.controller;

import com.example.reactiveguide.domain.PaymentStatus;
import com.example.reactiveguide.dto.PaymentRequest;
import com.example.reactiveguide.dto.PaymentResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
@Testcontainers
class PaymentControllerIT {

    static {
        reactor.blockhound.BlockHound.builder()
                .allowBlockingCallsInside("java.io.FileInputStream", "readBytes")
                .allowBlockingCallsInside("java.io.FileInputStream", "read")
                .install();
    }

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void r2dbcProperties(DynamicPropertyRegistry registry) {
        String r2dbcUrl = String.format("r2dbc:postgresql://%s:%d/%s",
                postgres.getHost(),
                postgres.getMappedPort(5432),
                postgres.getDatabaseName());

        registry.add("spring.r2dbc.url", () -> r2dbcUrl);
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
        registry.add("spring.sql.init.mode", () -> "always");
    }

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void testHealthCheck() {
        webTestClient.get().uri("/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void testPrometheusEndpoint() {
        // 1. Trigger HTTP request to generate server metrics
        webTestClient.get().uri("/api/payments")
                .exchange()
                .expectStatus().isOk();

        // 2. Query Prometheus endpoint and assert metrics are present
        webTestClient.get().uri("/prometheus")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> {
                    assertThat(body).contains("jvm_memory_used_bytes");
                    // Verify HTTP request metrics (used to calculate RPS - Requests Per Second)
                    assertThat(body).contains("http_server_requests_seconds_count");
                    assertThat(body).contains("uri=\"/api/payments\"");
                });
    }



    @Test
    void testPaymentCrudLifecycleAndFilters() {
        // 1. Create a payment
        PaymentRequest request = PaymentRequest.builder()
                .amount(new BigDecimal("150.00"))
                .currency("USD")
                .status(PaymentStatus.PENDING)
                .paymentDate(Instant.now().truncatedTo(ChronoUnit.SECONDS))
                .build();

        PaymentResponse created = webTestClient.post().uri("/api/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(PaymentResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(created).isNotNull();
        assertThat(created.getId()).isNotNull();
        assertThat(created.getAmount()).isEqualByComparingTo("150.00");
        assertThat(created.getStatus()).isEqualTo(PaymentStatus.PENDING);

        Long paymentId = created.getId();

        // 2. Get the payment by ID
        webTestClient.get().uri("/api/payments/" + paymentId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.amount").isEqualTo(150.00)
                .jsonPath("$.status").isEqualTo("PENDING");

        // 3. Update the payment
        PaymentRequest updateRequest = PaymentRequest.builder()
                .amount(new BigDecimal("200.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentDate(created.getPaymentDate())
                .build();

        webTestClient.put().uri("/api/payments/" + paymentId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(updateRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.amount").isEqualTo(200.00)
                .jsonPath("$.status").isEqualTo("COMPLETED");

        // 4. Get with filters
        // Exact Amount Filter
        webTestClient.get().uri(uriBuilder -> uriBuilder.path("/api/payments")
                        .queryParam("amount", "200.00")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(PaymentResponse.class)
                .hasSize(1);

        // Status Filter
        webTestClient.get().uri(uriBuilder -> uriBuilder.path("/api/payments")
                        .queryParam("status", "COMPLETED")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(PaymentResponse.class)
                .hasSize(1);

        // Date Range Filter
        Instant start = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant end = Instant.now().plus(1, ChronoUnit.HOURS);
        webTestClient.get().uri(uriBuilder -> uriBuilder.path("/api/payments")
                        .queryParam("startDate", start.toString())
                        .queryParam("endDate", end.toString())
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(PaymentResponse.class)
                .hasSize(1);

        // Non-matching Filter
        webTestClient.get().uri(uriBuilder -> uriBuilder.path("/api/payments")
                        .queryParam("status", "FAILED")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(PaymentResponse.class)
                .hasSize(0);

        // 5. Delete the payment
        webTestClient.delete().uri("/api/payments/" + paymentId)
                .exchange()
                .expectStatus().isNoContent();

        // Verify delete
        webTestClient.get().uri("/api/payments/" + paymentId)
                .exchange()
                .expectStatus().isNotFound();
    }
}
