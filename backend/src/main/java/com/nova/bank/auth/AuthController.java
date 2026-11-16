package com.nova.bank.auth;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
/** Expose login, activity and logout without leaking persistence models. */
@RestController @RequestMapping("/api/v1/auth") public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth=auth; }
    @PostMapping("/login") public AuthDtos.LoginResponse login(@Valid @RequestBody AuthDtos.LoginRequest request) { return auth.login(request); }
    @PostMapping("/activity") public AuthDtos.Message activity() { return new AuthDtos.Message("Session active"); }
    @PostMapping("/logout") public AuthDtos.Message logout(@AuthenticationPrincipal AuthService.Principal principal) { auth.logout(principal); return new AuthDtos.Message("Logged out successfully"); }
}
