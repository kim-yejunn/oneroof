package com.oneroof.rebate;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import com.oneroof.TestcontainersConfiguration;
import com.oneroof.relatedparty.RelatedPartyAnalysis;
import com.oneroof.shared.Money;
import com.oneroof.shared.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** Monthly totals counted from the original rebate file; the group exceeds the limit every month. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RebateExposureOriginalDataTests {

	private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 7, 1);

	private static final Map<YearMonth, Long> GROUP_TOTALS = Map.of(YearMonth.of(2026, 1), 4_870_186L,
			YearMonth.of(2026, 2), 4_540_341L, YearMonth.of(2026, 3), 4_055_114L, YearMonth.of(2026, 4), 4_267_112L,
			YearMonth.of(2026, 5), 4_505_596L, YearMonth.of(2026, 6), 3_888_097L);

	@Autowired
	private RebateExposureService exposure;

	@Autowired
	private RebateClaimRepository claims;

	@Test
	void sumsTheRelatedGroupAboveTheLimitInEveryMonth() {
		GROUP_TOTALS.forEach((period, expectedTotal) -> {
			ExposureUnit group = groupUnit(this.exposure.assess(period, BUSINESS_DATE));

			assertThat(group.total()).as(period.toString()).isEqualTo(Money.ofKrw(expectedTotal));
			assertThat(group.limit()).isEqualTo(Money.ofKrw(2_000_000));
			assertThat(group.exceedsLimit()).isTrue();
			assertThat(group.requiredApprover()).isEqualTo(Role.SALES_EXECUTIVE);
			assertThat(group.agencyIds()).containsExactly("D-011", "D-024", "D-037");
		});
	}

	@Test
	void routesJuneClaimsByAuthority() {
		List<ExposureUnit> units = this.exposure.assess(YearMonth.of(2026, 6), BUSINESS_DATE);

		assertThat(units).hasSize(38);
		assertThat(units.stream().filter((unit) -> unit.requiredApprover() == Role.SALES_EXECUTIVE))
			.singleElement()
			.satisfies((unit) -> assertThat(unit.claims()).hasSize(3));
		assertThat(units.stream()
			.filter((unit) -> unit.requiredApprover() == Role.SALES_SUPPORT)
			.mapToInt((unit) -> unit.claims().size())
			.sum()).isEqualTo(37);
	}

	@Test
	void showsThatReviewingAgenciesOneByOneNeverTripsTheLimit() {
		RelatedPartyAnalysis noRelations = new RelatedPartyAnalysis(BUSINESS_DATE, List.of(), List.of());

		for (YearMonth period : GROUP_TOTALS.keySet()) {
			List<ExposureUnit> units = RebateExposureCalculator.calculate(period,
					this.claims.findByPeriodYmOrderByAgencyIdAsc(period.toString()), noRelations,
					this.exposure.policyFor(period));

			assertThat(units).as(period.toString()).noneMatch(ExposureUnit::exceedsLimit);
		}
	}

	@Test
	void leavesRejectedClaimsOutOfTheMonthlyTotal() {
		List<ExposureUnit> february = this.exposure.assess(YearMonth.of(2026, 2), BUSINESS_DATE);

		assertThat(february.stream().mapToInt((unit) -> unit.claims().size()).sum()).isEqualTo(39);
		assertThat(february.stream().flatMap((unit) -> unit.claims().stream()))
			.noneMatch((claim) -> claim.getOriginalStatus().equals("반려"));
	}

	private static ExposureUnit groupUnit(List<ExposureUnit> units) {
		return units.stream().filter(ExposureUnit::relatedGroup).findFirst().orElseThrow();
	}
}
