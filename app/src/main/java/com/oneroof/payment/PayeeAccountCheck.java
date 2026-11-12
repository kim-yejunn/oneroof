package com.oneroof.payment;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.oneroof.masterdata.AgencyDirectory;
import com.oneroof.masterdata.AgencyIdentity;
import com.oneroof.masterdata.IdentityKind;
import com.oneroof.receivables.Deposit;
import com.oneroof.receivables.ReceivablesLedger;
import org.springframework.stereotype.Component;

/**
 * Decides which account a payment goes to and whether it must be confirmed first.
 *
 * <p>[교육용 가정] 원본의 회수(입금) 계좌를 지급 계좌로 단정하지 않는다(W07 업무 가정). 지급은 마스터에 등록된
 * 정산계좌로 하되, 그 대리점의 실제 입금이 다른 계좌에서 들어온 적이 있으면 어느 계좌가 맞는지 대리점에
 * 확인한 뒤 보낸다.
 */
@Component
class PayeeAccountCheck {

	static final List<PaymentStatus> CONFIRMED_BY_RELEASE = List.of(PaymentStatus.REQUESTED, PaymentStatus.SENT,
			PaymentStatus.PAID);

	private final AgencyDirectory agencyDirectory;
	private final ReceivablesLedger receivablesLedger;
	private final PaymentInstructionRepository instructions;

	PayeeAccountCheck(AgencyDirectory agencyDirectory, ReceivablesLedger receivablesLedger,
			PaymentInstructionRepository instructions) {
		this.agencyDirectory = agencyDirectory;
		this.receivablesLedger = receivablesLedger;
		this.instructions = instructions;
	}

	Result check(String agencyId, LocalDate today, List<Deposit> deposits) {
		Optional<AgencyIdentity> registered = this.agencyDirectory.registered(agencyId, IdentityKind.BANK_ACCOUNT, today);
		if (registered.isEmpty()) {
			return new Result(null, "마스터에 정산계좌가 등록되어 있지 않음");
		}
		String account = registered.get().getValue();
		List<Deposit> elsewhere = deposits.stream()
			.filter((deposit) -> deposit.getAgencyId().equals(agencyId))
			.filter((deposit) -> !normalize(deposit.getBankAccountNo()).equals(normalize(account)))
			.toList();
		if (elsewhere.isEmpty() || this.instructions.existsByAgencyIdAndPayeeAccountAndHoldReasonIsNotNullAndStatusIn(
				agencyId, account, CONFIRMED_BY_RELEASE)) {
			return new Result(account, null);
		}
		Deposit first = elsewhere.get(0);
		return new Result(account,
				"등록 정산계좌 '%s' 와 실제 입금 계좌 '%s' 가 다름 (%s 외 %d건, %s) — 지급 계좌 미확인".formatted(account,
						first.getBankAccountNo(), first.getDepositDate(), elsewhere.size() - 1, first.getSourceRef()));
	}

	List<Deposit> depositsUpTo(LocalDate today) {
		return this.receivablesLedger.depositsUpTo(today);
	}

	private static String normalize(String account) {
		return account.replaceAll("[\\s-]", "");
	}

	/**
	 * @param payeeAccount the account to pay, {@code null} when none is registered
	 * @param holdReason why it must be confirmed first, {@code null} when it can be sent
	 */
	record Result(String payeeAccount, String holdReason) {
	}
}
