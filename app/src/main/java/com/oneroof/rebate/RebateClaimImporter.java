package com.oneroof.rebate;

import java.time.YearMonth;
import java.util.List;

import com.oneroof.shared.OriginalDataImporter;
import com.oneroof.shared.OriginalFile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Loads the rebate payment history: who was paid how much for which month, and who approved it. */
@Component
@Order(20)
class RebateClaimImporter implements OriginalDataImporter {

	static final String FILE = "01_corevia/sales/sales_rebate_payment_cv.csv";

	private final RebateClaimRepository claims;

	RebateClaimImporter(RebateClaimRepository claims) {
		this.claims = claims;
	}

	@Override
	@Transactional
	public List<ImportResult> importIfEmpty() {
		OriginalFile file = OriginalFile.open(FILE);
		if (this.claims.count() > 0) {
			return List.of(new ImportResult(FILE, file.sha256(), 0, true));
		}
		List<RebateClaim> newClaims = file.rows()
			.stream()
			.map((row) -> new RebateClaim(row.text("rebate_id"), row.text("agency_id"),
					YearMonth.parse(row.text("period_ym")), row.number("sales_performance_krw"),
					row.number("rebate_amount_krw"), row.text("approval_status"), row.textOrNull("approver"),
					row.dateOrNull("payment_date"), row.sourceRef()))
			.toList();
		this.claims.saveAll(newClaims);
		return List.of(new ImportResult(FILE, file.sha256(), newClaims.size(), false));
	}
}
