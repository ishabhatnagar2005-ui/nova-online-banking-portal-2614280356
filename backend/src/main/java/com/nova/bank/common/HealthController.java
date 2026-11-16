package com.nova.bank.common;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
/** Expose a public liveness endpoint. */
@RestController public class HealthController {
    @GetMapping("/api/health") public Map<String,String> health() { return Map.of("status","UP"); }
}
