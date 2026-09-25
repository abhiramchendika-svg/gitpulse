package io.github.abhiramchendika.gitpulse.api.dto;

import io.github.abhiramchendika.gitpulse.explanation.Fact;
import java.time.Instant;
import java.util.List;

/**
 * Response of {@code POST /api/v1/repositories/{owner}/{repo}/explanation}: AI-written sentences
 * that passed verification against GitPulse's own numbers. Not a metric: see docs/metrics.md.
 *
 * @param model the Claude model that wrote the text
 * @param window the analysis window the numbers describe
 * @param sentences verified sentences, each with the facts it is based on
 * @param sentencesRemoved sentences the model wrote that failed verification and are not shown
 */
public record ExplanationResponse(
    String repository,
    Instant generatedAt,
    String model,
    Window window,
    List<Sentence> sentences,
    int sentencesRemoved) {

  public record Window(Instant since, Instant until, boolean botsExcluded) {}

  public record Sentence(String text, List<Fact> basedOn) {}
}
