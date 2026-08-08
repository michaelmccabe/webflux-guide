package com.example.reactiveguide.controller;

import com.example.reactiveguide.api.PaymentsApi;
import com.example.reactiveguide.domain.PaymentStatus;
import com.example.reactiveguide.dto.PaymentRequest;
import com.example.reactiveguide.dto.PaymentResponse;
import com.example.reactiveguide.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;

@RestController
@RequiredArgsConstructor
public class PaymentController implements PaymentsApi {

    private final PaymentService paymentService;

    @Override
    public Mono<ResponseEntity<PaymentResponse>> createPayment(
            Mono<PaymentRequest> paymentRequest,
            ServerWebExchange exchange) {
        return paymentRequest
                .flatMap(paymentService::createPayment)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response));
    }

    @Override
    public Mono<ResponseEntity<PaymentResponse>> updatePayment(
            Long id,
            Mono<PaymentRequest> paymentRequest,
            ServerWebExchange exchange) {
        return paymentRequest
                .flatMap(request -> paymentService.updatePayment(id, request))
                .map(ResponseEntity::ok);
    }

    @Override
    public Mono<ResponseEntity<Void>> deletePayment(
            Long id,
            ServerWebExchange exchange) {
        return paymentService.deletePayment(id)
                .then(Mono.just(ResponseEntity.noContent().build()));
    }

    @Override
    public Mono<ResponseEntity<PaymentResponse>> getPaymentById(
            Long id,
            ServerWebExchange exchange) {
        return paymentService.getPaymentById(id)
                .map(ResponseEntity::ok);
    }

    @Override
    public Mono<ResponseEntity<Flux<PaymentResponse>>> findPayments(
            OffsetDateTime startDate,
            OffsetDateTime endDate,
            BigDecimal amount,
            PaymentStatus status,
            ServerWebExchange exchange) {
        Instant start = startDate != null ? startDate.toInstant() : null;
        Instant end = endDate != null ? endDate.toInstant() : null;
        Flux<PaymentResponse> payments = paymentService.findPayments(start, end, amount, status);
        return Mono.just(ResponseEntity.ok(payments));
    }
}
