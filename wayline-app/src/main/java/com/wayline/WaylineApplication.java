package com.wayline;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Wayline Payment Orchestration and Reconciliation Platform
 *
 * A production-style payment orchestration and reconciliation platform
 * demonstrating strong backend engineering practices including:
 * - Idempotent payment creation
 * - Smart provider routing and resilience
 * - Double-entry ledger for financial correctness
 * - Webhook processing and state management
 * - Settlement and reconciliation
 * - Complete operational observability
 */
@SpringBootApplication
@EnableScheduling
public class WaylineApplication {

    public static void main(String[] args) {
        SpringApplication.run(WaylineApplication.class, args);
    }
}
