package com.oneroof.approval;

import java.util.ArrayList;
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

	/** Stores a batch of requests in one transaction. */
	@ApplicationModuleListener
	void on(ApprovalRequestsSubmitted submitted) {
		List<ApprovalRequest> saved = this.requests.saveAll(submitted.requests()
			.stream()
			.map((request) -> new ApprovalRequest(request, this.clock.today()))
			.toList());
		for (ApprovalRequest request : saved) {
			this.auditLog.record(new AuditRecord(Role.SYSTEM.name(), "APPROVAL_REQUESTED", SUBJECT,
					String.valueOf(request.getId()),
					"%s 에게 상신: %s".formatted(request.getRequiredRole().label(), request.getTitle()),
					Provenance.CALCULATED, request.getCorrelationId()));
		}
	}

	@Transactional
	public ApprovalRequest decide(long requestId, Role actorRole, String actorName, boolean approved, String comment) {
		return decideAll(List.of(requestId), actorRole, actorName, approved, comment).get(0);
	}

	/**
	 * Decides several requests with one action, for example the sales support team clearing its
	 * routine queue. All or nothing: if the role lacks authority over any request, none is decided.
	 * The outcome is announced as one event.
	 */
	@Transactional
	public List<ApprovalRequest> decideAll(List<Long> requestIds, Role actorRole, String actorName, boolean approved,
			String comment) {
		String note = comment == null ? "" : comment;
		List<ApprovalRequest> decided = new ArrayList<>(requestIds.size());
		List<ApprovalsDecided.Decision> decisions = new ArrayList<>(requestIds.size());
		for (Long requestId : requestIds) {
			ApprovalRequest request = this.requests.findById(requestId)
				.orElseThrow(() -> new IllegalArgumentException("승인 요청 #%d 이 없음".formatted(requestId)));
			request.decide(actorRole, actorName, approved, comment, this.clock.today());
			this.auditLog.record(new AuditRecord(actorRole.name(), approved ? "APPROVAL_GRANTED" : "APPROVAL_REJECTED",
					SUBJECT, String.valueOf(requestId),
					"%s(%s) %s: %s%s".formatted(actorRole.label(), actorName, approved ? "승인" : "반려", request.getTitle(),
							note.isBlank() ? "" : " — " + note),
					Provenance.MOCK_REPLY, request.getCorrelationId()));
			decided.add(request);
			decisions.add(new ApprovalsDecided.Decision(requestId, request.getSubjectType(), request.getSubjectId(),
					approved, actorRole.name(), note, request.getCorrelationId()));
		}
		this.events.publishEvent(new ApprovalsDecided(decisions));
		return decided;
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
