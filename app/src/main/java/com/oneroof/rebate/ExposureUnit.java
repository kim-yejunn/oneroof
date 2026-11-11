package com.oneroof.rebate;

import java.time.YearMonth;
import java.util.List;

import com.oneroof.shared.Money;
import com.oneroof.shared.Role;

/**
 * The unit the monthly limit applies to: a single agency, or a related group taken together.
 *
 * @param unitKey the related group key, or the agency id when the agency stands alone
 * @param relatedGroup whether this unit sums more than one related agency
 * @param agencyIds the agencies with a countable claim in the period, in ascending order
 * @param period the month the rebates are for
 * @param total the sum of the unit's countable claims
 * @param limit the monthly limit in force
 * @param exceedsLimit whether the total is above the limit
 * @param requiredApprover the role with authority over this total
 * @param claims the claims that make up the total
 */
public record ExposureUnit(String unitKey, boolean relatedGroup, List<String> agencyIds, YearMonth period, Money total,
		Money limit, boolean exceedsLimit, Role requiredApprover, List<RebateClaim> claims) {
}
