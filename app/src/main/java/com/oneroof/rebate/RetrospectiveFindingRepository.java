package com.oneroof.rebate;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface RetrospectiveFindingRepository extends JpaRepository<RetrospectiveFinding, Long> {

	boolean existsByPeriodYmAndUnitKey(String periodYm, String unitKey);

	List<RetrospectiveFinding> findAllByOrderByPeriodYmAscIdAsc();
}
