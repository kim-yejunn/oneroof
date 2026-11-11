package com.oneroof.payment;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface PaymentInstructionRepository extends JpaRepository<PaymentInstruction, Long> {

	Optional<PaymentInstruction> findByReferenceTypeAndReferenceId(String referenceType, String referenceId);

	List<PaymentInstruction> findByCorrelationIdOrderByIdAsc(String correlationId);

	List<PaymentInstruction> findByStatusOrderByIdAsc(PaymentStatus status);
}
