# Guide to PaymentMapper Interface

The `PaymentMapper` interface is a MapStruct-based mapper that handles compile-time generation of mapping code between `Payment` entities and DTOs.

## Definition
Located at: [PaymentMapper.java](file:///Users/michaelmccabe/local-code/java/reactive-guide/src/main/java/com/example/reactiveguide/mapper/PaymentMapper.java)

```java
package com.example.reactiveguide.mapper;

import com.example.reactiveguide.domain.Payment;
import com.example.reactiveguide.dto.PaymentRequest;
import com.example.reactiveguide.dto.PaymentResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface PaymentMapper {
    PaymentResponse toResponse(Payment payment);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Payment toEntity(PaymentRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromRequest(PaymentRequest request, @MappingTarget Payment payment);
}
```

## Description and Purpose
- **MapStruct Code Generation**: MapStruct automatically generates the implementation class (e.g. `PaymentMapperImpl`) at compile time. This avoids manual property copies and maintains high performance.
- **Spring Integration**: Annotated with `@Mapper(componentModel = "spring")` so that the generated implementation is registered as a Spring bean and can be injected using `@Autowired` or constructor injection.
- **Ignored Targets**: During conversion from `PaymentRequest` to `Payment`, metadata fields like `id`, `createdAt`, and `updatedAt` are ignored to prevent request payloads from overwriting database-owned values.
- **In-place Updates**: The `updateEntityFromRequest` method modifies an existing target `Payment` instance using values from `PaymentRequest`.

## Usage Example
```java
@Autowired
private PaymentMapper mapper;

public Mono<PaymentResponse> saveRequest(PaymentRequest request) {
    Payment entity = mapper.toEntity(request);
    return repository.save(entity)
            .map(mapper::toResponse);
}
```
