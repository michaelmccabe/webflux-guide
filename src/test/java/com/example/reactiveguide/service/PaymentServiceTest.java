package com.example.reactiveguide.service;

import com.example.reactiveguide.domain.Payment;
import com.example.reactiveguide.domain.PaymentStatus;
import com.example.reactiveguide.dto.PaymentRequest;
import com.example.reactiveguide.dto.PaymentResponse;
import com.example.reactiveguide.mapper.PaymentMapper;
import com.example.reactiveguide.mapper.PaymentMapperImpl;
import com.example.reactiveguide.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private R2dbcEntityTemplate entityTemplate;

    @Mock
    private org.springframework.data.r2dbc.core.ReactiveSelectOperation.ReactiveSelect<Payment> selectSpec;

    @Mock
    private org.springframework.data.r2dbc.core.ReactiveSelectOperation.TerminatingSelect<Payment> terminatingSelectSpec;


    private PaymentMapper paymentMapper;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        // Instantiate real MapStruct implementation to cover mapping code
        paymentMapper = new PaymentMapperImpl();
        paymentService = new PaymentService(paymentRepository, paymentMapper, entityTemplate);
    }

    @Test
    void testCreatePayment_WithNullDate() {
        PaymentRequest request = PaymentRequest.builder()
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .status(PaymentStatus.PENDING)
                .build();

        Payment savedEntity = Payment.builder()
                .id(1L)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .status(PaymentStatus.PENDING)
                .paymentDate(Instant.now())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(paymentRepository.save(any(Payment.class))).thenReturn(Mono.just(savedEntity));

        Mono<PaymentResponse> result = paymentService.createPayment(request);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getId()).isEqualTo(1L);
                    assertThat(response.getAmount()).isEqualByComparingTo("100.00");
                    assertThat(response.getCurrency()).isEqualTo("USD");
                    assertThat(response.getStatus()).isEqualTo(PaymentStatus.PENDING);
                    assertThat(response.getPaymentDate()).isNotNull();
                })
                .verifyComplete();

        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void testCreatePayment_WithNonNullDate() {
        Instant date = Instant.parse("2026-08-08T00:00:00Z");
        PaymentRequest request = PaymentRequest.builder()
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .status(PaymentStatus.PENDING)
                .paymentDate(date)
                .build();

        Payment savedEntity = Payment.builder()
                .id(1L)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .status(PaymentStatus.PENDING)
                .paymentDate(date)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(paymentRepository.save(any(Payment.class))).thenReturn(Mono.just(savedEntity));

        Mono<PaymentResponse> result = paymentService.createPayment(request);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getPaymentDate()).isEqualTo(date);
                })
                .verifyComplete();
    }

    @Test
    void testUpdatePayment_Success() {
        Long id = 1L;
        PaymentRequest request = PaymentRequest.builder()
                .amount(new BigDecimal("200.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .build();

        Payment existing = Payment.builder()
                .id(id)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .status(PaymentStatus.PENDING)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Payment updated = Payment.builder()
                .id(id)
                .amount(new BigDecimal("200.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .createdAt(existing.getCreatedAt())
                .updatedAt(Instant.now())
                .build();

        when(paymentRepository.findById(id)).thenReturn(Mono.just(existing));
        when(paymentRepository.save(any(Payment.class))).thenReturn(Mono.just(updated));

        Mono<PaymentResponse> result = paymentService.updatePayment(id, request);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getAmount()).isEqualByComparingTo("200.00");
                    assertThat(response.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
                })
                .verifyComplete();
    }

    @Test
    void testUpdatePayment_NotFound() {
        Long id = 99L;
        PaymentRequest request = PaymentRequest.builder().build();

        when(paymentRepository.findById(id)).thenReturn(Mono.empty());

        Mono<PaymentResponse> result = paymentService.updatePayment(id, request);

        StepVerifier.create(result)
                .expectErrorSatisfies(throwable -> {
                    assertThat(throwable).isInstanceOf(ResponseStatusException.class);
                    ResponseStatusException ex = (ResponseStatusException) throwable;
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                })
                .verify();
    }

    @Test
    void testDeletePayment_Success() {
        Long id = 1L;
        Payment existing = new Payment();

        when(paymentRepository.findById(id)).thenReturn(Mono.just(existing));
        when(paymentRepository.delete(existing)).thenReturn(Mono.empty());

        Mono<Void> result = paymentService.deletePayment(id);

        StepVerifier.create(result).verifyComplete();
    }

    @Test
    void testDeletePayment_NotFound() {
        Long id = 99L;
        when(paymentRepository.findById(id)).thenReturn(Mono.empty());

        Mono<Void> result = paymentService.deletePayment(id);

        StepVerifier.create(result)
                .expectError(ResponseStatusException.class)
                .verify();
    }

    @Test
    void testGetPaymentById_Success() {
        Long id = 1L;
        Payment existing = Payment.builder()
                .id(id)
                .amount(BigDecimal.TEN)
                .build();

        when(paymentRepository.findById(id)).thenReturn(Mono.just(existing));

        Mono<PaymentResponse> result = paymentService.getPaymentById(id);

        StepVerifier.create(result)
                .assertNext(response -> assertThat(response.getId()).isEqualTo(id))
                .verifyComplete();
    }

    @Test
    void testGetPaymentById_NotFound() {
        Long id = 99L;
        when(paymentRepository.findById(id)).thenReturn(Mono.empty());

        Mono<PaymentResponse> result = paymentService.getPaymentById(id);

        StepVerifier.create(result)
                .expectError(ResponseStatusException.class)
                .verify();
    }

    @Test
    void testFindPayments_EmptyCriteria() {
        Payment p1 = Payment.builder().id(1L).build();
        Payment p2 = Payment.builder().id(2L).build();

        when(entityTemplate.select(Payment.class)).thenReturn(selectSpec);
        when(selectSpec.matching(any(Query.class))).thenReturn(terminatingSelectSpec);
        when(terminatingSelectSpec.all()).thenReturn(Flux.just(p1, p2));

        Flux<PaymentResponse> result = paymentService.findPayments(null, null, null, null);

        StepVerifier.create(result)
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    void testFindPayments_WithAllCriteria() {
        Instant start = Instant.now().minusSeconds(3600);
        Instant end = Instant.now();
        BigDecimal amount = new BigDecimal("100.00");
        PaymentStatus status = PaymentStatus.COMPLETED;

        Payment p1 = Payment.builder().id(1L).build();

        when(entityTemplate.select(Payment.class)).thenReturn(selectSpec);
        when(selectSpec.matching(any(Query.class))).thenReturn(terminatingSelectSpec);
        when(terminatingSelectSpec.all()).thenReturn(Flux.just(p1));

        Flux<PaymentResponse> result = paymentService.findPayments(start, end, amount, status);

        StepVerifier.create(result)
                .expectNextCount(1)
                .verifyComplete();
    }
}
