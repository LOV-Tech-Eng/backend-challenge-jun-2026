package com.cloudcart.disputes.service;

import com.cloudcart.disputes.domain.model.*;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Composable JPA Specifications for dynamic dispute filtering.
 * All filters are optional; non-null parameters are AND-ed together.
 */
public final class DisputeSpecifications {

    private DisputeSpecifications() {}

    public static Specification<Dispute> withFilters(
            String merchantId,
            ReasonCategory reasonCategory,
            RecommendedAction recommendedAction,
            UrgencyLevel urgencyLevel,
            DisputeStatus status,
            LocalDate dateFrom,
            LocalDate dateTo,
            BigDecimal minAmount) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (merchantId != null && !merchantId.isBlank()) {
                predicates.add(cb.equal(root.get("merchantId"), merchantId));
            }
            if (reasonCategory != null) {
                predicates.add(cb.equal(root.get("reasonCategory"), reasonCategory));
            }
            if (recommendedAction != null) {
                predicates.add(cb.equal(root.get("recommendedAction"), recommendedAction));
            }
            if (urgencyLevel != null) {
                predicates.add(cb.equal(root.get("urgencyLevel"), urgencyLevel));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (dateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("disputeDate"), dateFrom));
            }
            if (dateTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("disputeDate"), dateTo));
            }
            if (minAmount != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("amount"), minAmount));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
