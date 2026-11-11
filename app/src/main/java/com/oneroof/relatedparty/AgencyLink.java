package com.oneroof.relatedparty;

import java.util.List;

/**
 * The identifiers two agencies share. A link with two or more matching identifiers makes the
 * agencies related parties; a link with exactly one is a circumstance worth noting, not a relation.
 *
 * @param agencyA the smaller agency id
 * @param agencyB the larger agency id
 * @param matches at most one match per identifier kind
 */
public record AgencyLink(String agencyA, String agencyB, List<IdentifierMatch> matches) {

	/** The policy threshold: agencies sharing this many identifiers are the same related party. */
	public static final int MINIMUM_MATCHING_IDENTIFIERS = 2;

	public boolean isRelated() {
		return this.matches.size() >= MINIMUM_MATCHING_IDENTIFIERS;
	}

	public boolean connects(String agencyId) {
		return this.agencyA.equals(agencyId) || this.agencyB.equals(agencyId);
	}
}
