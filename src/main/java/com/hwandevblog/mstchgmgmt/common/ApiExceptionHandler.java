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
        String trace=exception.getStatusCode().value()==409?java.util.UUID.randomUUID().toString():null;
        if(trace!=null)org.slf4j.LoggerFactory.getLogger(ApiExceptionHandler.class).warn("Request conflict code={} traceId={}",exception.getReason(),trace);
        return ResponseEntity.status(exception.getStatusCode()).body(new ErrorResponse(
                exception.getReason() == null ? "REQUEST_FAILED" : exception.getReason(),trace));
    }

    public record ErrorResponse(String code,String traceId) {
        public ErrorResponse(String code){this(code,null);}
    }
    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    public ResponseEntity<ErrorResponse> database(org.springframework.dao.DataAccessException exception) {
        String trace=java.util.UUID.randomUUID().toString();
        org.slf4j.LoggerFactory.getLogger(ApiExceptionHandler.class).error("Database transaction failed traceId="+trace,exception);
        boolean duplicate=exception instanceof org.springframework.dao.DuplicateKeyException;
        return ResponseEntity.status(duplicate?HttpStatus.CONFLICT:HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(duplicate?"CREATE_EXISTS":"TRANSACTION_FAILED",trace));
    }

    @ExceptionHandler(RevisionPolicy.Denied.class)
    public ResponseEntity<ErrorResponse> denied(RevisionPolicy.Denied exception) {
        var status = exception.failure() == RevisionPolicy.Failure.FORBIDDEN
                ? HttpStatus.FORBIDDEN : HttpStatus.CONFLICT;
        String trace=status==HttpStatus.CONFLICT?java.util.UUID.randomUUID().toString():null;
        if(trace!=null)org.slf4j.LoggerFactory.getLogger(ApiExceptionHandler.class).warn("Policy conflict code={} traceId={}",exception.failure(),trace);
        return ResponseEntity.status(status).body(new ErrorResponse(exception.failure().name(),trace));
    }
}
