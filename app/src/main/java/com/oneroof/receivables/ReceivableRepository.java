package com.oneroof.receivables;

import org.springframework.data.jpa.repository.JpaRepository;

interface ReceivableRepository extends JpaRepository<Receivable, String> {
}
