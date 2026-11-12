package com.oneroof.masterdata;

/**
 * An agency's registered identifier was corrected. Related groups are computed from the identity
 * history, so every module that relied on them must look again.
 *
 * @param changeId the {@code master_change} row
 * @param agencyId the agency corrected
 * @param kind the {@link IdentityKind} name
 * @param previousValue the value replaced
 * @param newValue the value now registered
 * @param effectiveFrom ISO date from which the new value applies
 * @param reason why, as reported
 * @param provenance a {@code Provenance} name: who vouches for the new value
 * @param correlationId the event that reported the change
 */
public record AgencyIdentityCorrected(long changeId, String agencyId, String kind, String previousValue,
		String newValue, String effectiveFrom, String reason, String provenance, String correlationId) {
}
