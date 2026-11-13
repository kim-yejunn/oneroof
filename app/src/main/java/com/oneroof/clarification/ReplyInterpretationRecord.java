package com.oneroof.clarification;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import com.oneroof.shared.Provenance;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A stored interpretation of one reply. Lists are kept one item per line. */
@Entity
@Table(name = "reply_interpretation")
class ReplyInterpretationRecord {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "clarification_id", nullable = false, unique = true)
	private long clarificationId;

	@Column(nullable = false)
	private String interpreter;

	@Column(name = "fell_back", nullable = false)
	private boolean fellBack;

	@Column(nullable = false)
	private String summary;

	@Column(nullable = false)
	private String stance;

	@Column(nullable = false)
	private String reasons;

	@Column(nullable = false)
	private String evidence;

	@Column(nullable = false)
	private String amounts;

	@Column(nullable = false)
	private String gaps;

	@Column(name = "follow_ups", nullable = false)
	private String followUps;

	@Column(nullable = false)
	private String notes;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Provenance provenance;

	@Column(name = "recorded_on", nullable = false)
	private LocalDate recordedOn;

	@Column(name = "correlation_id", nullable = false)
	private String correlationId;

	protected ReplyInterpretationRecord() {
	}

	ReplyInterpretationRecord(long clarificationId, String interpreter, boolean fellBack, ReplyInterpretation value,
			List<String> notes, Provenance provenance, LocalDate recordedOn, String correlationId) {
		this.clarificationId = clarificationId;
		this.interpreter = interpreter;
		this.fellBack = fellBack;
		this.summary = value.summary();
		this.stance = value.stance();
		this.reasons = lines(value.reasons());
		this.evidence = lines(value.evidence());
		this.amounts = value.amountsKrw().stream().map(String::valueOf).collect(Collectors.joining("\n"));
		this.gaps = lines(value.gaps());
		this.followUps = lines(value.followUps());
		this.notes = lines(notes);
		this.provenance = provenance;
		this.recordedOn = recordedOn;
		this.correlationId = correlationId;
	}

	private static String lines(List<String> items) {
		return String.join("\n", items);
	}
}
