package com.oneroof.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MoneyTests {

	@Test
	void addsAndComparesWholeWonAmounts() {
		Money total = Money.ofKrw(1_373_515).plus(Money.ofKrw(1_189_046)).plus(Money.ofKrw(1_325_536));

		assertThat(total).isEqualTo(Money.ofKrw(3_888_097));
		assertThat(total.isGreaterThan(Money.ofKrw(2_000_000))).isTrue();
		assertThat(Money.ofKrw(2_000_000).isGreaterThan(Money.ofKrw(2_000_000))).isFalse();
	}
}
