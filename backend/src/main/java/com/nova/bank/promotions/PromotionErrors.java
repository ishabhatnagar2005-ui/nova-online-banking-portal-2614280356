package com.nova.bank.promotions;

import com.nova.bank.common.ApiError;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

/** Return consistent errors without revealing unavailable campaign details. */
@RestControllerAdvice(assignableTypes=PromotionController.class) public class PromotionErrors {
    @ExceptionHandler(ResponseStatusException.class) ResponseEntity<ApiError> status(ResponseStatusException e) { int code=e.getStatusCode().value(); return ResponseEntity.status(code).body(new ApiError(code,"Offer not available",Instant.now())); }
    @ExceptionHandler(MethodArgumentTypeMismatchException.class) ResponseEntity<ApiError> invalid(MethodArgumentTypeMismatchException e) { return ResponseEntity.badRequest().body(new ApiError(400,"Invalid offer identifier",Instant.now())); }
}
