package com.oneroof.console.web;

import com.oneroof.approval.AuthorityViolationException;
import com.oneroof.console.api.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns domain refusals into HTTP answers the console can show as they are. */
@RestControllerAdvice(basePackageClasses = ApiErrors.class)
class ApiErrors {

	@ExceptionHandler(AuthorityViolationException.class)
	ResponseEntity<ApiErrorResponse> authority(AuthorityViolationException ex) {
		return error(HttpStatus.FORBIDDEN, "AUTHORITY", ex.getMessage());
	}

	@ExceptionHandler(SecurityException.class)
	ResponseEntity<ApiErrorResponse> forbidden(SecurityException ex) {
		return error(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage());
	}

	@ExceptionHandler(IllegalStateException.class)
	ResponseEntity<ApiErrorResponse> conflict(IllegalStateException ex) {
		return error(HttpStatus.CONFLICT, "STATE", ex.getMessage());
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ResponseEntity<ApiErrorResponse> badRequest(IllegalArgumentException ex) {
		return error(HttpStatus.BAD_REQUEST, "REQUEST", ex.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiErrorResponse> invalid(MethodArgumentNotValidException ex) {
		return error(HttpStatus.BAD_REQUEST, "REQUEST", "요청 값이 올바르지 않음: " + ex.getBindingResult()
			.getFieldErrors()
			.stream()
			.map((field) -> field.getField() + " " + field.getDefaultMessage())
			.toList());
	}

	@ExceptionHandler(MissingRequestHeaderException.class)
	ResponseEntity<ApiErrorResponse> missingHeader(MissingRequestHeaderException ex) {
		return error(HttpStatus.BAD_REQUEST, "REQUEST", ex.getHeaderName() + " 헤더로 행동하는 역할을 보내야 함");
	}

	@ExceptionHandler(ReadModel.NotFoundException.class)
	ResponseEntity<ApiErrorResponse> notFound(ReadModel.NotFoundException ex) {
		return error(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
	}

	private static ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message) {
		return ResponseEntity.status(status).body(new ApiErrorResponse(code, message));
	}
}
