package com.oneroof.console;

import java.util.Map;

import com.oneroof.approval.AuthorityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns domain refusals into HTTP answers the console can show as they are. */
@RestControllerAdvice
class ApiErrors {

	@ExceptionHandler(AuthorityViolationException.class)
	ResponseEntity<Map<String, String>> authority(AuthorityViolationException ex) {
		return error(HttpStatus.FORBIDDEN, "AUTHORITY", ex.getMessage());
	}

	@ExceptionHandler(IllegalStateException.class)
	ResponseEntity<Map<String, String>> conflict(IllegalStateException ex) {
		return error(HttpStatus.CONFLICT, "STATE", ex.getMessage());
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException ex) {
		return error(HttpStatus.BAD_REQUEST, "REQUEST", ex.getMessage());
	}

	@ExceptionHandler(ReadModel.NotFoundException.class)
	ResponseEntity<Map<String, String>> notFound(ReadModel.NotFoundException ex) {
		return error(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
	}

	private static ResponseEntity<Map<String, String>> error(HttpStatus status, String code, String message) {
		return ResponseEntity.status(status).body(Map.of("code", code, "message", message));
	}
}
