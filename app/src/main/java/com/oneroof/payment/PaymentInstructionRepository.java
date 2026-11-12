package com.oneroof.payment;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface PaymentInstructionRepository extends JpaRepository<PaymentInstruction, Long> {

	List<PaymentInstruction> findByReferenceTypeAndReferenceIdIn(String referenceType, Collection<String> referenceIds);

	List<PaymentInstruction> findByIdInOrderByIdAsc(Collection<Long> ids);

	List<PaymentInstruction> findByCorrelationIdOrderByIdAsc(String correlationId);

	List<PaymentInstruction> findByStatusOrderByIdAsc(PaymentStatus status);

	List<PaymentInstruction> findByAgencyIdAndStatusInOrderByIdAsc(String agencyId, Collection<PaymentStatus> statuses);

	/** Whether this account was already confirmed for the agency by releasing an earlier hold. */
	boolean existsByAgencyIdAndPayeeAccountAndHoldReasonIsNotNullAndStatusIn(String agencyId, String payeeAccount,
			Collection<PaymentStatus> statuses);
}
