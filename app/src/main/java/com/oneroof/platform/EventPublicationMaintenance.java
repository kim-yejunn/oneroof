package com.oneroof.platform;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps the outbox healthy while the app runs. A listener that failed (a database hiccup, a
 * deployment cut short) leaves its publication incomplete; it is handed to the listener again once
 * it is clearly not just slow. Restarts already do the same for everything outstanding. Completed
 * publications carry no further meaning (the audit log keeps the history) and are removed after a day.
 *
 * <p>Listeners tolerate seeing an event twice: inbox ids, unique references and status checks make
 * a repeated step a recorded no-op.
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "oneroof.events.maintenance.enabled", havingValue = "true", matchIfMissing = true)
class EventPublicationMaintenance {

	private static final Logger log = LoggerFactory.getLogger(EventPublicationMaintenance.class);

	private final IncompleteEventPublications incomplete;
	private final CompletedEventPublications completed;
	private final Duration retryAfter;
	private final Duration keepCompleted;

	EventPublicationMaintenance(IncompleteEventPublications incomplete, CompletedEventPublications completed,
			@Value("${oneroof.events.retry-after:PT2M}") Duration retryAfter,
			@Value("${oneroof.events.keep-completed:P1D}") Duration keepCompleted) {
		this.incomplete = incomplete;
		this.completed = completed;
		this.retryAfter = retryAfter;
		this.keepCompleted = keepCompleted;
	}

	@Scheduled(fixedDelayString = "${oneroof.events.maintenance-interval:PT1M}", initialDelayString = "PT1M")
	void maintain() {
		try {
			this.incomplete.resubmitIncompletePublicationsOlderThan(this.retryAfter);
			this.completed.deletePublicationsOlderThan(this.keepCompleted);
		}
		catch (RuntimeException ex) {
			log.warn("Event publication maintenance failed, will try again: {}", ex.toString());
		}
	}
}
