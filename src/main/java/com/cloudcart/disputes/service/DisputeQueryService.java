package com.cloudcart.disputes.service;

import com.cloudcart.disputes.api.dto.MerchantSummaryResponse;
import com.cloudcart.disputes.domain.model.Currency;
import com.cloudcart.disputes.domain.model.Dispute;
import com.cloudcart.disputes.domain.model.DisputeStatus;
import com.cloudcart.disputes.domain.model.ReasonCategory;
import com.cloudcart.disputes.domain.model.RecommendedAction;
import com.cloudcart.disputes.domain.model.UrgencyLevel;
import com.cloudcart.disputes.domain.port.DisputeRepository;
import com.cloudcart.disputes.infra.config.DisputeNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DisputeQueryService {

    private final DisputeRepository repository;

    public DisputeQueryService(DisputeRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<Dispute> findDisputes(
            String merchantId,
            ReasonCategory reasonCategory,
            RecommendedAction recommendedAction,
            UrgencyLevel urgencyLevel,
            DisputeStatus status,
            LocalDate dateFrom,
            LocalDate dateTo,
            BigDecimal minAmount,
            int page,
            int size) {

        Specification<Dispute> spec = DisputeSpecifications.withFilters(
            merchantId, reasonCategory, recommendedAction, urgencyLevel,
            status, dateFrom, dateTo, minAmount
        );

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "disputeDate"));
        return repository.findAll(spec, pageRequest);
    }

    @Transactional(readOnly = true)
    public Dispute findById(UUID id) {
        return repository.findById(id)
            .orElseThrow(() -> DisputeNotFoundException.forDisputeId(id.toString()));
    }

    @Transactional(readOnly = true)
    public MerchantSummaryResponse getSummary(String merchantId) {
        Specification<Dispute> spec = merchantId != null
            ? DisputeSpecifications.withFilters(merchantId, null, null, null, null, null, null, null)
            : (root, query, cb) -> cb.conjunction();

        List<Dispute> disputes = repository.findAll(spec);

        if (merchantId != null && disputes.isEmpty()) {
            throw DisputeNotFoundException.forMerchantId(merchantId);
        }

        return buildSummary(merchantId, disputes);
    }

    private MerchantSummaryResponse buildSummary(String merchantId, List<Dispute> disputes) {
        int totalCount = disputes.size();

        // Win probability average
        OptionalDouble avgProb = disputes.stream()
            .mapToInt(Dispute::getWinProbability)
            .average();
        double averageWinProbability = avgProb.isPresent()
            ? BigDecimal.valueOf(avgProb.getAsDouble()).setScale(2, RoundingMode.HALF_UP).doubleValue()
            : 0.0;

        // Amount at risk (OPEN, contestable disputes) per currency
        Map<Currency, BigDecimal> amountAtRisk = disputes.stream()
            .filter(d -> d.getStatus() == DisputeStatus.OPEN
                && (d.getRecommendedAction() == RecommendedAction.CONTEST
                    || d.getRecommendedAction() == RecommendedAction.URGENT_REVIEW))
            .collect(Collectors.groupingBy(
                Dispute::getCurrency,
                Collectors.reducing(BigDecimal.ZERO, Dispute::getAmount, BigDecimal::add)
            ));

        // Total volume per currency
        Map<Currency, BigDecimal> totalVolume = disputes.stream()
            .collect(Collectors.groupingBy(
                Dispute::getCurrency,
                Collectors.reducing(BigDecimal.ZERO, Dispute::getAmount, BigDecimal::add)
            ));

        // Breakdown by reason category
        Map<ReasonCategory, MerchantSummaryResponse.CategoryStats> byReason = disputes.stream()
            .collect(Collectors.groupingBy(Dispute::getReasonCategory))
            .entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> {
                    List<Dispute> group = e.getValue();
                    Map<Currency, BigDecimal> catVolume = group.stream()
                        .collect(Collectors.groupingBy(
                            Dispute::getCurrency,
                            Collectors.reducing(BigDecimal.ZERO, Dispute::getAmount, BigDecimal::add)
                        ));
                    return new MerchantSummaryResponse.CategoryStats(group.size(), catVolume);
                }
            ));

        // Counts by recommended action
        Map<RecommendedAction, Integer> byAction = disputes.stream()
            .collect(Collectors.groupingBy(Dispute::getRecommendedAction,
                Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));

        // Counts by urgency level
        Map<UrgencyLevel, Integer> byUrgency = disputes.stream()
            .collect(Collectors.groupingBy(Dispute::getUrgencyLevel,
                Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));

        return new MerchantSummaryResponse(
            merchantId,
            totalCount,
            amountAtRisk,
            totalVolume,
            averageWinProbability,
            byReason,
            byAction,
            byUrgency
        );
    }
}
