package com.oneroof.rebate;

import java.util.StringJoiner;

import com.oneroof.relatedparty.AgencyLink;
import com.oneroof.relatedparty.IdentifierMatch;
import com.oneroof.relatedparty.RelatedGroup;

/**
 * Writes the explanation an approver reads: the figures, the rule applied, and for a related
 * group the identifiers that tie its members together. Every number comes from the calculation;
 * nothing here is generated text.
 */
final class ReviewRationale {

	private ReviewRationale() {
	}

	static String title(ExposureUnit unit) {
		if (unit.relatedGroup()) {
			return "%s 판매장려금 — 관련자 그룹 %s (%d개 대리점) 합산 %,d원".formatted(unit.period(), unit.unitKey(),
					unit.agencyIds().size(), unit.total().krw());
		}
		return "%s 판매장려금 — %s %,d원".formatted(unit.period(), unit.unitKey(), unit.total().krw());
	}

	static String text(ExposureUnit unit, RelatedGroup group, String policySourceRef) {
		StringJoiner text = new StringJoiner("\n");
		text.add("월 한도 %,d원 (근거: %s)".formatted(unit.limit().krw(), policySourceRef));
		for (RebateClaim claim : unit.claims()) {
			text.add("- %s %s: %,d원 (%s)".formatted(claim.getAgencyId(), claim.getRebateId(), claim.getAmount().krw(),
					claim.getSourceRef()));
		}
		if (group != null) {
			text.add("관련자 합산 %,d원 = 한도 대비 %s".formatted(unit.total().krw(), unit.exceedsLimit() ? "초과" : "이내"));
			text.add("관련자로 본 근거 (식별정보 2개 이상 일치, 전이 연결):");
			for (AgencyLink link : group.links()) {
				StringJoiner matches = new StringJoiner(", ");
				for (IdentifierMatch match : link.matches()) {
					matches.add("%s '%s' [%s ↔ %s]".formatted(match.kind().label(), match.left().rawValue(),
							match.left().basis().label(), match.right().basis().label()));
				}
				text.add("- %s ↔ %s: %s".formatted(link.agencyA(), link.agencyB(), matches));
			}
			text.add("일치 정황은 합산과 소명의 근거이며 부정을 확정하지 않음.");
		}
		else {
			text.add("단독 합산 %,d원 = 한도 대비 %s".formatted(unit.total().krw(), unit.exceedsLimit() ? "초과" : "이내"));
		}
		text.add("승인 권한: %s".formatted(unit.requiredApprover().label()));
		return text.toString();
	}
}
