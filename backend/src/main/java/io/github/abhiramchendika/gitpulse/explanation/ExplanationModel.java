package io.github.abhiramchendika.gitpulse.explanation;

/**
 * The language model behind explanations. An interface so tests use a fake: no test ever calls a
 * paid API.
 */
public interface ExplanationModel {

  /**
   * @throws ExplanationUnavailableException the model could not be reached
   * @throws UnreliableExplanationException the model answered unusably (e.g. declined)
   */
  Reply draft(String systemPrompt, String userMessage);

  /**
   * @param model the model that actually answered (may differ from the requested one after a
   *     refusal fallback)
   */
  record Reply(ExplanationDraft draft, String model) {}
}
