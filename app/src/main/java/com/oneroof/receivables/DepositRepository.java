package com.oneroof.receivables;

import org.springframework.data.jpa.repository.JpaRepository;

interface DepositRepository extends JpaRepository<Deposit, String> {
}
