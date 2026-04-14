package com.carddemo.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the CardDemo Batch Application.
 * Migrated from mainframe COBOL batch programs.
 */
@SpringBootApplication
public class CardDemoBatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(CardDemoBatchApplication.class, args);
    }
}
