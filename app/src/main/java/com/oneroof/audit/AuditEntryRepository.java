package com.oneroof.audit;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {

	List<AuditEntry> findBySubjectTypeAndSubjectIdOrderByIdAsc(String subjectType, String subjectId);
}
