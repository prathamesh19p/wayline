package com.wayline.app.config;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Metrics configuration.
 * Defines custom metrics for payment processing pipeline.
 */
@Configuration
@Slf4j
public class MetricsConfiguration {

    /**
     * Initialize custom metrics.
     */
    @Component
    @RequiredArgsConstructor
    public static class MetricsInitializer {

        private final MeterRegistry meterRegistry;

        @PostConstruct
        public void initializeMetrics() {
            // Payment processing metrics
            meterRegistry.counter("payment.created.count");
            meterRegistry.counter("payment.succeeded.count");
            meterRegistry.counter("payment.failed.count");
            meterRegistry.counter("payment.unknown.count");
            meterRegistry.counter("payment.cancelled.count");

            // Provider metrics
            meterRegistry.counter("provider.request.count");
            meterRegistry.counter("provider.success.count");
            meterRegistry.counter("provider.failure.count");
            meterRegistry.counter("provider.timeout.count");

            // Idempotency metrics
            meterRegistry.counter("idempotency.cache.hits");
            meterRegistry.counter("idempotency.cache.misses");

            // Kafka metrics
            meterRegistry.counter("kafka.message.sent.count");
            meterRegistry.counter("kafka.message.received.count");
            meterRegistry.counter("kafka.message.dead.letter.count");

            // Outbox metrics
            meterRegistry.gauge("outbox.pending.count", 0);
            meterRegistry.counter("outbox.published.count");
            meterRegistry.counter("outbox.failed.count");

            // Ledger metrics
            meterRegistry.counter("ledger.transaction.created.count");
            meterRegistry.counter("ledger.entry.created.count");

            // Settlement metrics
            meterRegistry.counter("settlement.imported.count");
            meterRegistry.counter("settlement.completed.count");

            // Reconciliation metrics
            meterRegistry.gauge("reconciliation.mismatch.count", 0);
            meterRegistry.counter("reconciliation.matched.count");

            // Webhook metrics
            meterRegistry.counter("webhook.received.count");
            meterRegistry.counter("webhook.processed.count");
            meterRegistry.counter("webhook.failed.count");

            log.info("Custom metrics initialized");
        }
    }
}
