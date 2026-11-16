package com.nova.bank.admin;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.nova.bank.auth.AuthService;
import com.nova.bank.common.StrictJsonTypes;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Expose demo-only facilitator actions behind the administrator security rule. */
@RestController @RequestMapping("/api/v1/admin")
public class AdminController {
    private final AdminService service;
    public AdminController(AdminService service) { this.service=service; }
    @PostMapping("/reset") public Message reset(@AuthenticationPrincipal AuthService.Principal actor) {
        service.reset(actor); return new Message("Database successfully restored to original demonstration fixtures.");
    }
    @GetMapping("/audit") public List<AdminService.AuditEvent> audit(@AuthenticationPrincipal AuthService.Principal actor) { return service.auditEvents(actor); }
    @GetMapping("/simulation") public Simulation simulation(@AuthenticationPrincipal AuthService.Principal actor) { return new Simulation(service.probeEnabled(actor)); }
    @PostMapping("/simulation") public Simulation simulation(@AuthenticationPrincipal AuthService.Principal actor,@RequestBody Simulation request) {
        if (request.enabled()==null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"An enabled value is required.");
        service.setProbe(actor,request.enabled()); return request;
    }
    @PostMapping("/simulation/probe") public ResponseEntity<Message> probe(@AuthenticationPrincipal AuthService.Principal actor) {
        return service.probe(actor) ? ResponseEntity.status(503).body(new Message("Simulated service error; no customer service was affected.")) : ResponseEntity.ok(new Message("Demonstration probe is available."));
    }
    public record Message(String message) {}
    public record Simulation(@JsonDeserialize(using=StrictJsonTypes.BooleanValue.class) Boolean enabled) {}
}
