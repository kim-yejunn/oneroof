package com.oneroof.relatedparty;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import com.oneroof.TestcontainersConfiguration;
import com.oneroof.masterdata.IdentityKind;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** What the original data room yields. These are the facts the control decisions rest on. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RelatedPartyOriginalDataTests {

	private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 7, 1);

	@Autowired
	private RelatedPartyService relatedParties;

	@Test
	void findsExactlyOneRelatedGroupOfThreeAgencies() {
		RelatedPartyAnalysis analysis = this.relatedParties.analysisAsOf(BUSINESS_DATE);

		assertThat(analysis.groups()).singleElement().satisfies((group) -> {
			assertThat(group.groupKey()).isEqualTo("RPG-D-011");
			assertThat(group.members()).containsExactly("D-011", "D-024", "D-037");
		});
	}

	@Test
	void recordsWhichIdentifiersLinkEachPairInTheMaster() {
		RelatedGroup group = this.relatedParties.masterOnlyAnalysisAsOf(BUSINESS_DATE).groups().get(0);

		assertThat(kinds(group, "D-011", "D-024")).containsExactlyInAnyOrder(IdentityKind.REPRESENTATIVE,
				IdentityKind.ADDRESS);
		assertThat(kinds(group, "D-011", "D-037")).containsExactlyInAnyOrder(IdentityKind.REPRESENTATIVE,
				IdentityKind.BANK_ACCOUNT);
		// D-024 and D-037 share only the representative in the master and are joined through D-011.
		assertThat(kinds(group, "D-024", "D-037")).containsExactly(IdentityKind.REPRESENTATIVE);
		assertThat(group.members()).containsExactly("D-011", "D-024", "D-037");
	}

	@Test
	void depositsRevealThatD024PaysFromTheAccountSharedByTheOtherTwo() {
		RelatedGroup group = this.relatedParties.analysisAsOf(BUSINESS_DATE).groups().get(0);

		AgencyLink link = link(group, "D-024", "D-037");
		assertThat(link.isRelated()).isTrue();
		IdentifierMatch account = link.matches()
			.stream()
			.filter((match) -> match.kind() == IdentityKind.BANK_ACCOUNT)
			.findFirst()
			.orElseThrow();
		assertThat(account.left().basis()).isEqualTo(EvidenceBasis.OBSERVED_DEPOSIT);
		assertThat(account.left().rawValue()).isEqualTo("국민 412-27-556310");
		assertThat(account.left().sourceRef()).startsWith("01_corevia/financials/fin_collection_history_cv.csv:L");
		assertThat(account.right().basis()).isEqualTo(EvidenceBasis.MASTER_REGISTRATION);
	}

	@Test
	void reportsPairsSharingOneIdentifierWithoutGroupingThem() {
		RelatedPartyAnalysis analysis = this.relatedParties.analysisAsOf(BUSINESS_DATE);

		assertThat(analysis.nearMisses()).singleElement().satisfies((link) -> {
			assertThat(link.agencyA()).isEqualTo("D-034");
			assertThat(link.agencyB()).isEqualTo("D-041");
			assertThat(link.matches()).hasSize(1);
		});
		assertThat(analysis.groupOf("D-034")).isEmpty();
	}

	@Test
	void evaluatesAnEarlierDateWithTheIdentifiersRegisteredThen() {
		// D-037 registered on 2022-08-12; before that only D-011 and D-024 existed and already formed a group.
		RelatedPartyAnalysis analysis = this.relatedParties.analysisAsOf(LocalDate.of(2022, 8, 11));

		assertThat(analysis.groups()).singleElement()
			.satisfies((group) -> assertThat(group.members()).containsExactly("D-011", "D-024"));
	}

	private static List<IdentityKind> kinds(RelatedGroup group, String a, String b) {
		return link(group, a, b).matches().stream().map(IdentifierMatch::kind).toList();
	}

	private static AgencyLink link(RelatedGroup group, String a, String b) {
		return group.links()
			.stream()
			.filter((candidate) -> candidate.agencyA().equals(a) && candidate.agencyB().equals(b))
			.findFirst()
			.orElseThrow();
	}
}
