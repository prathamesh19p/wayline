package com.wayline.ledger.application;

import com.wayline.common.outbox.OutboxEvent;
import com.wayline.common.outbox.OutboxEventRepository;
import com.wayline.ledger.domain.*;
import com.wayline.ledger.infrastructure.*;
import com.wayline.ledger.domain.LedgerEntry.EntryType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Service for managing immutable double-entry ledger.
 *
 * Principles:
 * - Every transaction must have equal debits and credits
 * - Entries are immutable (never update or delete)
 * - Corrections use compensating transactions
 * - Financial balance must always be correct
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LedgerService {

    private final AccountRepository accountRepository;
    private final LedgerTransactionRepository transactionRepository;
    private final LedgerEntryRepository entryRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * Create or get account.
     */
    @Transactional
    public Account getOrCreateAccount(String accountType, String ownerId, String currency) {
        Optional<Account> existing = accountRepository
            .findByAccountTypeAndOwnerIdAndCurrency(accountType, ownerId, currency);

        if (existing.isPresent()) {
            return existing.get();
        }

        Account account = Account.builder()
            .accountType(accountType)
            .ownerId(ownerId)
            .currency(currency)
            .status("ACTIVE")
            .build();

        return accountRepository.save(account);
    }

    /**
     * Create double-entry transaction.
     *
     * Example:
     * Payment: ₹1,000
     * Debit: Customer/Clearing → ₹1,000
     * Credit: Merchant Payable → ₹1,000
     *
     * @param transactionType Type of transaction
     * @param entries Debit/credit entries
     * @param paymentId Associated payment ID
     * @return Created transaction
     * @throws IllegalStateException if debits ≠ credits
     */
    @Transactional
    public LedgerTransaction createTransaction(String transactionType, List<EntryData> entries, Long paymentId) {
        log.info("Creating ledger transaction: type={} entries={}", transactionType, entries.size());

        // Validate entries balance
        validateBalance(entries);

        // Create transaction
        LedgerTransaction transaction = LedgerTransaction.builder()
            .transactionType(transactionType)
            .paymentId(paymentId)
            .build();

        LedgerTransaction savedTransaction = transactionRepository.save(transaction);

        // Create entries
        for (EntryData entry : entries) {
            Account account = getOrCreateAccount(
                entry.accountType,
                entry.ownerId,
                entry.currency
            );

            LedgerEntry ledgerEntry = LedgerEntry.builder()
                .ledgerTransactionId(savedTransaction.getId())
                .accountId(account.getId())
                .entryType(entry.entryType)
                .amount(entry.amount)
                .currency(entry.currency)
                .build();

            entryRepository.save(ledgerEntry);

            log.debug("Created ledger entry: transaction={} account={} type={} amount={}",
                savedTransaction.getId(), account.getId(), entry.entryType, entry.amount);
        }

        // Create outbox event
        createOutboxEvent(savedTransaction);

        log.info("Ledger transaction created: id={}", savedTransaction.getId());
        return savedTransaction;
    }

    /**
     * Get transaction with all entries.
     */
    public Optional<LedgerTransaction> getTransaction(Long transactionId) {
        return transactionRepository.findById(transactionId);
    }

    /**
     * Get all transactions for payment.
     */
    public List<LedgerTransaction> getPaymentTransactions(Long paymentId) {
        return transactionRepository.findByPaymentIdOrderByCreatedAtAsc(paymentId);
    }

    /**
     * Get entries for transaction.
     */
    public List<LedgerEntry> getTransactionEntries(Long transactionId) {
        return entryRepository.findByLedgerTransactionIdOrderByCreatedAtAsc(transactionId);
    }

    /**
     * Validate transaction has equal debits and credits.
     */
    private void validateBalance(List<EntryData> entries) {
        Map<String, Long> balanceByType = new HashMap<>();

        for (EntryData entry : entries) {
            String key = entry.currency;
            long current = balanceByType.getOrDefault(key, 0L);

            if (entry.entryType == EntryType.DEBIT) {
                balanceByType.put(key, current + entry.amount);
            } else {
                balanceByType.put(key, current - entry.amount);
            }
        }

        // All balances must be zero
        for (Long balance : balanceByType.values()) {
            if (balance != 0) {
                throw new IllegalStateException(
                    "Transaction not balanced. Total debits ≠ total credits"
                );
            }
        }

        log.debug("Transaction balance validated: OK");
    }

    /**
     * Create outbox event for ledger transaction.
     */
    private void createOutboxEvent(LedgerTransaction transaction) {
        try {
            String payload = objectMapper.writeValueAsString(transaction);

            OutboxEvent event = OutboxEvent.builder()
                .aggregateType("LEDGER")
                .aggregateId(transaction.getId().toString())
                .eventType("LedgerTransactionCreated")
                .payload(payload)
                .status("PENDING")
                .build();

            outboxEventRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to create outbox event for ledger transaction", e);
            throw new RuntimeException("Failed to create outbox event", e);
        }
    }

    /**
     * Data class for ledger entry.
     */
    public static class EntryData {
        public String accountType;
        public String ownerId;
        public EntryType entryType;
        public Long amount;
        public String currency;

        public EntryData(String accountType, String ownerId, EntryType entryType, Long amount, String currency) {
            this.accountType = accountType;
            this.ownerId = ownerId;
            this.entryType = entryType;
            this.amount = amount;
            this.currency = currency;
        }
    }
}
