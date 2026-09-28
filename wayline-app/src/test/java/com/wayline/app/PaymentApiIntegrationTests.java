package com.wayline.app;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PaymentApiIntegrationTests extends IntegrationTestBase {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private static final String PAYMENT_BODY = """
        {"amount":12500,"currency":"INR","paymentMethod":"CARD"}""";

    @Test
    void rejectsPaymentCreationWithoutAToken() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(PAYMENT_BODY))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsLoginWithWrongPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"test-merchant","password":"wrong"}"""))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void createsAPaymentAndReachesATerminalState() throws Exception {
        String token = login();

        MvcResult result = mockMvc.perform(post("/api/v1/payments")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(PAYMENT_BODY))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.amount").value(12500))
            .andExpect(jsonPath("$.currency").value("INR"))
            .andReturn();

        JsonNode payment = objectMapper.readTree(result.getResponse().getContentAsString());
        assertNotNull(payment.get("id").asLong());
        assertNotNull(payment.get("selectedProvider").asText());

        // The simulated providers always answer, so the payment must not be left mid-flight.
        String status = payment.get("status").asText();
        assertEquals(true, java.util.List.of("SUCCESS", "FAILED", "UNKNOWN").contains(status),
            "unexpected terminal status: " + status);
    }

    @Test
    void replayingAnIdempotencyKeyReturnsTheSamePayment() throws Exception {
        String token = login();
        String key = UUID.randomUUID().toString();

        long first = createPayment(token, key);
        long second = createPayment(token, key);

        assertEquals(first, second, "replayed idempotency key created a second payment");
    }

    @Test
    void timelineExposesTheProviderAttemptsForAPayment() throws Exception {
        String token = login();
        long paymentId = createPayment(token, UUID.randomUUID().toString());

        mockMvc.perform(get("/api/v1/payments/{id}/timeline", paymentId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.payment.id").value(paymentId))
            .andExpect(jsonPath("$.stateTransitions").isArray())
            .andExpect(jsonPath("$.providerAttempts").isArray());
    }

    @Test
    void openApiDocumentIsPublishedAndDescribesThePaymentsResource() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/v1/payments'].post").exists())
            .andExpect(jsonPath("$.paths['/api/v1/payments/{paymentId}/refunds'].post").exists());
    }

    @Test
    void refundingMoreThanWasChargedIsRejected() throws Exception {
        String token = login();
        long paymentId = createSucceededPayment(token);

        mockMvc.perform(post("/api/v1/payments/{id}/refunds", paymentId)
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"amount":99999999,"reason":"too much"}"""))
            .andExpect(status().isConflict());
    }

    @Test
    void refundsAPaymentPartiallyAndThenExhaustsTheBalance() throws Exception {
        String token = login();
        long paymentId = createSucceededPayment(token);

        mockMvc.perform(post("/api/v1/payments/{id}/refunds", paymentId)
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"amount":5000,"reason":"partial return"}"""))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.amount").value(5000))
            .andExpect(jsonPath("$.paymentId").value(paymentId));

        // 12500 charged, 5000 already returned, so 7501 must not be allowed.
        mockMvc.perform(post("/api/v1/payments/{id}/refunds", paymentId)
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"amount":7501}"""))
            .andExpect(status().isConflict());
    }

            @Test
            void refundsTheFullRemainingBalanceWhenAmountIsOmitted() throws Exception {
            String token = login();
            long paymentId = createSucceededPayment(token);

            refund(token, paymentId, UUID.randomUUID().toString(), 5000L);

            mockMvc.perform(post("/api/v1/payments/{id}/refunds", paymentId)
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(7500))
                .andExpect(jsonPath("$.paymentId").value(paymentId));
            }

    @Test
    void replayingARefundIdempotencyKeyDoesNotRefundTwice() throws Exception {
        String token = login();
        long paymentId = createSucceededPayment(token);
        String key = UUID.randomUUID().toString();

        long first = refund(token, paymentId, key, 2500L);
        long second = refund(token, paymentId, key, 2500L);

        assertEquals(first, second, "replayed key created a second refund");
    }

    @Test
    void aPaymentThatNeverSucceededCannotBeRefunded() throws Exception {
        String token = login();
        String idempotencyKey = UUID.randomUUID().toString();
        MvcResult result = mockMvc.perform(post("/api/v1/payments")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"amount":12504,"currency":"INR","paymentMethod":"TEST_UNKNOWN"}"""))
            .andExpect(status().isCreated())
            .andReturn();
        long paymentId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        String status = statusOf(token, paymentId);
        assertEquals("UNKNOWN", status, "PENDING provider result must map to UNKNOWN");

        mockMvc.perform(post("/api/v1/payments/{id}/refunds", paymentId)
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"amount":100}"""))
            .andExpect(status().isConflict());
    }

    private long refund(String token, long paymentId, String key, long amount) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/payments/{id}/refunds", paymentId)
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":" + amount + "}"))
            .andExpect(status().isCreated())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private String statusOf(String token, long paymentId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/payments/{id}", paymentId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
            .get("status").asText();
    }

    /**
     * The simulated providers do not always succeed, so retry until one does rather than letting
     * an unrelated provider decline fail a refund test.
     */
    private long createSucceededPayment(String token) throws Exception {
        for (int attempt = 0; attempt < 20; attempt++) {
            long paymentId = createPayment(token, UUID.randomUUID().toString());
            if ("SUCCESS".equals(statusOf(token, paymentId))) {
                return paymentId;
            }
        }
        throw new IllegalStateException("no simulated payment succeeded in 20 attempts");
    }

    private long createPayment(String token, String idempotencyKey) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/payments")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(PAYMENT_BODY))
            .andExpect(status().isCreated())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private String login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"test-merchant","password":"test-password"}"""))
            .andExpect(status().isOk())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }
}
