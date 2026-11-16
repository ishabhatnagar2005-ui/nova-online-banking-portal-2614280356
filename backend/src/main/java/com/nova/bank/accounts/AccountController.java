package com.nova.bank.accounts;

import com.nova.bank.auth.AuthService;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Expose authenticated account enquiry and demonstration statement downloads. */
@RestController @RequestMapping("/api/v1/accounts/{id}") public class AccountController {
    private final AccountService service;
    public AccountController(AccountService service) { this.service=service; }
    @GetMapping("/transactions") public AccountService.Result transactions(@AuthenticationPrincipal AuthService.Principal principal,@PathVariable UUID id,@RequestParam(required=false) String search,@RequestParam(required=false) String category,@RequestParam(required=false) String direction,@RequestParam(required=false) String startDate,@RequestParam(required=false) String endDate,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return service.transactions(principal,id,search,category,direction,startDate,endDate,page,size);
    }
    @GetMapping("/statement/download") public ResponseEntity<byte[]> statement(@AuthenticationPrincipal AuthService.Principal principal,@PathVariable UUID id) {
        String body=service.statement(principal,id); String filename=service.statementFilename(principal,id);
        return ResponseEntity.ok().contentType(new MediaType("text","plain",StandardCharsets.UTF_8)).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+filename+"\"").body(body.getBytes(StandardCharsets.UTF_8));
    }
}
