package com.cloudcart.disputes.domain.port;

import com.cloudcart.disputes.domain.model.Dispute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface DisputeRepository extends JpaRepository<Dispute, UUID>, JpaSpecificationExecutor<Dispute> {

    Optional<Dispute> findByProcessorIdAndProcessorDisputeId(String processorId, String processorDisputeId);
}
