package com.oneroof.relatedparty;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.oneroof.masterdata.IdentityKind;
import com.oneroof.shared.Provenance;
import org.junit.jupiter.api.Test;

class RelatedPartyAnalyzerTests {

	private static final LocalDate AS_OF = LocalDate.of(2026, 7, 1);

	private final List<IdentityEvidence> evidence = new ArrayList<>();

	@Test
	void relatesAgenciesSharingTwoIdentifiers() {
		agency("A", "kim", "road 1", "bank 111");
		agency("B", "kim", "road 1", "bank 222");

		RelatedPartyAnalysis analysis = RelatedPartyAnalyzer.analyze(AS_OF, this.evidence);

		assertThat(analysis.groups()).singleElement().satisfies((group) -> {
			assertThat(group.groupKey()).isEqualTo("RPG-A");
			assertThat(group.members()).containsExactly("A", "B");
		});
		assertThat(analysis.nearMisses()).isEmpty();
	}

	@Test
	void doesNotRelateAgenciesSharingOnlyOneIdentifier() {
		agency("A", "kim", "road 1", "bank 111");
		agency("B", "kim", "road 2", "bank 222");

		RelatedPartyAnalysis analysis = RelatedPartyAnalyzer.analyze(AS_OF, this.evidence);

		assertThat(analysis.groups()).isEmpty();
		assertThat(analysis.nearMisses()).singleElement().satisfies((link) -> {
			assertThat(link.isRelated()).isFalse();
			assertThat(link.matches()).extracting(IdentifierMatch::kind).containsExactly(IdentityKind.REPRESENTATIVE);
		});
	}

	@Test
	void connectsGroupsTransitivelyThroughASharedMember() {
		agency("A", "kim", "road 1", "bank 111");
		agency("B", "kim", "road 1", "bank 222");
		agency("C", "kim", "road 3", "bank 111");
		agency("D", "lee", "road 9", "bank 999");

		RelatedPartyAnalysis analysis = RelatedPartyAnalyzer.analyze(AS_OF, this.evidence);

		assertThat(analysis.groups()).singleElement()
			.satisfies((group) -> assertThat(group.members()).containsExactly("A", "B", "C"));
		// B and C share only the representative, yet belong together through A; that link is evidence, not a near miss.
		assertThat(analysis.nearMisses()).isEmpty();
		assertThat(analysis.groupOf("B")).isEqualTo(analysis.groupOf("C"));
		assertThat(analysis.groupOf("D")).isEmpty();
	}

	@Test
	void countsAnObservedDepositAccountAsAnAccountMatch() {
		agency("A", "kim", "road 1", "bank 111");
		agency("B", "kim", "road 2", "bank 222");
		this.evidence.add(IdentityEvidence.of("B", IdentityKind.BANK_ACCOUNT, "bank 111", EvidenceBasis.OBSERVED_DEPOSIT,
				Provenance.ORIGINAL, "deposit:1"));

		RelatedPartyAnalysis analysis = RelatedPartyAnalyzer.analyze(AS_OF, this.evidence);

		assertThat(analysis.groups()).singleElement().satisfies((group) -> {
			AgencyLink link = group.links().get(0);
			IdentifierMatch account = link.matches()
				.stream()
				.filter((match) -> match.kind() == IdentityKind.BANK_ACCOUNT)
				.findFirst()
				.orElseThrow();
			assertThat(account.isRegisteredOnBothSides()).isFalse();
			assertThat(account.right().basis()).isEqualTo(EvidenceBasis.OBSERVED_DEPOSIT);
		});
	}

	private void agency(String id, String representative, String address, String account) {
		this.evidence.add(registered(id, IdentityKind.REPRESENTATIVE, representative));
		this.evidence.add(registered(id, IdentityKind.ADDRESS, address));
		this.evidence.add(registered(id, IdentityKind.BANK_ACCOUNT, account));
	}

	private static IdentityEvidence registered(String id, IdentityKind kind, String value) {
		return IdentityEvidence.of(id, kind, value, EvidenceBasis.MASTER_REGISTRATION, Provenance.ORIGINAL, "test:" + id);
	}
}
