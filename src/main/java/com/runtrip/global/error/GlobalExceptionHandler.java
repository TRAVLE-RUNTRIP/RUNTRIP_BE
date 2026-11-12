package com.runtrip.global.error;

import com.runtrip.global.rsdata.RsData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<RsData<Void>> handleApiException(ApiException ex) {
		ErrorCode errorCode = ex.getErrorCode();
		return ResponseEntity.status(errorCode.getStatus()).body(RsData.fail(errorCode));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<RsData<Void>> handleUnexpected(Exception ex) {
		log.error("처리되지 않은 예외", ex);
		return ResponseEntity.status(ErrorCode.SERVER_ERROR.getStatus()).body(RsData.fail(ErrorCode.SERVER_ERROR));
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		FieldError fieldError = ex.getBindingResult().getFieldError();
		String message = fieldError != null ? fieldError.getDefaultMessage() : ErrorCode.VALIDATION_ERROR.getMessage();
		return ResponseEntity.status(status).headers(headers).body(RsData.fail(ErrorCode.VALIDATION_ERROR, message));
	}

	// @RequestParam 등 메서드 인자 검증 실패도 지정한 메시지를 그대로 내려준다
	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		String message = ex.getAllErrors().stream()
				.map(MessageSourceResolvable::getDefaultMessage)
				.findFirst()
				.orElse(ErrorCode.VALIDATION_ERROR.getMessage());
		return ResponseEntity.status(status).headers(headers).body(RsData.fail(ErrorCode.VALIDATION_ERROR, message));
	}

	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		return ResponseEntity.status(statusCode).headers(headers).body(RsData.fail(toErrorCode(statusCode)));
	}

	private ErrorCode toErrorCode(HttpStatusCode status) {
		if (status.is5xxServerError()) {
			return ErrorCode.SERVER_ERROR;
		}
		if (status.isSameCodeAs(HttpStatus.NOT_FOUND)) {
			return ErrorCode.RESOURCE_NOT_FOUND;
		}
		if (status.isSameCodeAs(HttpStatus.METHOD_NOT_ALLOWED)) {
			return ErrorCode.METHOD_NOT_ALLOWED;
		}
		return ErrorCode.VALIDATION_ERROR;
	}
}
