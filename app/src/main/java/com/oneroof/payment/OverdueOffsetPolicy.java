package com.oneroof.payment;

import java.util.List;
import java.util.Optional;

import com.oneroof.receivables.OpenItem;
import com.oneroof.receivables.ReceivablesLedger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Decides whether a rebate should go to finance as an offset against the agency's overdue
 * receivables, and for how much.
 *
 * <p>[교육용 가정] 대리점 계약서에 "이행기가 지난 물품대금과 판매장려금을 대등액으로 상계할 수 있다"는 조항이 있다고
 * 둔다. 원본에 계약서가 없기 때문이다. 대상은 원본 미수금 연령표에서 {@code overdueDaysOver}일(기본 90일)을 넘긴 잔액뿐이다.
 *
 * <p>대리점법 시행령은 계약서의 지급 제한 사유에 해당하지 않는 장려금 삭감·미지급을 금지하고, 공정위는 2024년 4월
 * 연체 비율·일수와 무관하게 장려금 전액을 주지 않은 가구 3사를 제재했다. 그래서 이 규칙은 장려금을 몰수하지 않는다.
 * 같은 금액의 미수금을 함께 소멸시키는 상계만 제안하고, 그것도 회계·자금팀이 결정한다. 상계는 같은 법인 사이 채권끼리만
 * 가능하므로(민법 제492조) 관련자 그룹 안의 다른 대리점 미수금은 쓰지 않는다.
 */
@Component
class OverdueOffsetPolicy {

	private final ReceivablesLedger ledger;
	private final boolean enabled;
	private final int overdueDaysOver;

	OverdueOffsetPolicy(ReceivablesLedger ledger, @Value("${oneroof.offset.enabled:true}") boolean enabled,
			@Value("${oneroof.offset.overdue-days-over:90}") int overdueDaysOver) {
		this.ledger = ledger;
		this.enabled = enabled;
		this.overdueDaysOver = overdueDaysOver;
	}

	boolean enabled() {
		return this.enabled;
	}

	int overdueDaysOver() {
		return this.overdueDaysOver;
	}

	/** What could be offset now, ignoring whether the extension is switched on. Empty when nothing is overdue long enough. */
	Optional<Proposal> proposalFor(String agencyId, long amountKrw) {
		List<OpenItem> overdue = this.ledger.overdueBeyond(agencyId, this.overdueDaysOver);
		long overdueKrw = overdue.stream().mapToLong(OpenItem::outstandingKrw).sum();
		if (overdueKrw == 0 || amountKrw <= 0) {
			return Optional.empty();
		}
		long offset = Math.min(amountKrw, overdueKrw);
		String items = String.join(", ", overdue.stream()
			.map((item) -> "%s %d일 %,d원(%s)".formatted(item.arId(), item.agingDays(), item.outstandingKrw(),
					item.sourceRef()))
			.toList());
		String basis = "%d일 초과 연체 %d건 %,d원: %s".formatted(this.overdueDaysOver, overdue.size(), overdueKrw, items);
		return Optional.of(new Proposal(offset, overdueKrw, basis));
	}

	/**
	 * @param offsetKrw the amount to set off: the rebate or the overdue balance, whichever is smaller
	 * @param overdueKrw the agency's balance overdue beyond the threshold
	 * @param basis the invoices behind it, with their source rows
	 */
	record Proposal(long offsetKrw, long overdueKrw, String basis) {
	}
}
