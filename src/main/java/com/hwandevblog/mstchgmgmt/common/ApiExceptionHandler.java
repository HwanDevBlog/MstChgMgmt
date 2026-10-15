package com.hwandevblog.mstchgmgmt.common;

import com.hwandevblog.mstchgmgmt.domain.RevisionPolicy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    public record ErrorResponse(String code) { }

    @ExceptionHandler(RevisionPolicy.Denied.class)
    public ResponseEntity<ErrorResponse> denied(RevisionPolicy.Denied exception) {
        var status = exception.failure() == RevisionPolicy.Failure.FORBIDDEN
                ? HttpStatus.FORBIDDEN : HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(new ErrorResponse(exception.failure().name()));
    }
}
