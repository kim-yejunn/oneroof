package com.oneroof.approval;

import java.util.List;

import com.oneroof.shared.Role;
import org.springframework.data.jpa.repository.JpaRepository;

interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {

	List<ApprovalRequest> findByRequiredRoleAndStatusOrderByIdAsc(Role requiredRole, ApprovalStatus status);

	List<ApprovalRequest> findBySubjectTypeAndSubjectIdOrderByIdAsc(String subjectType, String subjectId);
}
