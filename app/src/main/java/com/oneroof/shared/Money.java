package com.oneroof.shared;

/**
 * An amount in Korean won, VAT excluded, as the source data records it (whole won, no decimals).
 */
public record Money(long krw) implements Comparable<Money> {

	public static final Money ZERO = new Money(0);

	public static Money ofKrw(long krw) {
		return new Money(krw);
	}

	public Money plus(Money other) {
		return new Money(Math.addExact(this.krw, other.krw));
	}

	public Money minus(Money other) {
		return new Money(Math.subtractExact(this.krw, other.krw));
	}

	public boolean isGreaterThan(Money other) {
		return this.krw > other.krw;
	}

	@Override
	public int compareTo(Money other) {
		return Long.compare(this.krw, other.krw);
	}
}
