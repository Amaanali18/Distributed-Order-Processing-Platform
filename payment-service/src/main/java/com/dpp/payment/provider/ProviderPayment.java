package com.dpp.payment.provider;

public record ProviderPayment(
        String providerPaymentId,
        ProviderPaymentStatus status,
        String failureCode,
        String failureMessage) {

    public static ProviderPayment pending(String providerPaymentId) {
        return new ProviderPayment(providerPaymentId, ProviderPaymentStatus.PENDING, null, null);
    }

    public static ProviderPayment succeeded(String providerPaymentId) {
        return new ProviderPayment(providerPaymentId, ProviderPaymentStatus.SUCCEEDED, null, null);
    }

    public static ProviderPayment failed(String providerPaymentId, String failureCode, String failureMessage) {
        return new ProviderPayment(providerPaymentId, ProviderPaymentStatus.FAILED, failureCode, failureMessage);
    }
}
