package com.cloudcart.disputes.service;

import com.cloudcart.disputes.domain.engine.ScoringResult;
import com.cloudcart.disputes.domain.engine.WinProbabilityEngine;
import com.cloudcart.disputes.domain.model.Dispute;
import com.cloudcart.disputes.domain.model.DisputeStatus;
import com.cloudcart.disputes.domain.port.DisputeRepository;
import org.springframework.dao.DataIntegrityViolationException;
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

        // ──── Business rule: deadline expiration boundary ────────────────────────────────
        // The challenge defines response deadlines as LocalDate values, not timestamps.
        // When ChronoUnit.DAYS.between(today, deadline) == 0, "today IS the deadline" —
        // the card-network contest window has not yet closed.
        //
        // Risk-aware design principle: a dispute management system should ALWAYS prefer
        // to preserve the merchant's opportunity to contest rather than prematurely
        // classifying a dispute as a lost cause.
        //
        // Decision (intentional, not incidental):
        //   daysUntilDeadline < 0  → EXPIRED  (deadline was yesterday or earlier; window closed)
        //   daysUntilDeadline == 0 → OPEN     (deadline is today; merchant still has the day)
        //   daysUntilDeadline > 0  → OPEN     (clearly within the window)
        //
        // Trade-off accepted: a dispute expiring at midnight tonight will be flagged as
        // OPEN + HIGH urgency + URGENT_REVIEW for one calendar day. This is preferable to
        // incorrectly classifying a still-actionable dispute as EXPIRED and silently dropping
        // the contest opportunity.
        dispute.setStatus(daysUntilDeadline < 0 ? DisputeStatus.EXPIRED : DisputeStatus.OPEN);

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

        // ──── Concurrency safety net ─────────────────────────────────────────────────────
        // The application-level check above is sufficient under normal load (READ_COMMITTED
        // isolation on H2). Under concurrent requests with the same natural key, two threads
        // can both pass the check and race to insert. The DB unique constraint on
        // (processor_id, processor_dispute_id) catches this and throws DataIntegrityViolationException.
        //
        // We handle it here rather than letting it propagate as a 500: read back the winner's
        // row and return it as a successful idempotent response (200, not 201).
        // This is the "optimistic idempotency" pattern — check → insert → catch-on-conflict → re-read.
        try {
            Dispute saved = repository.save(dispute);
            return new IngestResult(saved, true);
        } catch (DataIntegrityViolationException e) {
            // Lost the race — another thread inserted first. Return their record (idempotent).
            return new IngestResult(
                repository.findByProcessorIdAndProcessorDisputeId(
                    dispute.getProcessorId(), dispute.getProcessorDisputeId()
                ).orElseThrow(() -> e), // re-throw only if somehow not found (should never happen)
                false
            );
        }
    }

    /**
     * Wraps the result of an ingest call, indicating whether it was newly created.
     * Used by the controller to return 201 vs 200.
     */
    public record IngestResult(Dispute dispute, boolean created) {}
}
