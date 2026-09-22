package com.wayline.common.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Webhook signature verifier.
 * Verifies HMAC-SHA256 signatures for provider webhooks.
 */
@Component
@Slf4j
public class WebhookSignatureVerifier {

    private static final String ALGORITHM = "HmacSHA256";

    /**
     * Verify webhook signature.
     *
     * @param payload Webhook payload (JSON string)
     * @param signature Signature from webhook header (hex or base64)
     * @param secret Shared secret with provider
     * @return true if signature is valid
     */
    public boolean verify(String payload, String signature, String secret) {
        try {
            if (payload == null || signature == null || secret == null || secret.isBlank()) {
                return false;
            }
            byte[] digest = generateDigest(payload, secret);
            boolean isValid = MessageDigest.isEqual(
                digest,
                decodeSignature(signature)
            );

            if (!isValid) {
                log.warn("Webhook signature verification failed");
            }

            return isValid;
        } catch (Exception e) {
            log.error("Error verifying webhook signature: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Generate signature for payload.
     */
    public String generateSignature(String payload, String secret) throws NoSuchAlgorithmException, InvalidKeyException {
        return HexFormat.of().formatHex(generateDigest(payload, secret));
        }

        private byte[] generateDigest(String payload, String secret)
            throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance(ALGORITHM);
        SecretKeySpec secretKeySpec = new SecretKeySpec(
            secret.getBytes(StandardCharsets.UTF_8),
            0,
            secret.getBytes(StandardCharsets.UTF_8).length,
            ALGORITHM
        );
        mac.init(secretKeySpec);

        byte[] hmac = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return hmac;
    }

    private byte[] decodeSignature(String signature) {
        try {
            return HexFormat.of().parseHex(signature);
        } catch (IllegalArgumentException ignored) {
            return Base64.getDecoder().decode(signature);
        }
    }
}
