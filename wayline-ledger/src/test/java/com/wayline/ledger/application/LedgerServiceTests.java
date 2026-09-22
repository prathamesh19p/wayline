package com.wayline.ledger.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wayline.common.outbox.OutboxEventRepository;
import com.wayline.ledger.domain.LedgerEntry.EntryType;
import com.wayline.ledger.infrastructure.AccountRepository;
import com.wayline.ledger.infrastructure.LedgerEntryRepository;
import com.wayline.ledger.infrastructure.LedgerTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTests {
    @Mock private AccountRepository accountRepository;
    @Mock private LedgerTransactionRepository transactionRepository;
    @Mock private LedgerEntryRepository entryRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private ObjectMapper objectMapper;

    @Test
    void rejectsUnbalancedTransaction() {
        LedgerService service = new LedgerService(
            accountRepository,
            transactionRepository,
            entryRepository,
            outboxEventRepository,
            objectMapper
        );

        List<LedgerService.EntryData> entries = List.of(
            new LedgerService.EntryData("MERCHANT", "merchant", EntryType.DEBIT, 100L, "INR"),
            new LedgerService.EntryData("PLATFORM", "platform", EntryType.CREDIT, 99L, "INR")
        );

        assertThrows(IllegalStateException.class, () -> service.createTransaction("PAYMENT", entries, 1L));
    }
}
