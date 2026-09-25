package io.github.abhiramchendika.gitpulse.explanation;

import java.util.List;

/**
 * What the model returned, before verification. Nothing in here is trusted yet.
 *
 * @param sentences plain-English sentences, each naming the facts it is based on
 */
public record ExplanationDraft(List<Sentence> sentences) {

  public ExplanationDraft {
    sentences = sentences == null ? List.of() : List.copyOf(sentences);
  }

  /**
   * @param text the sentence
   * @param facts ids of the facts the sentence uses
   */
  public record Sentence(String text, List<String> facts) {
    public Sentence {
      facts = facts == null ? List.of() : List.copyOf(facts);
    }
  }
}
