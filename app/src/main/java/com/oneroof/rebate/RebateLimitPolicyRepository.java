package com.oneroof.rebate;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface RebateLimitPolicyRepository extends JpaRepository<RebateLimitPolicy, Long> {

	Optional<RebateLimitPolicy> findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(LocalDate date);
}
