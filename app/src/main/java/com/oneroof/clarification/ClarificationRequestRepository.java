package com.oneroof.clarification;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface ClarificationRequestRepository extends JpaRepository<ClarificationRequest, Long> {

	List<ClarificationRequest> findBySubjectTypeAndSubjectIdOrderByIdAsc(String subjectType, String subjectId);
}
