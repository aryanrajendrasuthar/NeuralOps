package com.neuralops.traceingestion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class TraceIngestionApplication {

    public static void main(String[] args) {
        SpringApplication.run(TraceIngestionApplication.class, args);
    }
}
