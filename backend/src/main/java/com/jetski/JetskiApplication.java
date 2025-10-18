package com.jetski;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Jetski SaaS API - Multi-tenant jetski rental management system
 *
 * Main application class for Spring Boot
 *
 * @author Jetski Team
 * @version 0.1.0
 * @since 2025-01-15
 */
@SpringBootApplication
@EnableCaching
@EnableJpaAuditing
public class JetskiApplication {

    public static void main(String[] args) {
        SpringApplication.run(JetskiApplication.class, args);
    }
}
