package com.oneroof.relatedparty;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.oneroof.masterdata.AgencyDirectory;
import com.oneroof.masterdata.AgencyIdentity;
import com.oneroof.masterdata.IdentityKind;
import com.oneroof.receivables.Deposit;
import com.oneroof.receivables.ReceivablesLedger;
import com.oneroof.shared.Provenance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Determines related groups from what is known on a date. Groups are computed from the identity
 * history each time, never stored, so a correction to the master changes every later answer and
 * past dates can still be evaluated as they were.
 */
@Service
@Transactional(readOnly = true)
public class RelatedPartyService {

	private final AgencyDirectory agencyDirectory;
	private final ReceivablesLedger receivablesLedger;

	RelatedPartyService(AgencyDirectory agencyDirectory, ReceivablesLedger receivablesLedger) {
		this.agencyDirectory = agencyDirectory;
		this.receivablesLedger = receivablesLedger;
	}

	/**
	 * Compares registered identifiers and the bank accounts deposits actually came from, as the
	 * data dictionary requires.
	 */
	public RelatedPartyAnalysis analysisAsOf(LocalDate date) {
		List<IdentityEvidence> evidence = registeredEvidence(date);
		evidence.addAll(observedDepositEvidence(date));
		return RelatedPartyAnalyzer.analyze(date, evidence);
	}

	/**
	 * Compares registered identifiers only. Exists to show what a review limited to the master
	 * would miss; decisions use {@link #analysisAsOf(LocalDate)}.
	 */
	public RelatedPartyAnalysis masterOnlyAnalysisAsOf(LocalDate date) {
		return RelatedPartyAnalyzer.analyze(date, registeredEvidence(date));
	}

	private List<IdentityEvidence> registeredEvidence(LocalDate date) {
		List<IdentityEvidence> evidence = new ArrayList<>();
		for (AgencyIdentity identity : this.agencyDirectory.identitiesEffectiveOn(date)) {
			evidence.add(IdentityEvidence.of(identity.getAgencyId(), identity.getKind(), identity.getValue(),
					EvidenceBasis.MASTER_REGISTRATION, identity.getProvenance(), identity.getSourceRef()));
		}
		return evidence;
	}

	private List<IdentityEvidence> observedDepositEvidence(LocalDate date) {
		List<IdentityEvidence> evidence = new ArrayList<>();
		for (Deposit deposit : this.receivablesLedger.depositsUpTo(date)) {
			evidence.add(IdentityEvidence.of(deposit.getAgencyId(), IdentityKind.BANK_ACCOUNT, deposit.getBankAccountNo(),
					EvidenceBasis.OBSERVED_DEPOSIT, Provenance.ORIGINAL, deposit.getSourceRef()));
		}
		return evidence;
	}
}
