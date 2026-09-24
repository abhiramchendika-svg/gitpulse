package io.github.abhiramchendika.gitpulse.api;

import io.github.abhiramchendika.gitpulse.api.dto.RateLimitResponse;
import io.github.abhiramchendika.gitpulse.service.RateLimitService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rate-limit")
public class RateLimitController {

  private final RateLimitService rateLimitService;

  public RateLimitController(RateLimitService rateLimitService) {
    this.rateLimitService = rateLimitService;
  }

  @GetMapping
  public RateLimitResponse getRateLimit() {
    return rateLimitService.getRateLimit();
  }
}
