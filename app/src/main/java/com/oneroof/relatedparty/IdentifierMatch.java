package com.oneroof.relatedparty;

import com.oneroof.masterdata.IdentityKind;

/**
 * Two agencies sharing one identifier, with the evidence on each side.
 *
 * @param kind the identifier that matches
 * @param left the evidence for the agency with the smaller id
 * @param right the evidence for the agency with the larger id
 */
public record IdentifierMatch(IdentityKind kind, IdentityEvidence left, IdentityEvidence right) {

	/** Whether both sides are registered in the master, as opposed to observed in deposits. */
	public boolean isRegisteredOnBothSides() {
		return this.left.basis() == EvidenceBasis.MASTER_REGISTRATION
				&& this.right.basis() == EvidenceBasis.MASTER_REGISTRATION;
	}
}
