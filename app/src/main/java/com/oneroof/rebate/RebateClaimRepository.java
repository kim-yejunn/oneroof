package com.oneroof.rebate;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface RebateClaimRepository extends JpaRepository<RebateClaim, String> {

	List<RebateClaim> findByPeriodYmOrderByAgencyIdAsc(String periodYm);

	List<RebateClaim> findByReviewIdOrderByAgencyIdAsc(Long reviewId);
}
