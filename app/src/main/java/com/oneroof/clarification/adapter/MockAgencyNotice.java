package com.oneroof.clarification.adapter;

import com.oneroof.clarification.AgencyNoticePort;
import org.springframework.stereotype.Component;

/** Simulated notice channel: records a reference and sends nothing. Replies are entered from the console. */
@Component
class MockAgencyNotice implements AgencyNoticePort {

	@Override
	public String send(long clarificationId, String agencyIds, String title, String question) {
		return "MOCK-NOTICE-%06d".formatted(clarificationId);
	}

	@Override
	public boolean isSimulated() {
		return true;
	}
}
