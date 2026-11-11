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

	/** The identifiers registered for every agency on the given date. */
	public List<AgencyIdentity> identitiesEffectiveOn(LocalDate date) {
		return this.identities.findAllByOrderByAgencyIdAscKindAscEffectiveFromAsc()
			.stream()
			.filter((identity) -> identity.isEffectiveOn(date))
			.toList();
	}
}
