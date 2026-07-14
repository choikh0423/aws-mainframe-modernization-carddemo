package com.carddemo.transaction;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point for the Transaction Management stream (CT00/CT01/CT02).
 * Wave A lays the foundation (entities, repositories, date utility, schema/seed,
 * app + React shells); the per-screen APIs arrive in later waves.
 */
@SpringBootApplication
public class TransactionManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransactionManagementApplication.class, args);
    }
}
