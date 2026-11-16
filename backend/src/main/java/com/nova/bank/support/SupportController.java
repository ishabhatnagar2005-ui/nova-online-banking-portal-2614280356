package com.nova.bank.support;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.nova.bank.auth.AuthService;
import com.nova.bank.common.StrictJsonTypes;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Expose authenticated simulation endpoints. */
@RestController @RequestMapping("/api/v1") public class SupportController {
    private final SupportService service;
    public SupportController(SupportService service) { this.service=service; }
    public record Inquiry(@JsonDeserialize(using=StrictJsonTypes.Text.class) @NotBlank @Size(max=80) String topic,@JsonDeserialize(using=StrictJsonTypes.Text.class) @NotBlank @Size(max=160) String subject,@JsonDeserialize(using=StrictJsonTypes.Text.class) @NotBlank @Size(max=4000) String message) {}
    public record Chat(@JsonDeserialize(using=StrictJsonTypes.Text.class) @NotBlank @Size(max=500) String message) {}
    @PostMapping("/support/inquiries") @ResponseStatus(HttpStatus.CREATED)
    public SupportService.Confirmation inquiry(@AuthenticationPrincipal AuthService.Principal principal,@Valid @RequestBody Inquiry request) { return service.inquire(principal,request.topic(),request.subject(),request.message()); }
    @PostMapping("/chat") public SupportService.Reply chat(@AuthenticationPrincipal AuthService.Principal principal,@Valid @RequestBody Chat request) { return service.chat(principal,request.message()); }
}
