package com.cloudcart.disputes.domain.model;

public enum ReasonCategory {
    FRAUD,
    PRODUCT_NOT_RECEIVED,
    PRODUCT_UNACCEPTABLE,
    DUPLICATE_PROCESSING,
    SUBSCRIPTION_CANCELLED,
    /** Reason code received from processor was not recognized. Scored conservatively. */
    OTHER
}
