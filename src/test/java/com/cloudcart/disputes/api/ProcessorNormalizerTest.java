package com.cloudcart.disputes.api;

import com.cloudcart.disputes.api.dto.ProcessorARequest;
import com.cloudcart.disputes.api.dto.ProcessorBRequest;
import com.cloudcart.disputes.api.mapper.ProcessorNormalizer;
import com.cloudcart.disputes.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessorNormalizerTest {

    private ProcessorNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new ProcessorNormalizer();
    }

    // ---- Processor A: field mapping ----

    @Test
    void processorA_mapsAllRequiredFields() {
        ProcessorARequest req = new ProcessorARequest(
            "PA-DISP-001",
            "MERCHANT_001",
            new BigDecimal("250.00"),
            "USD",
            "10.4",
            LocalDate.of(2026, 6, 1),
            LocalDate.of(2026, 7, 5),
            LocalDate.of(2026, 5, 20),
            "customer@example.com",
            "ORDER-999"
        );

        Dispute dispute = normalizer.fromProcessorA(req);

        assertThat(dispute.getProcessorId()).isEqualTo("PROCESSOR_A");
        assertThat(dispute.getProcessorDisputeId()).isEqualTo("PA-DISP-001");
        assertThat(dispute.getMerchantId()).isEqualTo("MERCHANT_001");
        assertThat(dispute.getAmount()).isEqualByComparingTo(new BigDecimal("250.00"));
        assertThat(dispute.getCurrency()).isEqualTo(Currency.USD);
        assertThat(dispute.getReasonCode()).isEqualTo("10.4");
        assertThat(dispute.getReasonCategory()).isEqualTo(ReasonCategory.FRAUD);
        assertThat(dispute.getDisputeDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(dispute.getResponseDeadline()).isEqualTo(LocalDate.of(2026, 7, 5));
        assertThat(dispute.getTransactionDate()).isEqualTo(LocalDate.of(2026, 5, 20));
        assertThat(dispute.getCustomerEmail()).isEqualTo("customer@example.com");
        assertThat(dispute.getOrderId()).isEqualTo("ORDER-999");
    }

    @Test
    void processorA_nullableFieldsAccepted() {
        ProcessorARequest req = new ProcessorARequest(
            "PA-DISP-002", "MERCHANT_002",
            new BigDecimal("100.00"), "BRL", "13.1",
            LocalDate.of(2026, 6, 10), LocalDate.of(2026, 7, 10),
            null, null, null
        );

        Dispute dispute = normalizer.fromProcessorA(req);
        assertThat(dispute.getTransactionDate()).isNull();
        assertThat(dispute.getCustomerEmail()).isNull();
        assertThat(dispute.getOrderId()).isNull();
    }

    // ---- Processor B: field mapping + cents conversion ----

    @Test
    void processorB_mapsAllRequiredFields() {
        ProcessorBRequest req = new ProcessorBRequest(
            "PB-REF-001",
            "MERCHANT_003",
            5099,      // 50.99 USD in cents
            "USD",
            "4855",
            LocalDate.of(2026, 6, 5),
            LocalDate.of(2026, 6, 20),
            LocalDate.of(2026, 5, 25),
            "buyer@shop.mx",
            "MX-ORDER-42"
        );

        Dispute dispute = normalizer.fromProcessorB(req);

        assertThat(dispute.getProcessorId()).isEqualTo("PROCESSOR_B");
        assertThat(dispute.getProcessorDisputeId()).isEqualTo("PB-REF-001");
        assertThat(dispute.getMerchantId()).isEqualTo("MERCHANT_003");
        assertThat(dispute.getCurrency()).isEqualTo(Currency.USD);
        assertThat(dispute.getReasonCode()).isEqualTo("4855");
        assertThat(dispute.getReasonCategory()).isEqualTo(ReasonCategory.PRODUCT_NOT_RECEIVED);
        assertThat(dispute.getDisputeDate()).isEqualTo(LocalDate.of(2026, 6, 5));
        assertThat(dispute.getResponseDeadline()).isEqualTo(LocalDate.of(2026, 6, 20));
        assertThat(dispute.getCustomerEmail()).isEqualTo("buyer@shop.mx");
        assertThat(dispute.getOrderId()).isEqualTo("MX-ORDER-42");
    }

    @Test
    void processorB_convertsCentsToDecimalExactly() {
        // $50.99 = 5099 cents — must not be $509.90 or $50.990001
        ProcessorBRequest req = new ProcessorBRequest(
            "PB-AMT-001", "M1", 5099, "USD", "4837",
            LocalDate.now(), LocalDate.now().plusDays(10), null, null, null
        );
        Dispute dispute = normalizer.fromProcessorB(req);
        assertThat(dispute.getAmount()).isEqualByComparingTo(new BigDecimal("50.99"));
    }

    @Test
    void processorB_convertsCents_largeAmount() {
        // $3500.00 = 350000 cents
        ProcessorBRequest req = new ProcessorBRequest(
            "PB-AMT-002", "M1", 350000, "BRL", "4834",
            LocalDate.now(), LocalDate.now().plusDays(10), null, null, null
        );
        Dispute dispute = normalizer.fromProcessorB(req);
        assertThat(dispute.getAmount()).isEqualByComparingTo(new BigDecimal("3500.00"));
    }

    @Test
    void processorB_convertsCents_smallAmount() {
        // $15.00 = 1500 cents
        ProcessorBRequest req = new ProcessorBRequest(
            "PB-AMT-003", "M1", 1500, "MXN", "4841",
            LocalDate.now(), LocalDate.now().plusDays(5), null, null, null
        );
        Dispute dispute = normalizer.fromProcessorB(req);
        assertThat(dispute.getAmount()).isEqualByComparingTo(new BigDecimal("15.00"));
    }

    // ---- Reason code mapping ----

    @ParameterizedTest(name = "reasonCode={0} → {1}")
    @CsvSource({
        "10.4,  FRAUD",
        "4837,  FRAUD",
        "13.1,  PRODUCT_NOT_RECEIVED",
        "4855,  PRODUCT_NOT_RECEIVED",
        "13.3,  PRODUCT_UNACCEPTABLE",
        "4853,  PRODUCT_UNACCEPTABLE",
        "12.6,  DUPLICATE_PROCESSING",
        "4834,  DUPLICATE_PROCESSING",
        "13.2,  SUBSCRIPTION_CANCELLED",
        "4841,  SUBSCRIPTION_CANCELLED"
    })
    void reasonCodeMapping(String code, String expectedCategory) {
        ReasonCategory category = normalizer.mapReasonCode(code.trim());
        assertThat(category).isEqualTo(ReasonCategory.valueOf(expectedCategory.trim()));
    }

    @Test
    void unknownReasonCode_mapsToOther_notFraud() {
        // Unknown code → OTHER (base score 30), not FRAUD (base score 20).
        // Mapping to FRAUD would be semantically wrong: an unknown code is not evidence of fraud.
        ReasonCategory category = normalizer.mapReasonCode("UNKNOWN-CODE");
        assertThat(category).isEqualTo(ReasonCategory.OTHER);
    }

    @Test
    void unknownReasonCode_10_9_doesNotThrow() {
        // A real unknown code from a processor must not throw — just score conservatively.
        assertThatCode(() -> normalizer.mapReasonCode("10.9"))
            .doesNotThrowAnyException();
        assertThat(normalizer.mapReasonCode("10.9")).isEqualTo(ReasonCategory.OTHER);
    }
}
