package com.wayline.app;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PaymentApiIntegrationTests extends IntegrationTestBase {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectsUnauthenticatedPaymentCreation() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                .header("Idempotency-Key", "test-key")
                .contentType("application/json")
                .content("{\"amount\":100,\"currency\":\"INR\",\"paymentMethod\":\"CARD\"}"))
            .andExpect(status().isUnauthorized());
    }
}
