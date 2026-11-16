package com.nova.bank.auth;
/** Describe a safe client-facing authentication failure. */
public class AuthFailure extends RuntimeException {
    private final int status;
    public AuthFailure(int status, String message) { super(message); this.status=status; }
    public int status() { return status; }
}
