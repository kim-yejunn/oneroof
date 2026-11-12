package com.oneroof.clarification;

/** Sends a request to agencies through whatever channel the company uses (mail, portal, letter). */
public interface AgencyNoticePort {

	/** Returns the channel's reference for the notice. */
	String send(long clarificationId, String agencyIds, String title, String question);

	/** Whether notices go nowhere, so audit entries can say so. */
	boolean isSimulated();
}
