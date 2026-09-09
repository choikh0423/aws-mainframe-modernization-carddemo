package com.carddemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Single entry point for the consolidated CardDemo application (decision D-1).
 * Every migrated stream lives under {@code com.carddemo.<stream>}, shared
 * services under {@code com.carddemo.common}, and Spring Batch jobs under
 * {@code com.carddemo.batch}; all of them are component-scanned from here.
 */
@SpringBootApplication
public class CardDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(CardDemoApplication.class, args);
    }
}
