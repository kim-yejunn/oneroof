package com.oneroof.clarification;

import java.util.ArrayList;
import java.util.List;

import com.oneroof.audit.AuditLog;
import com.oneroof.audit.AuditRecord;
import com.oneroof.clarification.adapter.RuleBasedReplyInterpreter;
import com.oneroof.shared.BusinessClock;
import com.oneroof.shared.Provenance;
import com.oneroof.shared.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Interprets each answered reply after the answer has already been applied. The flow never waits
 * for it and nothing reads it to decide: approvals and state changes run on the reply itself. The
 * interpretation only helps the person who decides next to read the reply quickly.
 *
 * <p>The model call runs outside any database transaction so a slow answer holds no connection.
 * Whatever the configured interpreter returns is checked ({@link ReplyInterpreter}); when it fails
 * or is rejected, the rule-based interpreter answers instead and the record says so.
 */
@Component
class ReplyInterpretationService {

	private static final Logger log = LoggerFactory.getLogger(ReplyInterpretationService.class);

	private static final String SUBJECT = "ClarificationRequest";

	private final ReplyInterpreterPort interpreter;
	private final RuleBasedReplyInterpreter rules;
	private final ClarificationRequestRepository requests;
	private final ReplyInterpretationRepository interpretations;
	private final TransactionTemplate transactions;
	private final AuditLog auditLog;
	private final BusinessClock clock;

	ReplyInterpretationService(ReplyInterpreterPort interpreter, RuleBasedReplyInterpreter rules,
			ClarificationRequestRepository requests, ReplyInterpretationRepository interpretations,
			TransactionTemplate transactions, AuditLog auditLog, BusinessClock clock) {
		this.interpreter = interpreter;
		this.rules = rules;
		this.requests = requests;
		this.interpretations = interpretations;
		this.transactions = transactions;
		this.auditLog = auditLog;
		this.clock = clock;
	}

	@Async
	@TransactionalEventListener
	void on(ClarificationClosed closed) {
		ClarificationOutcome outcome = ClarificationOutcome.valueOf(closed.outcome());
		if (outcome == ClarificationOutcome.NO_REPLY || closed.replyText() == null || closed.replyText().isBlank()
				|| this.interpretations.existsByClarificationId(closed.clarificationId())) {
			return;
		}
		ClarificationRequest request = this.requests.findById(closed.clarificationId()).orElseThrow();
		ReplyInterpreterPort.Input input = new ReplyInterpreterPort.Input(request.getKind(), outcome,
				request.getQuestion(), closed.replyText());
		List<String> notes = new ArrayList<>();
		ReplyInterpreterPort used = this.interpreter;
		ReplyInterpreter.Checked checked = null;
		if (this.interpreter != this.rules) {
			try {
				checked = ReplyInterpreter.check(this.interpreter.interpret(input), input);
			}
			catch (RuntimeException ex) {
				log.warn("AI interpretation of clarification {} failed: {}", closed.clarificationId(), ex.toString());
				notes.add("AI 해석 실패(%s), 규칙 기반 해석으로 대체".formatted(ex.getClass().getSimpleName()));
				used = this.rules;
			}
		}
		if (checked == null) {
			used = this.rules;
			checked = ReplyInterpreter.check(this.rules.interpret(input), input);
		}
		notes.addAll(checked.notes());
		boolean fellBack = used == this.rules && this.interpreter != this.rules;
		String by = used.name();
		ReplyInterpretation value = checked.interpretation();
		Provenance provenance = used == this.rules ? Provenance.CALCULATED : Provenance.AI_GENERATED;
		this.transactions.executeWithoutResult((status) -> {
			if (this.interpretations.existsByClarificationId(closed.clarificationId())) {
				return;
			}
			this.interpretations.save(new ReplyInterpretationRecord(closed.clarificationId(), by, fellBack, value, notes,
					provenance, this.clock.today(), closed.correlationId()));
			this.auditLog.record(new AuditRecord(Role.SYSTEM.name(), "REPLY_INTERPRETED", SUBJECT,
					String.valueOf(closed.clarificationId()),
					"회신 해석(%s%s, 검토용): %s — 미답변 %d건, 확인할 일 %d건%s".formatted(by, fellBack ? ", 대체" : "",
							value.summary(), value.gaps().size(), value.followUps().size(),
							notes.isEmpty() ? "" : " / " + String.join(" / ", notes)),
					provenance, closed.correlationId()));
		});
	}
}
