package com.oneroof.relatedparty;

import com.oneroof.masterdata.IdentityKind;
import com.oneroof.shared.Provenance;

/**
 * One identifier known for one agency, with where it came from.
 *
 * @param agencyId the agency the identifier belongs to
 * @param kind which of the three policy identifiers this is
 * @param rawValue the value as recorded
 * @param normalizedValue the value used for comparison
 * @param basis whether it was registered or observed
 * @param provenance the origin of the underlying record
 * @param sourceRef the file and line, or the record, the value was read from
 */
public record IdentityEvidence(String agencyId, IdentityKind kind, String rawValue, String normalizedValue,
		EvidenceBasis basis, Provenance provenance, String sourceRef) {

	public static IdentityEvidence of(String agencyId, IdentityKind kind, String rawValue, EvidenceBasis basis,
			Provenance provenance, String sourceRef) {
		return new IdentityEvidence(agencyId, kind, rawValue, IdentityNormalizer.normalize(kind, rawValue), basis,
				provenance, sourceRef);
	}
}
