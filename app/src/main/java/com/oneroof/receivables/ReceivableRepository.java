package com.oneroof.receivables;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface ReceivableRepository extends JpaRepository<Receivable, String> {

	List<Receivable> findByAgencyIdOrderByAgingDaysDescArIdAsc(String agencyId);
}
