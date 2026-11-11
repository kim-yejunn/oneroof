package com.oneroof.relatedparty;

import static org.assertj.core.api.Assertions.assertThat;

import com.oneroof.masterdata.IdentityKind;
import org.junit.jupiter.api.Test;

class IdentityNormalizerTests {

	@Test
	void ignoresSpacingAndHyphensInBankAccounts() {
		assertThat(IdentityNormalizer.normalize(IdentityKind.BANK_ACCOUNT, "국민 412-27-556310"))
			.isEqualTo("국민:41227556310")
			.isEqualTo(IdentityNormalizer.normalize(IdentityKind.BANK_ACCOUNT, " 국민412 27 556310 "));
	}

	@Test
	void keepsDifferentBanksWithTheSameDigitsApart() {
		assertThat(IdentityNormalizer.normalize(IdentityKind.BANK_ACCOUNT, "하나 412-27-556310"))
			.isNotEqualTo(IdentityNormalizer.normalize(IdentityKind.BANK_ACCOUNT, "국민 412-27-556310"));
	}

	@Test
	void collapsesWhitespaceInAddressesAndNames() {
		assertThat(IdentityNormalizer.normalize(IdentityKind.ADDRESS, "성남시  상공로   128 "))
			.isEqualTo("성남시 상공로 128");
		assertThat(IdentityNormalizer.normalize(IdentityKind.REPRESENTATIVE, "박 상현")).isEqualTo("박상현");
	}

	@Test
	void doesNotTreatDifferentAddressesAsEqual() {
		assertThat(IdentityNormalizer.normalize(IdentityKind.ADDRESS, "성남시 번영로 77"))
			.isNotEqualTo(IdentityNormalizer.normalize(IdentityKind.ADDRESS, "성남시 상공로 128"));
	}
}
