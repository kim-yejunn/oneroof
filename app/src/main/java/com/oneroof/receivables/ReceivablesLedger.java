package com.oneroof.receivables;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read access to receivables and deposits for other modules. */
@Service
@Transactional(readOnly = true)
public class ReceivablesLedger {

	private final DepositRepository deposits;
	private final ReceivableRepository receivables;
	private final ReceivableAdjustmentRepository adjustments;

	ReceivablesLedger(DepositRepository deposits, ReceivableRepository receivables,
			ReceivableAdjustmentRepository adjustments) {
		this.deposits = deposits;
		this.receivables = receivables;
		this.adjustments = adjustments;
	}

	/** Deposits received on or before the given date, oldest first. */
	public List<Deposit> depositsUpTo(LocalDate date) {
		return this.deposits.findByDepositDateLessThanEqualOrderByDepositDateAscDepositIdAsc(date);
	}

	/** What the agency still owes on each invoice, most overdue first. Fully settled invoices are left out. */
	public List<OpenItem> openItems(String agencyId) {
		return open(this.receivables.findByAgencyIdOrderByAgingDaysDescArIdAsc(agencyId),
				this.adjustments.findByAgencyId(agencyId));
	}

	/** The open invoices overdue by more than the given number of days, most overdue first. */
	public List<OpenItem> overdueBeyond(String agencyId, int daysOver) {
		return openItems(agencyId).stream().filter((item) -> item.agingDays() > daysOver).toList();
	}

	/** The agency's balance overdue beyond the given days on the aging report itself, before any adjustment. */
	public long originalOverdueBeyond(String agencyId, int daysOver) {
		return this.receivables.findByAgencyIdOrderByAgingDaysDescArIdAsc(agencyId)
			.stream()
			.filter((row) -> row.getAgingDays() > daysOver)
			.mapToLong((row) -> row.getOutstanding().krw())
			.sum();
	}

	/** Why a reported deposit cannot be applied, if it cannot. */
	public Optional<String> problemWithDeposit(String agencyId, long amountKrw) {
		if (amountKrw <= 0) {
			return Optional.of("입금액은 0원보다 커야 함");
		}
		long owed = openItems(agencyId).stream().mapToLong(OpenItem::outstandingKrw).sum();
		if (owed == 0) {
			return Optional.of("%s 는 남은 미수금이 없음".formatted(agencyId));
		}
		if (amountKrw > owed) {
			return Optional.of("입금액 %,d원이 남은 미수금 %,d원보다 큼".formatted(amountKrw, owed));
		}
		return Optional.empty();
	}

	static List<OpenItem> open(List<Receivable> rows, List<ReceivableAdjustment> adjustments) {
		Map<String, Long> adjusted = adjustments.stream()
			.collect(Collectors.groupingBy(ReceivableAdjustment::getArId,
					Collectors.summingLong(ReceivableAdjustment::getAmountKrw)));
		return rows.stream()
			.map((row) -> new OpenItem(row.getArId(), row.getAgingDays(),
					row.getOutstanding().krw() - adjusted.getOrDefault(row.getArId(), 0L), row.getSourceRef()))
			.filter((item) -> item.outstandingKrw() > 0)
			.toList();
	}
}
