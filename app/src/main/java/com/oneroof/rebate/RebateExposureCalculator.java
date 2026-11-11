package com.oneroof.rebate;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.oneroof.relatedparty.RelatedPartyAnalysis;
import com.oneroof.shared.Money;

/**
 * Sums a month's rebates per limit unit and decides who must approve each unit. Agencies in the
 * same related group are summed, which is what stops a payment from being split across
 * differently named agencies to stay under the limit. Pure computation.
 */
public final class RebateExposureCalculator {

	/** A claim the source marks as rejected was not paid and does not count toward the monthly total. */
	static final String REJECTED = "반려";

	private RebateExposureCalculator() {
	}

	public static List<ExposureUnit> calculate(YearMonth period, List<RebateClaim> claims,
			RelatedPartyAnalysis relatedParties, RebateLimitPolicy policy) {
		Map<String, List<RebateClaim>> claimsByUnit = new TreeMap<>();
		Map<String, Boolean> groupedUnit = new TreeMap<>();
		for (RebateClaim claim : claims) {
			if (!claim.getPeriod().equals(period) || REJECTED.equals(claim.getOriginalStatus())) {
				continue;
			}
			var group = relatedParties.groupOf(claim.getAgencyId());
			String unitKey = group.map((g) -> g.groupKey()).orElse(claim.getAgencyId());
			claimsByUnit.computeIfAbsent(unitKey, (key) -> new ArrayList<>()).add(claim);
			groupedUnit.put(unitKey, group.isPresent());
		}

		List<ExposureUnit> units = new ArrayList<>();
		claimsByUnit.forEach((unitKey, unitClaims) -> {
			Money total = unitClaims.stream().map(RebateClaim::getAmount).reduce(Money.ZERO, Money::plus);
			List<String> agencyIds = unitClaims.stream().map(RebateClaim::getAgencyId).distinct().sorted().toList();
			units.add(new ExposureUnit(unitKey, groupedUnit.get(unitKey), agencyIds, period, total,
					policy.getMonthlyLimit(), policy.isExceededBy(total), policy.approverFor(total),
					List.copyOf(unitClaims)));
		});
		return units;
	}
}
