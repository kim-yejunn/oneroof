package com.oneroof.rebate;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

interface RebateReviewRepository extends JpaRepository<RebateReview, Long> {

	List<RebateReview> findByPeriodYmOrderByIdAsc(String periodYm);

	/**
	 * Loads a review and holds its row until the transaction ends. Payment confirmations for the
	 * claims of one review arrive concurrently; taking them one at a time is what lets the last
	 * one see that every claim is paid.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<RebateReview> findWithLockById(Long id);
}
