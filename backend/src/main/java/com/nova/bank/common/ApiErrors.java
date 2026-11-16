package com.nova.bank.common;
import com.nova.bank.auth.AuthFailure;
import java.time.Instant;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
/** Translate expected request failures without exposing internal details. */
@RestControllerAdvice public class ApiErrors {
    @ExceptionHandler(AuthFailure.class) ResponseEntity<ApiError> auth(AuthFailure e) { return ResponseEntity.status(e.status()).body(new ApiError(e.status(),e.getMessage(),Instant.now())); }
    @ExceptionHandler({MethodArgumentNotValidException.class,HttpMessageNotReadableException.class}) ResponseEntity<ApiError> invalid(Exception e) { return ResponseEntity.badRequest().body(new ApiError(400,"Please provide a valid User ID and password.",Instant.now())); }
}
