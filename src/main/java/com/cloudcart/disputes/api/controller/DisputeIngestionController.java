package com.cloudcart.disputes.api.controller;

import com.cloudcart.disputes.api.dto.DisputeResponse;
import com.cloudcart.disputes.api.dto.ProcessorARequest;
import com.cloudcart.disputes.api.dto.ProcessorBRequest;
import com.cloudcart.disputes.api.mapper.ProcessorNormalizer;
import com.cloudcart.disputes.domain.model.Dispute;
import com.cloudcart.disputes.service.DisputeIngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/disputes/ingest")
@Tag(name = "Ingestion", description = "Ingest chargeback data from multiple processor formats")
public class DisputeIngestionController {

    private final ProcessorNormalizer normalizer;
    private final DisputeIngestionService ingestionService;

    public DisputeIngestionController(ProcessorNormalizer normalizer,
                                      DisputeIngestionService ingestionService) {
        this.normalizer = normalizer;
        this.ingestionService = ingestionService;
    }

    @PostMapping(value = "/processor-a",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
        summary = "Ingest from Processor A",
        description = "Accepts Processor A format (camelCase fields, amounts in full currency units). " +
                      "Idempotent: re-ingesting the same disputeId returns the existing record (200)."
    )
    public ResponseEntity<DisputeResponse> ingestFromProcessorA(@Valid @RequestBody ProcessorARequest request) {
        Dispute normalized = normalizer.fromProcessorA(request);
        DisputeIngestionService.IngestResult result = ingestionService.ingest(normalized);
        return buildResponse(result);
    }

    @PostMapping(value = "/processor-a/bulk",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
        summary = "Bulk ingest from Processor A",
        description = "Accepts an array of Processor A format records. Each is ingested idempotently."
    )
    public ResponseEntity<List<DisputeResponse>> bulkIngestFromProcessorA(
            @Valid @RequestBody List<@Valid ProcessorARequest> requests) {
        List<DisputeResponse> responses = requests.stream()
            .map(req -> {
                Dispute normalized = normalizer.fromProcessorA(req);
                return DisputeResponse.from(ingestionService.ingest(normalized).dispute());
            })
            .toList();
        return ResponseEntity.status(HttpStatus.CREATED).body(responses);
    }

    @PostMapping(value = "/processor-b",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
        summary = "Ingest from Processor B",
        description = "Accepts Processor B format (snake_case fields, amount_cents in integer minor units). " +
                      "Idempotent: re-ingesting the same dispute_reference returns the existing record (200)."
    )
    public ResponseEntity<DisputeResponse> ingestFromProcessorB(@Valid @RequestBody ProcessorBRequest request) {
        Dispute normalized = normalizer.fromProcessorB(request);
        DisputeIngestionService.IngestResult result = ingestionService.ingest(normalized);
        return buildResponse(result);
    }

    @PostMapping(value = "/processor-b/bulk",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
        summary = "Bulk ingest from Processor B",
        description = "Accepts an array of Processor B format records. Each is ingested idempotently."
    )
    public ResponseEntity<List<DisputeResponse>> bulkIngestFromProcessorB(
            @Valid @RequestBody List<@Valid ProcessorBRequest> requests) {
        List<DisputeResponse> responses = requests.stream()
            .map(req -> {
                Dispute normalized = normalizer.fromProcessorB(req);
                return DisputeResponse.from(ingestionService.ingest(normalized).dispute());
            })
            .toList();
        return ResponseEntity.status(HttpStatus.CREATED).body(responses);
    }

    private ResponseEntity<DisputeResponse> buildResponse(DisputeIngestionService.IngestResult result) {
        DisputeResponse body = DisputeResponse.from(result.dispute());
        if (result.created()) {
            URI location = URI.create("/api/v1/disputes/" + result.dispute().getId());
            return ResponseEntity.created(location).body(body);
        }
        return ResponseEntity.ok(body);
    }
}
