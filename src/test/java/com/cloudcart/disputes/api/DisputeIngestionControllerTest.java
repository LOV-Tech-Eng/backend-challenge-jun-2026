package com.cloudcart.disputes.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DisputeIngestionControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ---- Processor A: happy path ----

    @Test
    void processorA_validRequest_returns201WithIntelligence() throws Exception {
        String body = """
            {
              "disputeId": "PA-TEST-001",
              "merchantId": "MERCHANT_TEST",
              "transactionAmount": 750.00,
              "currencyCode": "USD",
              "reasonCode": "13.1",
              "chargebackDate": "2026-06-01",
              "dueDate": "2026-07-15",
              "transactionDate": "2026-05-20",
              "customerEmail": "user@example.com",
              "orderId": "ORDER-123"
            }
            """;

        mvc.perform(post("/api/v1/disputes/ingest/processor-a")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.processorId").value("PROCESSOR_A"))
            .andExpect(jsonPath("$.processorDisputeId").value("PA-TEST-001"))
            .andExpect(jsonPath("$.merchantId").value("MERCHANT_TEST"))
            .andExpect(jsonPath("$.amount").value(750.00))
            .andExpect(jsonPath("$.currency").value("USD"))
            .andExpect(jsonPath("$.reasonCategory").value("PRODUCT_NOT_RECEIVED"))
            .andExpect(jsonPath("$.winProbability").isNumber())
            .andExpect(jsonPath("$.recommendedAction").isString())
            .andExpect(jsonPath("$.urgencyLevel").isString())
            .andExpect(jsonPath("$.scoringReason").isNotEmpty());
    }

    // ---- Processor B: happy path ----

    @Test
    void processorB_validRequest_returns201_amountConvertedFromCents() throws Exception {
        String body = """
            {
              "dispute_reference": "PB-TEST-001",
              "merchant_ref": "MERCHANT_TEST",
              "amount_cents": 50099,
              "currency": "BRL",
              "reason_code": "4834",
              "chargeback_dt": "2026-06-10",
              "response_deadline_dt": "2026-07-10",
              "txn_date": "2026-06-01"
            }
            """;

        mvc.perform(post("/api/v1/disputes/ingest/processor-b")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.processorId").value("PROCESSOR_B"))
            .andExpect(jsonPath("$.processorDisputeId").value("PB-TEST-001"))
            .andExpect(jsonPath("$.amount").value(500.99))   // 50099 cents = 500.99
            .andExpect(jsonPath("$.currency").value("BRL"))
            .andExpect(jsonPath("$.reasonCategory").value("DUPLICATE_PROCESSING"))
            .andExpect(jsonPath("$.winProbability").isNumber());
    }

    // ---- Idempotency: re-ingest same key returns 200 (not 201) ----

    @Test
    void processorA_reIngestSameKey_returns200NotDuplicate() throws Exception {
        String body = """
            {
              "disputeId": "PA-IDEM-001",
              "merchantId": "MERCHANT_IDEM",
              "transactionAmount": 200.00,
              "currencyCode": "USD",
              "reasonCode": "10.4",
              "chargebackDate": "2026-06-01",
              "dueDate": "2026-07-10"
            }
            """;

        // First ingest: 201
        String firstResponse = mvc.perform(post("/api/v1/disputes/ingest/processor-a")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        // Second ingest (identical): 200
        mvc.perform(post("/api/v1/disputes/ingest/processor-a")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.processorDisputeId").value("PA-IDEM-001"));
    }

    // ---- Validation: missing required fields → 400 ----

    @Test
    void missingMerchantId_returns400WithViolation() throws Exception {
        String body = """
            {
              "disputeId": "PA-VAL-001",
              "transactionAmount": 100.00,
              "currencyCode": "USD",
              "reasonCode": "10.4",
              "chargebackDate": "2026-06-01",
              "dueDate": "2026-07-01"
            }
            """;

        mvc.perform(post("/api/v1/disputes/ingest/processor-a")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.violations[*].field", hasItem("merchantId")));
    }

    @Test
    void invalidCurrency_returns400() throws Exception {
        String body = """
            {
              "disputeId": "PA-VAL-002",
              "merchantId": "M1",
              "transactionAmount": 100.00,
              "currencyCode": "XYZ",
              "reasonCode": "10.4",
              "chargebackDate": "2026-06-01",
              "dueDate": "2026-07-01"
            }
            """;

        mvc.perform(post("/api/v1/disputes/ingest/processor-a")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.violations[*].field", hasItem("currencyCode")));
    }

    @Test
    void negativeAmount_returns400() throws Exception {
        String body = """
            {
              "disputeId": "PA-VAL-003",
              "merchantId": "M1",
              "transactionAmount": -50.00,
              "currencyCode": "USD",
              "reasonCode": "10.4",
              "chargebackDate": "2026-06-01",
              "dueDate": "2026-07-01"
            }
            """;

        mvc.perform(post("/api/v1/disputes/ingest/processor-a")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void malformedJson_returns400() throws Exception {
        mvc.perform(post("/api/v1/disputes/ingest/processor-a")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{bad json"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    void processorB_missingAmountCents_returns400() throws Exception {
        String body = """
            {
              "dispute_reference": "PB-VAL-001",
              "merchant_ref": "M1",
              "currency": "USD",
              "reason_code": "4837",
              "chargeback_dt": "2026-06-01",
              "response_deadline_dt": "2026-07-01"
            }
            """;

        mvc.perform(post("/api/v1/disputes/ingest/processor-b")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.violations[*].field", hasItem("amountCents")));
    }

    @Test
    void expiredDispute_isScoredAcceptHighUrgency() throws Exception {
        String body = """
            {
              "disputeId": "PA-EXP-001",
              "merchantId": "MERCHANT_EXP",
              "transactionAmount": 1500.00,
              "currencyCode": "USD",
              "reasonCode": "12.6",
              "chargebackDate": "2026-05-01",
              "dueDate": "2026-06-10"
            }
            """;

        // dueDate in the past → ACCEPT + HIGH urgency, regardless of high win probability category
        mvc.perform(post("/api/v1/disputes/ingest/processor-a")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.recommendedAction").value("ACCEPT"))
            .andExpect(jsonPath("$.urgencyLevel").value("HIGH"))
            .andExpect(jsonPath("$.status").value("EXPIRED"));
    }
}
