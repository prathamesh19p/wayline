package com.wayline.payment.application;

import com.wayline.payment.domain.*;
import com.wayline.payment.infrastructure.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Core payment service managing payment lifecycle.
 * Responsible for:
 * - Payment creation and idempotency
 * - State machine validation
 * - Payment attempt tracking
 * - State history recording
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentStateHistoryRepository stateHistoryRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final PaymentWebhookRepository webhookRepository;

    /**
     * Create a new payment with idempotency support.
     *
     * @param merchantId Merchant identifier
     * @param idempotencyKey Unique request key
     * @param request Payment request details
     * @return Created or existing payment
     */
    @Transactional
    public Payment createPayment(String merchantId, String idempotencyKey, CreatePaymentRequest request) {
        log.info("Creating payment for merchant {} with idempotency key {}", merchantId, idempotencyKey);

        // Check idempotency
        Optional<IdempotencyRecord> existingRecord = idempotencyRecordRepository
            .findByMerchantIdAndIdempotencyKey(merchantId, idempotencyKey);

        if (existingRecord.isPresent()) {
            IdempotencyRecord record = existingRecord.get();
            String requestHash = hashRequest(request);
            if (!requestHash.equals(record.getRequestHash())) {
                throw new IllegalArgumentException("Idempotency key was already used with a different request");
            }
            log.info("Idempotency key already processed: payment_id={}", record.getPaymentId());
            return paymentRepository.findById(record.getPaymentId())
                .orElseThrow(() -> new IllegalStateException("Payment not found for idempotency record"));
        }

        // Validate request
        validatePaymentRequest(request);

        // Create payment
        Payment payment = Payment.builder()
            .merchantId(merchantId)
            .idempotencyKey(idempotencyKey)
            .amount(request.getAmount())
            .currency(request.getCurrency())
            .paymentMethod(request.getPaymentMethod())
            .status(PaymentStatus.CREATED)
            .version(0L)
            .build();

        Payment savedPayment = paymentRepository.save(payment);
        log.info("Payment created: id={} status={}", savedPayment.getId(), savedPayment.getStatus());

        // Record idempotency
        IdempotencyRecord idempotencyRecord = IdempotencyRecord.builder()
            .merchantId(merchantId)
            .idempotencyKey(idempotencyKey)
            .requestHash(hashRequest(request))
            .paymentId(savedPayment.getId())
            .responseStatus("CREATED")
            .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
            .build();

        idempotencyRecordRepository.save(idempotencyRecord);

        // Record initial state
        recordStateTransition(savedPayment.getId(), null, PaymentStatus.CREATED, "Initial creation", "API");

        return savedPayment;
    }

    /**
     * Get payment by ID.
     */
    public Optional<Payment> getPayment(Long paymentId) {
        return paymentRepository.findById(paymentId);
    }

    public Optional<Payment> getPaymentForMerchant(Long paymentId, String merchantId) {
        return paymentRepository.findById(paymentId)
            .filter(payment -> payment.getMerchantId().equals(merchantId));
    }

    /**
     * Transition payment to new status.
     *
     * @param paymentId Payment ID
     * @param newStatus Target status
     * @param reason Reason for transition
     * @param source Source of transition
     * @return Updated payment
     */
    @Transactional
    public Payment transitionPaymentStatus(Long paymentId, PaymentStatus newStatus, String reason, String source) {
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));

        PaymentStatus oldStatus = payment.getStatus();

        if (!payment.canTransitionTo(newStatus)) {
            log.error("Invalid state transition from {} to {} for payment {}", oldStatus, newStatus, paymentId);
            throw new IllegalStateException(
                String.format("Cannot transition from %s to %s", oldStatus, newStatus)
            );
        }

        payment.transitionTo(newStatus);
        payment = paymentRepository.save(payment);

        recordStateTransition(paymentId, oldStatus, newStatus, reason, source);

        log.info("Payment transitioned: id={} from {} to {}", paymentId, oldStatus, newStatus);
        return payment;
    }

    @Transactional
    public Payment startProcessing(Long paymentId, String provider) {
        Payment payment = transitionPaymentStatus(
            paymentId,
            PaymentStatus.PROCESSING,
            "Selected provider: " + provider,
            "ORCHESTRATOR"
        );
        payment.setSelectedProvider(provider);
        return paymentRepository.save(payment);
    }

    /**
     * Record a payment attempt.
     */
    @Transactional
    public PaymentAttempt recordAttempt(Long paymentId, String provider, Integer attemptNumber) {
        PaymentAttempt attempt = PaymentAttempt.builder()
            .paymentId(paymentId)
            .provider(provider)
            .attemptNumber(attemptNumber)
            .status("INITIATED")
            .requestTime(Instant.now())
            .build();

        return paymentAttemptRepository.save(attempt);
    }

    /**
     * Update attempt with result.
     */
    @Transactional
    public PaymentAttempt updateAttempt(Long attemptId, String status, String providerPaymentId, 
                                        String failureCode, String failureMessage) {
        PaymentAttempt attempt = paymentAttemptRepository.findById(attemptId)
            .orElseThrow(() -> new IllegalArgumentException("Attempt not found: " + attemptId));

        attempt.setStatus(status);
        attempt.setProviderPaymentId(providerPaymentId);
        attempt.setFailureCode(failureCode);
        attempt.setFailureMessage(failureMessage);
        attempt.setResponseTime(Instant.now());

        return paymentAttemptRepository.save(attempt);
    }

    /**
     * Get all attempts for a payment.
     */
    public List<PaymentAttempt> getPaymentAttempts(Long paymentId) {
        return paymentAttemptRepository.findByPaymentIdOrderByAttemptNumberAsc(paymentId);
    }

    /**
     * Get payment state history.
     */
    public List<PaymentStateHistory> getStateHistory(Long paymentId) {
        return stateHistoryRepository.findByPaymentIdOrderByCreatedAtAsc(paymentId);
    }

    /**
     * Record webhook event.
     */
    @Transactional
    public PaymentWebhook recordWebhook(String provider, String providerEventId, Long paymentId,
                                       String eventType, String payload) {
        PaymentWebhook webhook = PaymentWebhook.builder()
            .provider(provider)
            .providerEventId(providerEventId)
            .paymentId(paymentId)
            .eventType(eventType)
            .payload(payload)
            .status("RECEIVED")
            .build();

        return webhookRepository.save(webhook);
    }

    /**
     * Mark webhook as processed.
     */
    @Transactional
    public PaymentWebhook markWebhookProcessed(Long webhookId) {
        PaymentWebhook webhook = webhookRepository.findById(webhookId)
            .orElseThrow(() -> new IllegalArgumentException("Webhook not found: " + webhookId));

        webhook.setProcessedAt(Instant.now());
        webhook.setStatus("PROCESSED");

        return webhookRepository.save(webhook);
    }

    @Transactional
    public boolean processWebhook(String provider, String providerEventId, Long paymentId,
                                  String eventType, String payload, PaymentStatus targetStatus) {
        if (webhookRepository.findByProviderAndProviderEventId(provider, providerEventId).isPresent()) {
            return false;
        }

        PaymentWebhook webhook = recordWebhook(provider, providerEventId, paymentId, eventType, payload);
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));

        if (payment.getStatus() != targetStatus && payment.canTransitionTo(targetStatus)) {
            transitionPaymentStatus(paymentId, targetStatus, "Provider webhook: " + eventType, "WEBHOOK");
        } else if (payment.getStatus() != targetStatus && !payment.isTerminal()) {
            throw new IllegalStateException(
                "Webhook status " + targetStatus + " cannot transition payment from " + payment.getStatus()
            );
        }

        webhook.setProcessedAt(Instant.now());
        webhook.setStatus("PROCESSED");
        webhookRepository.save(webhook);
        return true;
    }

    /**
     * Validate payment request.
     */
    private void validatePaymentRequest(CreatePaymentRequest request) {
        if (request.getAmount() == null || request.getAmount() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (request.getCurrency() == null || request.getCurrency().isBlank()) {
            throw new IllegalArgumentException("Currency is required");
        }
        if (request.getPaymentMethod() == null || request.getPaymentMethod().isBlank()) {
            throw new IllegalArgumentException("Payment method is required");
        }
    }

    /**
     * Record state transition in history.
     */
    private void recordStateTransition(Long paymentId, PaymentStatus fromState, PaymentStatus toState,
                                      String reason, String source) {
        PaymentStateHistory history = PaymentStateHistory.builder()
            .paymentId(paymentId)
            .fromState(fromState != null ? fromState.toString() : null)
            .toState(toState.toString())
            .reason(reason)
            .source(source)
            .build();

        stateHistoryRepository.save(history);
    }

    /**
     * Hash request for idempotency validation.
     */
    private String hashRequest(CreatePaymentRequest request) {
        try {
            String combined = request.getAmount() + "|" + request.getCurrency() + "|" + request.getPaymentMethod();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(combined.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash request", e);
        }
    }
}
