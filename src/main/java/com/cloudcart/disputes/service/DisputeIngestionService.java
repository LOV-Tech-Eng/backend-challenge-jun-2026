package com.cloudcart.disputes.service;

import com.cloudcart.disputes.domain.engine.ScoringResult;
import com.cloudcart.disputes.domain.engine.WinProbabilityEngine;
import com.cloudcart.disputes.domain.model.Dispute;
import com.cloudcart.disputes.domain.model.DisputeStatus;
import com.cloudcart.disputes.domain.port.DisputeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
public class DisputeIngestionService {

    private final DisputeRepository repository;
    private final WinProbabilityEngine scoringEngine;

    public DisputeIngestionService(DisputeRepository repository) {
        this.repository = repository;
        this.scoringEngine = new WinProbabilityEngine();
    }

    /**
     * Ingests a normalized dispute: scores it, determines status, persists it.
     * Idempotent: if a dispute with the same (processorId, processorDisputeId) already
     * exists, returns the existing record without creating a duplicate.
     *
     * @param dispute a partially populated Dispute (normalized by ProcessorNormalizer,
     *                without scoring fields set yet)
     * @return the persisted dispute (either newly created or existing on re-ingest)
     */
    @Transactional
    public IngestResult ingest(Dispute dispute) {
        // Idempotency check: return existing record if already ingested
        Optional<Dispute> existing = repository.findByProcessorIdAndProcessorDisputeId(
            dispute.getProcessorId(), dispute.getProcessorDisputeId()
        );
        if (existing.isPresent()) {
            return new IngestResult(existing.get(), false);
        }

        // Compute days until deadline (negative = expired)
        long daysUntilDeadline = ChronoUnit.DAYS.between(LocalDate.now(), dispute.getResponseDeadline());

        // Determine status
        dispute.setStatus(daysUntilDeadline <= 0 ? DisputeStatus.EXPIRED : DisputeStatus.OPEN);

        // Score the dispute
        ScoringResult scoring = scoringEngine.score(
            dispute.getReasonCategory(),
            dispute.getAmount(),
            daysUntilDeadline
        );

        dispute.setWinProbability(scoring.winProbability());
        dispute.setRecommendedAction(scoring.recommendedAction());
        dispute.setUrgencyLevel(scoring.urgencyLevel());
        dispute.setScoringReason(scoring.reason());

        Dispute saved = repository.save(dispute);
        return new IngestResult(saved, true);
    }

    /**
     * Wraps the result of an ingest call, indicating whether it was newly created.
     * Used by the controller to return 201 vs 200.
     */
    public record IngestResult(Dispute dispute, boolean created) {}
}
