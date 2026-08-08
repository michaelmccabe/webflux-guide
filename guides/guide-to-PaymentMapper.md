# Guide to PaymentMapper Interface

The `PaymentMapper` interface is a MapStruct-based mapper that handles compile-time generation of mapping code between the `Payment` record and DTOs.

## Definition
Located at: [PaymentMapper.java](file:///Users/michaelmccabe/local-code/java/reactive-guide/src/main/java/com/example/reactiveguide/mapper/PaymentMapper.java)

```java
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
```

## Description and Purpose
- **MapStruct Code Generation**: MapStruct automatically generates the implementation class (e.g. `PaymentMapperImpl`) at compile time. Because `Payment` is a record, MapStruct uses the record's builder to construct new instances when mapping.
- **Spring Integration**: Annotated with `@Mapper(componentModel = "spring")` so that the generated implementation is registered as a Spring bean and can be injected using constructor injection.
- **Ignored Targets**: When converting `PaymentRequest` to the `Payment` record, metadata fields like `id`, `createdAt`, and `updatedAt` are ignored to prevent request payloads from setting or overwriting database-owned values.
- **Immutable Updates**: Since records are immutable, in-place updates (using `@MappingTarget`) are not supported. Updates are handled at the service layer by creating a new record instance using `toBuilder()`.

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
