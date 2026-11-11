package com.oneroof.approval;

/** Thrown when someone tries to decide a request their role has no authority over. */
public class AuthorityViolationException extends RuntimeException {

	public AuthorityViolationException(String message) {
		super(message);
	}
}
