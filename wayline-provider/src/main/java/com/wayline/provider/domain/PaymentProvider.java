package com.wayline.provider.domain;

/**
 * Interface for payment provider abstraction.
 * All payment providers must implement this interface.
 */
public interface PaymentProvider {

    /**
     * Get provider name/identifier.
     */
    String getProviderName();

    /**
     * Check if provider supports given payment method and currency.
     */
    boolean supports(String paymentMethod, String currency);

    /**
     * Create payment with provider.
     * Should NOT hold database transaction during this call.
     *
     * @param request Payment creation request
     * @return Provider response
     * @throws ProviderException if provider call fails
     */
    ProviderPaymentResponse createPayment(ProviderPaymentRequest request) throws ProviderException;

    /**
     * Get payment status from provider.
     *
     * @param providerPaymentId Provider's payment ID
     * @return Current payment status
     * @throws ProviderException if provider call fails
     */
    ProviderPaymentResponse getPaymentStatus(String providerPaymentId) throws ProviderException;

    /**
     * Refund a payment.
     *
     * @param providerPaymentId Provider's payment ID
     * @param amount Amount to refund
     * @return Refund response
     * @throws ProviderException if provider call fails
     */
    ProviderPaymentResponse refundPayment(String providerPaymentId, Long amount) throws ProviderException;

    /**
     * Verify webhook signature from provider.
     *
     * @param payload Webhook payload
     * @param signature Provider's signature
     * @return true if signature is valid
     */
    boolean verifyWebhookSignature(String payload, String signature);

    /**
     * Get provider health status.
     *
     * @return true if provider is available
     */
    boolean isHealthy();
}
