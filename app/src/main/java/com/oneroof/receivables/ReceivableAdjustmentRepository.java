package com.oneroof.receivables;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface ReceivableAdjustmentRepository extends JpaRepository<ReceivableAdjustment, Long> {

	List<ReceivableAdjustment> findByAgencyId(String agencyId);

	List<ReceivableAdjustment> findByKindAndReference(AdjustmentKind kind, String reference);

	boolean existsByKindAndReference(AdjustmentKind kind, String reference);
}
