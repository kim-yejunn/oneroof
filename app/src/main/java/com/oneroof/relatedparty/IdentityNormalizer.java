package com.oneroof.relatedparty;

import java.text.Normalizer;

import com.oneroof.masterdata.IdentityKind;

/**
 * Reduces an identifier to the form used for comparison, so that spacing and punctuation
 * differences do not hide a match. Only formatting is removed; no value is guessed or corrected.
 */
final class IdentityNormalizer {

	private IdentityNormalizer() {
	}

	static String normalize(IdentityKind kind, String raw) {
		String text = Normalizer.normalize(raw, Normalizer.Form.NFKC).strip();
		return switch (kind) {
			case REPRESENTATIVE -> text.replaceAll("\\s+", "");
			case ADDRESS -> text.replaceAll("\\s+", " ");
			case BANK_ACCOUNT -> bankAccount(text);
		};
	}

	/** Bank name and account digits, ignoring spaces and hyphens: {@code 국민 412-27-556310} becomes {@code 국민:41227556310}. */
	private static String bankAccount(String text) {
		String bank = text.replaceAll("[\\d\\s-]", "");
		String digits = text.replaceAll("\\D", "");
		return bank + ":" + digits;
	}
}
