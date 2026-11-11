package com.oneroof.receivables;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read access to receivables and deposits for other modules. */
@Service
@Transactional(readOnly = true)
public class ReceivablesLedger {

	private final DepositRepository deposits;

	ReceivablesLedger(DepositRepository deposits) {
		this.deposits = deposits;
	}

	/** Deposits received on or before the given date, oldest first. */
	public List<Deposit> depositsUpTo(LocalDate date) {
		return this.deposits.findByDepositDateLessThanEqualOrderByDepositDateAscDepositIdAsc(date);
	}
}
