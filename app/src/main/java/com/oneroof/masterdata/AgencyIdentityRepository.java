package com.oneroof.masterdata;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface AgencyIdentityRepository extends JpaRepository<AgencyIdentity, Long> {

	List<AgencyIdentity> findAllByOrderByAgencyIdAscKindAscEffectiveFromAsc();

	java.util.Optional<AgencyIdentity> findByAgencyIdAndKindAndEffectiveToIsNull(String agencyId, IdentityKind kind);

	List<AgencyIdentity> findByAgencyIdOrderByKindAscEffectiveFromAsc(String agencyId);
}
