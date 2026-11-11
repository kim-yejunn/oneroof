package com.oneroof;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import com.oneroof.masterdata.AgencyDirectory;
import com.oneroof.masterdata.AgencyIdentity;
import com.oneroof.masterdata.IdentityKind;
import com.oneroof.shared.OriginalDataImporter;
import com.oneroof.shared.Provenance;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** The numbers here are counted from the original files and act as the load's acceptance criteria. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OriginalDataLoadTests {

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private AgencyDirectory agencyDirectory;

	@Autowired
	private List<OriginalDataImporter> importers;

	@Test
	void loadsEveryOriginalRecordOnStartup() {
		assertThat(count("agency")).isEqualTo(50);
		assertThat(count("agency_identity")).isEqualTo(150);
		assertThat(count("rebate_claim")).isEqualTo(240);
		assertThat(count("receivable")).isEqualTo(146);
		assertThat(count("deposit")).isEqualTo(32);
	}

	@Test
	void keepsWhatTheSourceSaysAboutEachRebate() {
		assertThat(count("rebate_claim WHERE period_ym = '2026-06' AND original_status = '미지급'")).isEqualTo(40);
		assertThat(count("rebate_claim WHERE original_status = '미지급' AND original_approver IS NOT NULL")).isZero();
		assertThat(this.jdbc.queryForObject("SELECT max(amount_krw) FROM rebate_claim", Long.class))
			.isEqualTo(1_867_253L);
		assertThat(count("receivable WHERE agency_id IS NOT NULL")).isEqualTo(74);
	}

	@Test
	void registersIdentifiersFromTheRegistrationDateWithTheirSourceLine() {
		List<AgencyIdentity> current = this.agencyDirectory.identitiesEffectiveOn(LocalDate.of(2026, 7, 1));

		assertThat(current).hasSize(150);
		AgencyIdentity account = current.stream()
			.filter((identity) -> identity.getAgencyId().equals("D-011") && identity.getKind() == IdentityKind.BANK_ACCOUNT)
			.findFirst()
			.orElseThrow();
		assertThat(account.getValue()).isEqualTo("국민 412-27-556310");
		assertThat(account.getEffectiveFrom()).isEqualTo(LocalDate.of(2021, 2, 8));
		assertThat(account.getProvenance()).isEqualTo(Provenance.ORIGINAL);
		assertThat(account.getSourceRef()).isEqualTo("01_corevia/master/m_agency_cv.csv:L12");
		assertThat(this.agencyDirectory.identitiesEffectiveOn(LocalDate.of(2019, 12, 31))).isEmpty();
	}

	@Test
	void recordsEachLoadInTheAuditLogAndDoesNotLoadTwice() {
		assertThat(count("audit_log WHERE action = 'ORIGINAL_DATA_LOADED'")).isEqualTo(4);

		this.importers.forEach((importer) -> assertThat(importer.importIfEmpty())
			.allSatisfy((result) -> assertThat(result.alreadyPresent()).isTrue()));

		assertThat(count("agency")).isEqualTo(50);
		assertThat(count("rebate_claim")).isEqualTo(240);
	}

	private long count(String tableAndFilter) {
		return this.jdbc.queryForObject("SELECT count(*) FROM " + tableAndFilter, Long.class);
	}
}
