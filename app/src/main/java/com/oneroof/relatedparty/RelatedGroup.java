package com.oneroof.relatedparty;

import java.util.List;

/**
 * Agencies treated as one related party. Membership is transitive: if A is related to B and A to
 * C, all three form one group even when B and C share fewer than two identifiers.
 *
 * @param groupKey a stable key derived from the smallest member id
 * @param members the member agency ids in ascending order
 * @param links every link between two members, including those below the threshold, as evidence
 */
public record RelatedGroup(String groupKey, List<String> members, List<AgencyLink> links) {

	public boolean contains(String agencyId) {
		return this.members.contains(agencyId);
	}
}
