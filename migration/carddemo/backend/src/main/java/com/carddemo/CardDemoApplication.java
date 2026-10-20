package com.carddemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Single entry point for the consolidated CardDemo application (decision D-1).
 * Every migrated stream lives under {@code com.carddemo.<stream>}, shared
 * services under {@code com.carddemo.common}, and Spring Batch jobs under
 * {@code com.carddemo.batch}; all of them are component-scanned from here.
 *
 * <p>Started without a web server (the batch launch), the process exits with
 * the batch condition code the {@code ExitCodeGenerator} beans report.
 */
@SpringBootApplication
public class CardDemoApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(CardDemoApplication.class, args);
        if (!(context instanceof WebServerApplicationContext)) {
            System.exit(SpringApplication.exit(context));
        }
    }
}
