package com.example.reactiveguide.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Payment Service API")
                        .version("1.0.0")
                        .description("High-performance, reactive Web API for managing payments using Spring Boot WebFlux, R2DBC, and PostgreSQL.")
                        .contact(new Contact()
                                .name("API Support")
                                .email("support@example.com")));
    }
}
