package com.oneroof.clarification;

import java.util.List;
import java.util.Optional;

import com.oneroof.audit.AuditLog;
import com.oneroof.audit.AuditRecord;
import com.oneroof.intake.ClarificationReplyReceived;
import com.oneroof.shared.BusinessClock;
import com.oneroof.shared.Provenance;
import com.oneroof.shared.Role;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sends requests for explanation or confirmation to agencies and records their answers. What an
 * answer means is decided by the module that asked; this module only checks that the answer fits
 * the question and passes it on.
 */
@Service
public class ClarificationService {

	/** [교육용 가정] 원본에 회신 기한 규정이 없어 발송일로부터 7일로 둔다. */
	static final int REPLY_DAYS = 7;

	private static final String SUBJECT = "ClarificationRequest";

	private final ClarificationRequestRepository requests;
	private final AgencyNoticePort notices;
	private final ApplicationEventPublisher events;
	private final AuditLog auditLog;
	private final BusinessClock clock;

	ClarificationService(ClarificationRequestRepository requests, AgencyNoticePort notices,
			ApplicationEventPublisher events, AuditLog auditLog, BusinessClock clock) {
		this.requests = requests;
		this.notices = notices;
		this.events = events;
		this.auditLog = auditLog;
		this.clock = clock;
	}

	@ApplicationModuleListener
	void on(ClarificationsRequested requested) {
		List<ClarificationRequest> saved = this.requests.saveAll(requested.requests()
			.stream()
			.map((request) -> new ClarificationRequest(request, this.clock.today(),
					this.clock.today().plusDays(REPLY_DAYS)))
			.toList());
		for (ClarificationRequest request : saved) {
			request.noticeSent(
					this.notices.send(request.getId(), request.getAgencyIds(), request.getTitle(), request.getQuestion()));
			this.auditLog.record(new AuditRecord(Role.SYSTEM.name(), "CLARIFICATION_SENT", SUBJECT,
					String.valueOf(request.getId()),
					"%s 요청을 %s 에 발송%s, 회신 기한 %s (기한은 교육용 가정): %s".formatted(request.getKind().label(),
							request.getAgencyIds(), this.notices.isSimulated() ? "(모의 발송)" : "", request.getDueOn(),
							request.getTitle()),
					Provenance.ASSUMPTION, request.getCorrelationId()));
		}
	}

	/** Applies an answer. One that arrives after the request was closed is recorded and skipped. */
	@ApplicationModuleListener
	void on(ClarificationReplyReceived reply) {
		Optional<ClarificationRequest> found = this.requests.findById(reply.clarificationId());
		if (found.isEmpty() || found.get().getStatus() != ClarificationStatus.SENT) {
			this.auditLog.record(new AuditRecord("EXTERNAL:AGENCY", "CLARIFICATION_REPLY_IGNORED", SUBJECT,
					String.valueOf(reply.clarificationId()),
					found.isEmpty() ? "없는 요청에 대한 회신이라 반영하지 않음"
							: "이미 %s 상태라 회신을 반영하지 않음".formatted(found.get().getStatus()),
					Provenance.MOCK_REPLY, found.map(ClarificationRequest::getCorrelationId).orElse(reply.eventId())));
			return;
		}
		ClarificationRequest request = found.get();
		Optional<String> problem = problemWith(request.getId(), reply.outcome(), reply.replyText(), reply.replyAccount());
		if (problem.isPresent()) {
			this.auditLog.record(new AuditRecord("EXTERNAL:AGENCY", "CLARIFICATION_REPLY_IGNORED", SUBJECT,
					String.valueOf(request.getId()), problem.get() + " — 회신을 반영하지 않음", Provenance.MOCK_REPLY,
					request.getCorrelationId()));
			return;
		}
		ClarificationOutcome outcome = ClarificationOutcome.valueOf(reply.outcome());
		request.close(outcome, reply.replyText(), reply.replyAccount(), this.clock.today());
		this.auditLog.record(new AuditRecord(
				outcome == ClarificationOutcome.NO_REPLY ? Role.SYSTEM.name() : "EXTERNAL:AGENCY",
				outcome == ClarificationOutcome.NO_REPLY ? "CLARIFICATION_EXPIRED" : "CLARIFICATION_ANSWERED", SUBJECT,
				String.valueOf(request.getId()),
				"%s: %s%s%s".formatted(request.getKind().label(), outcome.label(),
						request.getReplyAccount() == null ? "" : " (새 계좌 " + request.getReplyAccount() + ")",
						reply.replyText().isBlank() ? "" : " — " + reply.replyText()),
				Provenance.MOCK_REPLY, request.getCorrelationId()));
		this.events.publishEvent(new ClarificationClosed(request.getId(), request.getKind().name(),
				request.getSubjectType(), request.getSubjectId(), request.getAgencyIds(), outcome.name(),
				reply.replyText(), request.getReplyAccount(), request.getCorrelationId()));
	}

	/**
	 * Checks an answer before it is accepted, so the console can refuse a mistyped one at once
	 * instead of failing in the background. Returns the reason it cannot be accepted, if any.
	 */
	@Transactional(readOnly = true)
	public Optional<String> problemWith(long clarificationId, String outcome, String replyAccount) {
		return problemWith(clarificationId, outcome, null, replyAccount);
	}

	/** Why the reply cannot be accepted, if it cannot. A dispute has to say what is disputed. */
	public Optional<String> problemWith(long clarificationId, String outcome, String replyText, String replyAccount) {
		Optional<ClarificationRequest> found = this.requests.findById(clarificationId);
		if (found.isEmpty()) {
			return Optional.of("소명·확인 요청 #%d 이 없음".formatted(clarificationId));
		}
		ClarificationOutcome parsed;
		try {
			parsed = ClarificationOutcome.valueOf(outcome);
		}
		catch (IllegalArgumentException | NullPointerException ex) {
			return Optional.of("알 수 없는 회신 종류: " + outcome);
		}
		ClarificationRequest request = found.get();
		if (request.getStatus() != ClarificationStatus.SENT) {
			return Optional.of("소명·확인 요청 #%d 은 이미 %s 상태임".formatted(clarificationId, request.getStatus()));
		}
		if (!request.getKind().accepts(parsed)) {
			return Optional.of("%s 요청에는 '%s' 회신을 쓸 수 없음".formatted(request.getKind().label(), parsed.label()));
		}
		if (parsed == ClarificationOutcome.ACCOUNT_CHANGED && (replyAccount == null || replyAccount.isBlank())) {
			return Optional.of("계좌 변경 회신에는 새 계좌가 있어야 함");
		}
		if (parsed == ClarificationOutcome.OFFSET_DISPUTED && (replyText == null || replyText.isBlank())) {
			return Optional.of("상계 이의에는 이의 사유가 있어야 함");
		}
		return Optional.empty();
	}

	@Transactional(readOnly = true)
	public List<ClarificationRequest> requestsAbout(String subjectType, String subjectId) {
		return this.requests.findBySubjectTypeAndSubjectIdOrderByIdAsc(subjectType, subjectId);
	}
}
