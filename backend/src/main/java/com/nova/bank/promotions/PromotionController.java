package com.nova.bank.promotions;

import com.nova.bank.auth.AuthService;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Expose the authenticated catalogue and simulated interest action. */
@RestController @RequestMapping("/api/v1/promotions") public class PromotionController {
    private final PromotionService service;
    public PromotionController(PromotionService service) { this.service=service; }
    @GetMapping public PromotionService.Catalogue list(@AuthenticationPrincipal AuthService.Principal principal) { return service.list(principal); }
    @PostMapping("/{promotionId}/express-interest") public PromotionService.Confirmation interest(@AuthenticationPrincipal AuthService.Principal principal,@PathVariable UUID promotionId) { return service.expressInterest(principal,promotionId); }
}
