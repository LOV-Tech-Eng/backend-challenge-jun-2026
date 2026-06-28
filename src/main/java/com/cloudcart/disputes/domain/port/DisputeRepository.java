package com.cloudcart.disputes.domain.port;

import com.cloudcart.disputes.domain.model.Dispute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DisputeRepository extends JpaRepository<Dispute, UUID>, JpaSpecificationExecutor<Dispute> {

    Optional<Dispute> findByProcessorIdAndProcessorDisputeId(String processorId, String processorDisputeId);

    boolean existsByMerchantId(String merchantId);

    @Query("SELECT DISTINCT d.merchantId FROM Dispute d WHERE d.merchantId = :merchantId")
    List<String> findMerchantIds(@Param("merchantId") String merchantId);
}
