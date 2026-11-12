package com.oneroof.audit;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface AuditReferralRepository extends JpaRepository<AuditReferral, Long> {

	List<AuditReferral> findAllByOrderByIdAsc();
}
