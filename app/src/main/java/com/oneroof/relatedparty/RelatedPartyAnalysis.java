package com.oneroof.relatedparty;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * The outcome of comparing every agency with every other on a given date.
 *
 * @param asOf the date whose registered identifiers were compared
 * @param groups related groups of two or more agencies
 * @param nearMisses pairs outside any common group that share exactly one identifier
 */
public record RelatedPartyAnalysis(LocalDate asOf, List<RelatedGroup> groups, List<AgencyLink> nearMisses) {

	public Optional<RelatedGroup> groupOf(String agencyId) {
		return this.groups.stream().filter((group) -> group.contains(agencyId)).findFirst();
	}
}
