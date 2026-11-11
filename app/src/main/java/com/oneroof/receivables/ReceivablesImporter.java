package com.oneroof.receivables;

import java.util.List;

import com.oneroof.shared.OriginalDataImporter;
import com.oneroof.shared.OriginalFile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Loads the receivables aging report and the deposit history. */
@Component
@Order(30)
class ReceivablesImporter implements OriginalDataImporter {

	static final String AGING_FILE = "01_corevia/financials/fin_ar_aging_cv.csv";
	static final String DEPOSIT_FILE = "01_corevia/financials/fin_collection_history_cv.csv";

	private final ReceivableRepository receivables;
	private final DepositRepository deposits;

	ReceivablesImporter(ReceivableRepository receivables, DepositRepository deposits) {
		this.receivables = receivables;
		this.deposits = deposits;
	}

	@Override
	@Transactional
	public List<ImportResult> importIfEmpty() {
		return List.of(importReceivables(), importDeposits());
	}

	private ImportResult importReceivables() {
		OriginalFile file = OriginalFile.open(AGING_FILE);
		if (this.receivables.count() > 0) {
			return new ImportResult(AGING_FILE, file.sha256(), 0, true);
		}
		List<Receivable> rows = file.rows()
			.stream()
			.map((row) -> new Receivable(row.text("ar_id"), row.textOrNull("agency_id"), row.textOrNull("customer_id"),
					row.date("invoice_date"), row.date("due_date"), row.number("amount_krw"),
					row.number("outstanding_krw"), Math.toIntExact(row.number("aging_days")), row.sourceRef()))
			.toList();
		this.receivables.saveAll(rows);
		return new ImportResult(AGING_FILE, file.sha256(), rows.size(), false);
	}

	private ImportResult importDeposits() {
		OriginalFile file = OriginalFile.open(DEPOSIT_FILE);
		if (this.deposits.count() > 0) {
			return new ImportResult(DEPOSIT_FILE, file.sha256(), 0, true);
		}
		List<Deposit> rows = file.rows()
			.stream()
			.map((row) -> new Deposit(row.text("deposit_id"), row.text("agency_id"), row.date("deposit_date"),
					row.number("amount_krw"), row.text("bank_account_no"), row.textOrNull("matched_ar_id"),
					row.sourceRef()))
			.toList();
		this.deposits.saveAll(rows);
		return new ImportResult(DEPOSIT_FILE, file.sha256(), rows.size(), false);
	}
}
