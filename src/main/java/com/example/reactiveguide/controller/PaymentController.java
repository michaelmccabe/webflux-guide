package com.example.reactiveguide.controller;

import com.example.reactiveguide.domain.PaymentStatus;
import com.example.reactiveguide.dto.PaymentRequest;
import com.example.reactiveguide.dto.PaymentResponse;
import com.example.reactiveguide.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<PaymentResponse> createPayment(@RequestBody PaymentRequest request) {
        return paymentService.createPayment(request);
    }

    @PutMapping("/{id}")
    public Mono<PaymentResponse> updatePayment(@PathVariable Long id, @RequestBody PaymentRequest request) {
        return paymentService.updatePayment(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deletePayment(@PathVariable Long id) {
        return paymentService.deletePayment(id);
    }

    @GetMapping("/{id}")
    public Mono<PaymentResponse> getPaymentById(@PathVariable Long id) {
        return paymentService.getPaymentById(id);
    }

    @GetMapping
    public Flux<PaymentResponse> findPayments(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(required = false) BigDecimal amount,
            @RequestParam(required = false) PaymentStatus status) {
        return paymentService.findPayments(startDate, endDate, amount, status);
    }
}
