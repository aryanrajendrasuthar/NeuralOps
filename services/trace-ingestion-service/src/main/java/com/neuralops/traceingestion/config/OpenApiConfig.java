package com.neuralops.traceingestion.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI traceIngestionOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("NeuralOps Trace Ingestion API")
                        .version("1.0.0")
                        .description("REST API for submitting AI agent trace events to NeuralOps. " +
                                     "Events are validated, published to Kafka, and persisted to PostgreSQL. " +
                                     "All error responses follow RFC 7807 Problem Details format.")
                        .contact(new Contact()
                                .name("NeuralOps Platform Team")
                                .email("platform@neuralops.io")))
                .servers(List.of(
                        new Server().url("http://localhost:8081").description("Local development"),
                        new Server().url("http://localhost:8080").description("Via API Gateway")
                ));
    }
}
