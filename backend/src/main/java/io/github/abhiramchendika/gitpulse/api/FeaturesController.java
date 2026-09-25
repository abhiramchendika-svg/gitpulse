package io.github.abhiramchendika.gitpulse.api;

import io.github.abhiramchendika.gitpulse.api.dto.FeaturesResponse;
import io.github.abhiramchendika.gitpulse.explanation.ExplanationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tells the frontend which optional features are available on this server. */
@RestController
public class FeaturesController {

  private final ExplanationService explanationService;

  public FeaturesController(ExplanationService explanationService) {
    this.explanationService = explanationService;
  }

  @GetMapping("/api/v1/features")
  public FeaturesResponse features() {
    return new FeaturesResponse(explanationService.enabled());
  }
}
