package com.nova.bank.accounts;

import com.nova.bank.common.ApiError;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

/** Return account errors without disclosing resource metadata. */
@RestControllerAdvice(assignableTypes=AccountController.class) public class AccountErrors {
    @ExceptionHandler(ResponseStatusException.class) ResponseEntity<ApiError> status(ResponseStatusException e) {
        int code=e.getStatusCode().value();
        return ResponseEntity.status(code).body(new ApiError(code,code==403?"Account Not Available":e.getReason(),Instant.now()));
    }
    @ExceptionHandler(MethodArgumentTypeMismatchException.class) ResponseEntity<ApiError> invalid(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(new ApiError(400,"Invalid request parameter",Instant.now()));
    }
}
