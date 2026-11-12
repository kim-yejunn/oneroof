package com.oneroof.masterdata;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read access to the agency master for other modules. */
@Service
@Transactional(readOnly = true)
public class AgencyDirectory {

	private final AgencyRepository agencies;
	private final AgencyIdentityRepository identities;

	AgencyDirectory(AgencyRepository agencies, AgencyIdentityRepository identities) {
		this.agencies = agencies;
		this.identities = identities;
	}

	public List<Agency> agencies() {
		return this.agencies.findAll(org.springframework.data.domain.Sort.by("agencyId"));
	}

	public java.util.Optional<Agency> agency(String agencyId) {
		return this.agencies.findById(agencyId);
	}

	/** The value registered for one identifier of one agency on the given date. */
	public java.util.Optional<AgencyIdentity> registered(String agencyId, IdentityKind kind, LocalDate date) {
		return this.identities.findByAgencyIdOrderByKindAscEffectiveFromAsc(agencyId)
			.stream()
			.filter((identity) -> identity.getKind() == kind && identity.isEffectiveOn(date))
			.findFirst();
	}

	/** The identifiers registered for every agency on the given date. */
	public List<AgencyIdentity> identitiesEffectiveOn(LocalDate date) {
		return this.identities.findAllByOrderByAgencyIdAscKindAscEffectiveFromAsc()
			.stream()
			.filter((identity) -> identity.isEffectiveOn(date))
			.toList();
	}
}
