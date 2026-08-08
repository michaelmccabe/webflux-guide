package com.example.reactiveguide.mapper;

import com.example.reactiveguide.domain.Payment;
import com.example.reactiveguide.dto.PaymentRequest;
import com.example.reactiveguide.dto.PaymentResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PaymentMapper {
    PaymentResponse toResponse(Payment payment);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Payment toEntity(PaymentRequest request);
}
