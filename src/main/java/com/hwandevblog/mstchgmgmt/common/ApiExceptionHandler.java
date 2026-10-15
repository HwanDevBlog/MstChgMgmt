package com.hwandevblog.mstchgmgmt.common;

import com.hwandevblog.mstchgmgmt.domain.RevisionPolicy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> tooLarge() {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(new ErrorResponse("FILE_TOO_LARGE"));
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> responseStatus(org.springframework.web.server.ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(new ErrorResponse(
                exception.getReason() == null ? "REQUEST_FAILED" : exception.getReason()));
    }

    public record ErrorResponse(String code) { }

    @ExceptionHandler(RevisionPolicy.Denied.class)
    public ResponseEntity<ErrorResponse> denied(RevisionPolicy.Denied exception) {
        var status = exception.failure() == RevisionPolicy.Failure.FORBIDDEN
                ? HttpStatus.FORBIDDEN : HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(new ErrorResponse(exception.failure().name()));
    }
}
