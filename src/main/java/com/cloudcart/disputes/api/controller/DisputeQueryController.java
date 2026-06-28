package com.cloudcart.disputes.api.controller;

import com.cloudcart.disputes.api.dto.DisputeResponse;
import com.cloudcart.disputes.api.dto.MerchantSummaryResponse;
import com.cloudcart.disputes.api.dto.PagedDisputeResponse;
import com.cloudcart.disputes.domain.model.*;
import com.cloudcart.disputes.service.DisputeQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Disputes", description = "Query and filter disputes, retrieve dispute intelligence")
public class DisputeQueryController {

    private final DisputeQueryService queryService;

    public DisputeQueryController(DisputeQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/disputes")
    @Operation(
        summary = "List disputes with filters",
        description = "All filters are optional and combinable. Results are paginated (default page=0, size=20)."
    )
    public ResponseEntity<PagedDisputeResponse> getDisputes(
            @Parameter(description = "Filter by merchant ID")
            @RequestParam(required = false) String merchantId,
            @Parameter(description = "Filter by reason category: FRAUD, PRODUCT_NOT_RECEIVED, PRODUCT_UNACCEPTABLE, DUPLICATE_PROCESSING, SUBSCRIPTION_CANCELLED")
            @RequestParam(required = false) ReasonCategory reasonCategory,
            @Parameter(description = "Filter by recommended action: CONTEST, ACCEPT, URGENT_REVIEW")
            @RequestParam(required = false) RecommendedAction recommendedAction,
            @Parameter(description = "Filter by urgency level: HIGH, MEDIUM, LOW")
            @RequestParam(required = false) UrgencyLevel urgencyLevel,
            @Parameter(description = "Filter by status: OPEN, EXPIRED, RESOLVED")
            @RequestParam(required = false) DisputeStatus status,
            @Parameter(description = "Filter disputes on or after this date (YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @Parameter(description = "Filter disputes on or before this date (YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @Parameter(description = "Minimum transaction amount (inclusive)")
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (size > 100) size = 100;
        if (page < 0) page = 0;

        Page<Dispute> result = queryService.findDisputes(
            merchantId, reasonCategory, recommendedAction, urgencyLevel,
            status, dateFrom, dateTo, minAmount, page, size
        );

        PagedDisputeResponse response = new PagedDisputeResponse(
            result.getContent().stream().map(DisputeResponse::from).toList(),
            result.getNumber(),
            result.getSize(),
            result.getTotalElements(),
            result.getTotalPages()
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/disputes/{id}")
    @Operation(summary = "Get dispute by ID", description = "Returns a single dispute with full intelligence data.")
    public ResponseEntity<DisputeResponse> getDisputeById(@PathVariable UUID id) {
        Dispute dispute = queryService.findById(id);
        return ResponseEntity.ok(DisputeResponse.from(dispute));
    }

    @GetMapping("/disputes/summary")
    @Operation(
        summary = "Get summary statistics",
        description = "Returns aggregate statistics. If merchantId is provided, scoped to that merchant; " +
                      "otherwise returns global stats across all merchants."
    )
    public ResponseEntity<MerchantSummaryResponse> getSummary(
            @Parameter(description = "Merchant ID (optional — omit for global summary)")
            @RequestParam(required = false) String merchantId) {
        return ResponseEntity.ok(queryService.getSummary(merchantId));
    }

    @GetMapping("/merchants/{merchantId}/summary")
    @Operation(
        summary = "Get merchant summary statistics",
        description = "Returns dispute statistics for a specific merchant: count, volume, win probability, breakdown by reason/action/urgency."
    )
    public ResponseEntity<MerchantSummaryResponse> getMerchantSummary(@PathVariable String merchantId) {
        return ResponseEntity.ok(queryService.getSummary(merchantId));
    }
}
