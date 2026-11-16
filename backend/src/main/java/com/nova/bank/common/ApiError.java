package com.nova.bank.common;
import java.time.Instant;
/** Return consistent, non-sensitive error details. */
public record ApiError(int status, String message, Instant timestamp) {}
