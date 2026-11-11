package com.oneroof.rebate;

import org.springframework.data.jpa.repository.JpaRepository;

interface SettlementBatchRepository extends JpaRepository<SettlementBatch, Long> {

	boolean existsByPeriodYm(String periodYm);
}
