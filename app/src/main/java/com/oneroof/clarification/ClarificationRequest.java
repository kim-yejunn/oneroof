package com.oneroof.clarification;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** A question sent to one or more agencies, and the answer once it arrives. */
@Entity
@Table(name = "clarification_request")
public class ClarificationRequest {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ClarificationKind kind;

	@Column(name = "subject_type", nullable = false)
	private String subjectType;

	@Column(name = "subject_id", nullable = false)
	private String subjectId;

	@Column(name = "agency_ids", nullable = false)
	private String agencyIds;

	@Column(nullable = false)
	private String title;

	@Column(nullable = false)
	private String question;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ClarificationStatus status;

	@Column(name = "sent_on", nullable = false)
	private LocalDate sentOn;

	@Column(name = "due_on", nullable = false)
	private LocalDate dueOn;

	@Column(name = "notice_reference")
	private String noticeReference;

	@Enumerated(EnumType.STRING)
	private ClarificationOutcome outcome;

	@Column(name = "reply_text")
	private String replyText;

	@Column(name = "reply_account")
	private String replyAccount;

	@Column(name = "replied_on")
	private LocalDate repliedOn;

	@Column(name = "correlation_id", nullable = false)
	private String correlationId;

	@Version
	private long version;

	protected ClarificationRequest() {
	}

	ClarificationRequest(ClarificationsRequested.Request request, LocalDate sentOn, LocalDate dueOn) {
		this.kind = ClarificationKind.valueOf(request.kind());
		this.subjectType = request.subjectType();
		this.subjectId = request.subjectId();
		this.agencyIds = request.agencyIds();
		this.title = request.title();
		this.question = request.question();
		this.status = ClarificationStatus.SENT;
		this.sentOn = sentOn;
		this.dueOn = dueOn;
		this.correlationId = request.correlationId();
	}

	void noticeSent(String reference) {
		this.noticeReference = reference;
	}

	/** Records the answer. Only an open request can be answered, and only with an answer that fits its kind. */
	void close(ClarificationOutcome outcome, String replyText, String replyAccount, LocalDate on) {
		if (this.status != ClarificationStatus.SENT) {
			throw new IllegalStateException("소명·확인 요청 #%d 은 이미 %s 상태임".formatted(this.id, this.status));
		}
		if (!this.kind.accepts(outcome)) {
			throw new IllegalArgumentException(
					"%s 요청에는 '%s' 회신을 쓸 수 없음".formatted(this.kind.label(), outcome.label()));
		}
		if (outcome == ClarificationOutcome.ACCOUNT_CHANGED && (replyAccount == null || replyAccount.isBlank())) {
			throw new IllegalArgumentException("계좌 변경 회신에는 새 계좌가 있어야 함");
		}
		this.status = outcome == ClarificationOutcome.NO_REPLY ? ClarificationStatus.EXPIRED : ClarificationStatus.ANSWERED;
		this.outcome = outcome;
		this.replyText = replyText;
		this.replyAccount = outcome == ClarificationOutcome.ACCOUNT_CHANGED ? replyAccount.trim() : null;
		this.repliedOn = on;
	}

	public Long getId() {
		return this.id;
	}

	public ClarificationKind getKind() {
		return this.kind;
	}

	public String getSubjectType() {
		return this.subjectType;
	}

	public String getSubjectId() {
		return this.subjectId;
	}

	public String getAgencyIds() {
		return this.agencyIds;
	}

	public String getTitle() {
		return this.title;
	}

	public String getQuestion() {
		return this.question;
	}

	public ClarificationStatus getStatus() {
		return this.status;
	}

	public LocalDate getSentOn() {
		return this.sentOn;
	}

	public LocalDate getDueOn() {
		return this.dueOn;
	}

	public String getNoticeReference() {
		return this.noticeReference;
	}

	public ClarificationOutcome getOutcome() {
		return this.outcome;
	}

	public String getReplyText() {
		return this.replyText;
	}

	public String getReplyAccount() {
		return this.replyAccount;
	}

	public LocalDate getRepliedOn() {
		return this.repliedOn;
	}

	public String getCorrelationId() {
		return this.correlationId;
	}
}
