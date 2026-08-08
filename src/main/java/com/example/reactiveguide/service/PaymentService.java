package com.example.reactiveguide.service;

import com.example.reactiveguide.domain.Payment;
import com.example.reactiveguide.domain.PaymentStatus;
import com.example.reactiveguide.dto.PaymentRequest;
import com.example.reactiveguide.dto.PaymentResponse;
import com.example.reactiveguide.mapper.PaymentMapper;
import com.example.reactiveguide.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final R2dbcEntityTemplate entityTemplate;

    public Mono<PaymentResponse> createPayment(PaymentRequest request) {
        log.info("Creating new payment: {}", request);
        Payment payment = paymentMapper.toEntity(request);
        Instant now = Instant.now();
        payment.setCreatedAt(now);
        payment.setUpdatedAt(now);
        if (payment.getPaymentDate() == null) {
            payment.setPaymentDate(now);
        }
        return paymentRepository.save(payment)
                .map(paymentMapper::toResponse)
                .doOnSuccess(saved -> log.info("Created payment successfully with ID: {}", saved.getId()));
    }

    public Mono<PaymentResponse> updatePayment(Long id, PaymentRequest request) {
        log.info("Updating payment ID: {} with: {}", id, request);
        return paymentRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found with ID: " + id)))
                .flatMap(existingPayment -> {
                    paymentMapper.updateEntityFromRequest(request, existingPayment);
                    existingPayment.setUpdatedAt(Instant.now());
                    return paymentRepository.save(existingPayment);
                })
                .map(paymentMapper::toResponse)
                .doOnSuccess(updated -> log.info("Updated payment ID: {} successfully", id));
    }

    public Mono<Void> deletePayment(Long id) {
        log.info("Deleting payment ID: {}", id);
        return paymentRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found with ID: " + id)))
                .flatMap(paymentRepository::delete)
                .doOnSuccess(v -> log.info("Deleted payment ID: {} successfully", id));
    }

    public Mono<PaymentResponse> getPaymentById(Long id) {
        log.info("Fetching payment ID: {}", id);
        return paymentRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found with ID: " + id)))
                .map(paymentMapper::toResponse);
    }

    public Flux<PaymentResponse> findPayments(Instant startDate, Instant endDate, BigDecimal amount, PaymentStatus status) {
        log.info("Searching payments with criteria -> startDate: {}, endDate: {}, amount: {}, status: {}",
                startDate, endDate, amount, status);

        List<Criteria> criteriaList = new ArrayList<>();
        if (startDate != null) {
            criteriaList.add(Criteria.where("payment_date").greaterThanOrEquals(startDate));
        }
        if (endDate != null) {
            criteriaList.add(Criteria.where("payment_date").lessThanOrEquals(endDate));
        }
        if (amount != null) {
            criteriaList.add(Criteria.where("amount").is(amount));
        }
        if (status != null) {
            criteriaList.add(Criteria.where("status").is(status));
        }

        Query query;
        if (criteriaList.isEmpty()) {
            query = Query.empty();
        } else {
            Criteria combinedCriteria = criteriaList.get(0);
            for (int i = 1; i < criteriaList.size(); i++) {
                combinedCriteria = combinedCriteria.and(criteriaList.get(i));
            }
            query = Query.query(combinedCriteria);
        }

        return entityTemplate.select(Payment.class)
                .matching(query)
                .all()
                .map(paymentMapper::toResponse);
    }
}
