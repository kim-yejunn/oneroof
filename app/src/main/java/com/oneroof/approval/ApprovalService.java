package com.oneroof.approval;

import java.util.List;

import com.oneroof.audit.AuditLog;
import com.oneroof.audit.AuditRecord;
import com.oneroof.shared.BusinessClock;
import com.oneroof.shared.Provenance;
import com.oneroof.shared.Role;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Holds requests for decisions and records what the responsible role decided. The system never
 * decides here: a request stays pending until a person in the required role acts.
 */
@Service
public class ApprovalService {

	private static final String SUBJECT = "ApprovalRequest";

	private final ApprovalRequestRepository requests;
	private final ApplicationEventPublisher events;
	private final AuditLog auditLog;
	private final BusinessClock clock;

	ApprovalService(ApprovalRequestRepository requests, ApplicationEventPublisher events, AuditLog auditLog,
			BusinessClock clock) {
		this.requests = requests;
		this.events = events;
		this.auditLog = auditLog;
		this.clock = clock;
	}

	@ApplicationModuleListener
	void on(ApprovalRequestSubmitted submitted) {
		ApprovalRequest request = this.requests.save(new ApprovalRequest(submitted, this.clock.today()));
		this.auditLog.record(new AuditRecord(Role.SYSTEM.name(), "APPROVAL_REQUESTED", SUBJECT,
				String.valueOf(request.getId()),
				"%s 에게 상신: %s".formatted(request.getRequiredRole().label(), request.getTitle()),
				Provenance.CALCULATED, submitted.correlationId()));
	}

	@Transactional
	public ApprovalRequest decide(long requestId, Role actorRole, String actorName, boolean approved, String comment) {
		ApprovalRequest request = this.requests.findById(requestId)
			.orElseThrow(() -> new IllegalArgumentException("승인 요청 #%d 이 없음".formatted(requestId)));
		request.decide(actorRole, actorName, approved, comment, this.clock.today());
		this.auditLog.record(new AuditRecord(actorRole.name(), approved ? "APPROVAL_GRANTED" : "APPROVAL_REJECTED",
				SUBJECT, String.valueOf(requestId),
				"%s(%s) %s: %s%s".formatted(actorRole.label(), actorName, approved ? "승인" : "반려", request.getTitle(),
						comment == null || comment.isBlank() ? "" : " — " + comment),
				Provenance.MOCK_REPLY, request.getCorrelationId()));
		this.events.publishEvent(new ApprovalDecided(requestId, request.getSubjectType(), request.getSubjectId(),
				approved, actorRole.name(), comment == null ? "" : comment, request.getCorrelationId()));
		return request;
	}

	/** Decides several requests with one action, for example the sales support team clearing its routine queue. */
	@Transactional
	public List<ApprovalRequest> decideAll(List<Long> requestIds, Role actorRole, String actorName, boolean approved,
			String comment) {
		return requestIds.stream().map((id) -> decide(id, actorRole, actorName, approved, comment)).toList();
	}

	@Transactional(readOnly = true)
	public List<ApprovalRequest> pendingFor(Role role) {
		return this.requests.findByRequiredRoleAndStatusOrderByIdAsc(role, ApprovalStatus.PENDING);
	}

	@Transactional(readOnly = true)
	public List<ApprovalRequest> requestsAbout(String subjectType, String subjectId) {
		return this.requests.findBySubjectTypeAndSubjectIdOrderByIdAsc(subjectType, subjectId);
	}
}
