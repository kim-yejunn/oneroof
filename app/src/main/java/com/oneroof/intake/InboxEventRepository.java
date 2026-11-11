package com.oneroof.intake;

import org.springframework.data.jpa.repository.JpaRepository;

interface InboxEventRepository extends JpaRepository<InboxEvent, String> {
}
