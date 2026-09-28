package com.wayline.ledger.api;

import com.wayline.ledger.application.LedgerService;
import com.wayline.ledger.domain.LedgerEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ledger/me")
@RequiredArgsConstructor
public class LedgerController {
    private final LedgerService ledgerService;

    @GetMapping("/{currency}/balance")
    public ResponseEntity<LedgerService.AccountBalance> getBalance(
        @PathVariable String currency,
        Authentication authentication
    ) {
        return ResponseEntity.ok(ledgerService.getMerchantBalance(authentication.getName(), currency));
    }

    @GetMapping("/{currency}/entries")
    public ResponseEntity<List<LedgerEntry>> getEntries(
        @PathVariable String currency,
        Authentication authentication
    ) {
        return ResponseEntity.ok(ledgerService.getMerchantEntries(authentication.getName(), currency));
    }
}
