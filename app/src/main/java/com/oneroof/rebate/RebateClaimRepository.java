package com.oneroof.rebate;

import org.springframework.data.jpa.repository.JpaRepository;

interface RebateClaimRepository extends JpaRepository<RebateClaim, String> {
}
