package com.cloudcart.disputes.api;

import com.cloudcart.disputes.domain.model.*;
import com.cloudcart.disputes.domain.port.DisputeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DisputeQueryControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private DisputeRepository repository;

    @BeforeEach
    void seedTestData() {
        repository.deleteAll();
        repository.save(dispute("M1", "FRAUD",                  "500.00", "USD", 15));
        repository.save(dispute("M1", "PRODUCT_NOT_RECEIVED",   "200.00", "USD", 5));
        repository.save(dispute("M1", "DUPLICATE_PROCESSING",   "1200.00","USD", 2));
        repository.save(dispute("M2", "FRAUD",                  "800.00", "BRL", 10));
        repository.save(dispute("M2", "SUBSCRIPTION_CANCELLED", "150.00", "BRL", -1)); // expired
        repository.save(dispute("M3", "PRODUCT_UNACCEPTABLE",   "300.00", "MXN", 20));
    }

    @Test
    void listDisputes_noFilters_returnsAll() throws Exception {
        mvc.perform(get("/api/v1/disputes"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(6))
            .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void filterByMerchantId_returnsOnlyThatMerchant() throws Exception {
        mvc.perform(get("/api/v1/disputes").param("merchantId", "M1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(3))
            .andExpect(jsonPath("$.content[*].merchantId", everyItem(equalTo("M1"))));
    }

    @Test
    void filterByReasonCategory_returnsOnlyFraud() throws Exception {
        mvc.perform(get("/api/v1/disputes").param("reasonCategory", "FRAUD"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].reasonCategory", everyItem(equalTo("FRAUD"))));
    }

    @Test
    void filterByUrgencyLevel_returnsHighUrgencyOnly() throws Exception {
        // Disputes with ≤3 days deadline are HIGH urgency
        mvc.perform(get("/api/v1/disputes").param("urgencyLevel", "HIGH"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].urgencyLevel", everyItem(equalTo("HIGH"))));
    }

    @Test
    void filterByRecommendedAction_returnsContest() throws Exception {
        mvc.perform(get("/api/v1/disputes").param("recommendedAction", "CONTEST"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].recommendedAction", everyItem(equalTo("CONTEST"))));
    }

    @Test
    void filterByMinAmount_returnsOnlyAboveThreshold() throws Exception {
        // Seed has: M1-FRAUD=$500, M1-DUPLICATE=$1200, M2-FRAUD=$800 → 3 records >= $500
        // Also verifies the predicate is >= (inclusive), not > (exclusive): $500 must be included.
        mvc.perform(get("/api/v1/disputes").param("minAmount", "500"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(3))
            .andExpect(jsonPath("$.content[*].amount", everyItem(greaterThanOrEqualTo(500.0))));
    }

    @Test
    void combinedFilters_merchantAndCategory() throws Exception {
        mvc.perform(get("/api/v1/disputes")
                .param("merchantId", "M1")
                .param("reasonCategory", "FRAUD"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].merchantId").value("M1"))
            .andExpect(jsonPath("$.content[0].reasonCategory").value("FRAUD"));
    }

    @Test
    void noMatchingFilters_returnsEmptyList_not404() throws Exception {
        mvc.perform(get("/api/v1/disputes").param("merchantId", "NONEXISTENT_MERCHANT"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(0))
            .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void getById_existingDispute_returns200() throws Exception {
        Dispute saved = repository.save(dispute("M9", "FRAUD", "100.00", "USD", 7));

        mvc.perform(get("/api/v1/disputes/" + saved.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(saved.getId().toString()))
            .andExpect(jsonPath("$.merchantId").value("M9"));
    }

    @Test
    void getById_nonExistent_returns404WithStructuredError() throws Exception {
        mvc.perform(get("/api/v1/disputes/00000000-0000-0000-0000-000000000000"))
            .andExpect(status().isNotFound())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.code").value("DISPUTE_NOT_FOUND"));
    }

    @Test
    void pagination_defaultPageSize() throws Exception {
        mvc.perform(get("/api/v1/disputes").param("page", "0").param("size", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(2)))
            .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void merchantSummary_correctCounts() throws Exception {
        mvc.perform(get("/api/v1/merchants/M1/summary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.merchantId").value("M1"))
            .andExpect(jsonPath("$.totalDisputeCount").value(3));
    }

    @Test
    void merchantSummary_unknownMerchant_returns404() throws Exception {
        mvc.perform(get("/api/v1/merchants/UNKNOWN_MERCHANT/summary"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("MERCHANT_NOT_FOUND"));
    }

    @Test
    void globalSummary_noFilters_returnsAllDisputes() throws Exception {
        mvc.perform(get("/api/v1/disputes/summary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalDisputeCount").value(6));
    }

    @Test
    void summary_filteredByMerchantId_equivalentToMerchantEndpoint() throws Exception {
        mvc.perform(get("/api/v1/disputes/summary").param("merchantId", "M1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalDisputeCount").value(3));
    }

    @Test
    void summary_filteredByReasonCategory_countsOnlyFraud() throws Exception {
        mvc.perform(get("/api/v1/disputes/summary").param("reasonCategory", "FRAUD"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalDisputeCount").value(2)); // M1 FRAUD + M2 FRAUD
    }

    @Test
    void summary_combinedFilters_merchantAndCategory() throws Exception {
        mvc.perform(get("/api/v1/disputes/summary")
                .param("merchantId", "M2")
                .param("reasonCategory", "FRAUD"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalDisputeCount").value(1));
    }

    // ---- Pagination validation (AC-22) ----

    @Test
    void pagination_sizeZero_returns400() throws Exception {
        mvc.perform(get("/api/v1/disputes").param("size", "0"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_PAGINATION"));
    }

    @Test
    void pagination_sizeAbove100_returns400() throws Exception {
        mvc.perform(get("/api/v1/disputes").param("size", "101"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_PAGINATION"));
    }

    @Test
    void pagination_negativePageNumber_returns400() throws Exception {
        mvc.perform(get("/api/v1/disputes").param("page", "-1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_PAGINATION"));
    }

    // ---- Error path: non-UUID id and invalid enum param ----

    @Test
    void getById_nonUuidPath_returns400() throws Exception {
        mvc.perform(get("/api/v1/disputes/not-a-uuid"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void filterByInvalidReasonCategory_returns400() throws Exception {
        mvc.perform(get("/api/v1/disputes").param("reasonCategory", "GARBAGE"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    // ---- Date range filters (dateFrom / dateTo) ----

    @Test
    void filterByDateFrom_futureDate_returnsEmpty() throws Exception {
        mvc.perform(get("/api/v1/disputes").param("dateFrom", LocalDate.now().plusDays(1).toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void filterByDateTo_pastDate_returnsEmpty() throws Exception {
        mvc.perform(get("/api/v1/disputes").param("dateTo", LocalDate.now().minusDays(10).toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void filterByDateRange_includesMatchingDisputes() throws Exception {
        // All seed disputes have disputeDate = today - 5 days.
        String date = LocalDate.now().minusDays(5).toString();
        mvc.perform(get("/api/v1/disputes")
                .param("dateFrom", date)
                .param("dateTo", date))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(6));
    }

    // ---- Test fixture builder ----

    private Dispute dispute(String merchantId, String categoryName, String amount,
                             String currencyName, int deadlineDaysFromNow) {
        Dispute d = new Dispute();
        d.setProcessorId("PROCESSOR_TEST");
        d.setProcessorDisputeId("TEST-" + merchantId + "-" + System.nanoTime());
        d.setMerchantId(merchantId);
        d.setAmount(new BigDecimal(amount));
        d.setCurrency(Currency.valueOf(currencyName));
        d.setReasonCode("10.4");
        d.setReasonCategory(ReasonCategory.valueOf(categoryName));
        d.setDisputeDate(LocalDate.now().minusDays(5));
        d.setTransactionDate(LocalDate.now().minusDays(10));
        d.setResponseDeadline(LocalDate.now().plusDays(deadlineDaysFromNow));

        // Pre-compute scoring (mirrors what DisputeIngestionService does)
        long days = deadlineDaysFromNow;
        // Mirror DisputeIngestionService exactly: days < 0 → EXPIRED, days == 0 → OPEN
        d.setStatus(days < 0 ? DisputeStatus.EXPIRED : DisputeStatus.OPEN);

        com.cloudcart.disputes.domain.engine.WinProbabilityEngine engine =
            new com.cloudcart.disputes.domain.engine.WinProbabilityEngine();
        com.cloudcart.disputes.domain.engine.ScoringResult scoring =
            engine.score(d.getReasonCategory(), d.getAmount(), days);

        d.setWinProbability(scoring.winProbability());
        d.setRecommendedAction(scoring.recommendedAction());
        d.setUrgencyLevel(scoring.urgencyLevel());
        d.setScoringReason(scoring.reason());
        return d;
    }
}
