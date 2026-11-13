package com.oneroof.clarification;

import org.springframework.data.jpa.repository.JpaRepository;

interface ReplyInterpretationRepository extends JpaRepository<ReplyInterpretationRecord, Long> {

	boolean existsByClarificationId(long clarificationId);
}
