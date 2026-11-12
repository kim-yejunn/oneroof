package com.oneroof.masterdata;

import org.springframework.data.jpa.repository.JpaRepository;

interface MasterChangeRepository extends JpaRepository<MasterChange, Long> {

	boolean existsByEventId(String eventId);
}
