package com.wayline.payment.application;

import com.wayline.common.outbox.OutboxEvent;
import com.wayline.common.outbox.OutboxEventRepository;
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

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentStateHistoryRepository stateHistoryRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final PaymentWebhookRepository webhookRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final PaymentLedgerPoster ledgerPoster;

    @Transactional
    public Payment createPayment(String merchantId, String idempotencyKey, CreatePaymentRequest request) {
        log.info("Creating payment for merchant {} with idempotency key {}", merchantId, idempotencyKey);

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

        validatePaymentRequest(request);

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

        IdempotencyRecord idempotencyRecord = IdempotencyRecord.builder()
            .merchantId(merchantId)
            .idempotencyKey(idempotencyKey)
            .requestHash(hashRequest(request))
            .paymentId(savedPayment.getId())
            .responseStatus("CREATED")
            .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
            .build();

        idempotencyRecordRepository.save(idempotencyRecord);

        recordStateTransition(savedPayment.getId(), null, PaymentStatus.CREATED, "Initial creation", "API");

        return savedPayment;
    }

    public Optional<Payment> getPayment(Long paymentId) {
        return paymentRepository.findById(paymentId);
    }

    public Optional<Payment> getPaymentForMerchant(Long paymentId, String merchantId) {
        return paymentRepository.findById(paymentId)
            .filter(payment -> payment.getMerchantId().equals(merchantId));
    }

    @Transactional
    public Payment transitionPaymentStatus(Long paymentId, PaymentStatus newStatus, String reason, String source) {
        return transitionPaymentStatus(paymentId, newStatus, reason, source, null);
    }

    /**
     * Persists the state change, its history row, the ledger effect and the outbox event in one
     * transaction, so an event can never be published for a state that was rolled back, nor a
     * payment be marked captured without the matching ledger entries.
     */
    @Transactional
    public Payment transitionPaymentStatus(Long paymentId, PaymentStatus newStatus, String reason, String source,
                                           OutboxEvent outboxEvent) {
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));

        PaymentStatus oldStatus = payment.getStatus();

        if (!payment.canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                String.format("Cannot transition payment %d from %s to %s", paymentId, oldStatus, newStatus)
            );
        }

        payment.transitionTo(newStatus);
        payment = paymentRepository.save(payment);

        recordStateTransition(paymentId, oldStatus, newStatus, reason, source);

        if (newStatus == PaymentStatus.SUCCESS) {
            ledgerPoster.postCapture(payment);
        }

        if (outboxEvent != null) {
            outboxEventRepository.save(outboxEvent);
        }

        log.info("Payment transitioned: id={} from {} to {}", paymentId, oldStatus, newStatus);
        return payment;
    }

    @Transactional
    public Payment startProcessing(Long paymentId, String provider) {
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));

        PaymentStatus oldStatus = payment.getStatus();
        if (!payment.canTransitionTo(PaymentStatus.PROCESSING)) {
            throw new IllegalStateException(
                String.format("Cannot transition payment %d from %s to PROCESSING", paymentId, oldStatus)
            );
        }

        payment.transitionTo(PaymentStatus.PROCESSING);
        payment.setSelectedProvider(provider);
        payment = paymentRepository.save(payment);

        recordStateTransition(paymentId, oldStatus, PaymentStatus.PROCESSING,
            "Selected provider: " + provider, "ORCHESTRATOR");
        return payment;
    }

    @Transactional
    public Payment selectProviderForProcessingAttempt(Long paymentId, String provider) {
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));
        if (payment.getStatus() != PaymentStatus.PROCESSING) {
            throw new IllegalStateException("Payment must be PROCESSING before selecting another provider");
        }
        payment.setSelectedProvider(provider);
        return paymentRepository.save(payment);
    }

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

    public List<PaymentAttempt> getPaymentAttempts(Long paymentId) {
        return paymentAttemptRepository.findByPaymentIdOrderByAttemptNumberAsc(paymentId);
    }

    public List<PaymentStateHistory> getStateHistory(Long paymentId) {
        return stateHistoryRepository.findByPaymentIdOrderByCreatedAtAsc(paymentId);
    }

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
     * Validates against the same fields hashed for idempotency, so a replayed key with a tampered
     * amount is rejected rather than silently returning the original payment.
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
