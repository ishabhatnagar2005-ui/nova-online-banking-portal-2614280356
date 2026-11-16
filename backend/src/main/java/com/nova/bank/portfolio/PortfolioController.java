package com.nova.bank.portfolio;
import com.nova.bank.auth.AuthService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
/** Serve a portfolio scoped to the active authenticated session. */
@RestController @RequestMapping("/api/v1/portfolio") public class PortfolioController {
    private final PortfolioService portfolio;
    public PortfolioController(PortfolioService portfolio) { this.portfolio=portfolio; }
    @GetMapping("/summary") public PortfolioService.Summary summary(@AuthenticationPrincipal AuthService.Principal principal) { return portfolio.summary(principal); }
}
