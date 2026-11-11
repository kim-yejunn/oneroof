package com.oneroof.receivables;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface DepositRepository extends JpaRepository<Deposit, String> {

	List<Deposit> findByDepositDateLessThanEqualOrderByDepositDateAscDepositIdAsc(LocalDate date);
}
