package com.cloudcart.disputes.infra.config;

public class DisputeNotFoundException extends RuntimeException {

    private final String code;

    public DisputeNotFoundException(String message, String code) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static DisputeNotFoundException forDisputeId(String id) {
        return new DisputeNotFoundException("Dispute not found: " + id, "DISPUTE_NOT_FOUND");
    }

    public static DisputeNotFoundException forMerchantId(String merchantId) {
        return new DisputeNotFoundException("No disputes found for merchant: " + merchantId, "MERCHANT_NOT_FOUND");
    }
}
