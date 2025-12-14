package com.knight.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Knight Platform - Spring Boot Application Entry Point
 *
 * DDD Modular Monolith for Commercial Banking
 */
@SpringBootApplication(scanBasePackages = {
    "com.knight.application",
    "com.knight.domain"
})
public class KnightApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnightApplication.class, args);
    }
}
