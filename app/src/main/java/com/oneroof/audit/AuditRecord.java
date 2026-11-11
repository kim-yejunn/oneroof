package com.oneroof.audit;

import com.oneroof.shared.Provenance;

/**
 * One thing that happened, as the caller describes it.
 *
 * @param actorRole the role that acted, for example "영업임원" or "SYSTEM"
 * @param action a short verb phrase, for example "APPROVAL_GRANTED"
 * @param subjectType the kind of thing acted on, for example "RebateClaim"
 * @param subjectId the identifier of that thing
 * @param detail a human-readable explanation with the figures the decision rested on
 * @param provenance where the information behind this entry came from
 * @param correlationId ties together every entry caused by the same incoming event
 */
public record AuditRecord(String actorRole, String action, String subjectType, String subjectId, String detail,
		Provenance provenance, String correlationId) {
}
