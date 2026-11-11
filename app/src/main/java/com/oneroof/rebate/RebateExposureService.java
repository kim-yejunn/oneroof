package com.oneroof.rebate;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import com.oneroof.relatedparty.RelatedPartyAnalysis;
import com.oneroof.relatedparty.RelatedPartyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Answers, for one month, how much each limit unit is due and who has authority to approve it. */
@Service
@Transactional(readOnly = true)
public class RebateExposureService {

	private final RebateClaimRepository claims;
	private final RebateLimitPolicyRepository policies;
	private final RelatedPartyService relatedParties;

	RebateExposureService(RebateClaimRepository claims, RebateLimitPolicyRepository policies,
			RelatedPartyService relatedParties) {
		this.claims = claims;
		this.policies = policies;
		this.relatedParties = relatedParties;
	}

	/**
	 * Assesses a month using the related groups known on {@code relationsAsOf} and the limit in
	 * force on the last day of the month.
	 */
	public List<ExposureUnit> assess(YearMonth period, LocalDate relationsAsOf) {
		RelatedPartyAnalysis analysis = this.relatedParties.analysisAsOf(relationsAsOf);
		return RebateExposureCalculator.calculate(period, this.claims.findByPeriodYmOrderByAgencyIdAsc(period.toString()),
				analysis, policyFor(period));
	}

	RebateLimitPolicy policyFor(YearMonth period) {
		return this.policies.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(period.atEndOfMonth())
			.orElseThrow(() -> new IllegalStateException("No rebate limit policy in force for " + period));
	}
}
