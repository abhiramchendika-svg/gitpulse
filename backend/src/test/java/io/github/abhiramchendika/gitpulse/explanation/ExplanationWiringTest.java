package io.github.abhiramchendika.gitpulse.explanation;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.abhiramchendika.gitpulse.config.AiProperties;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** The application starts with and without a key, and never exposes the key. */
class ExplanationWiringTest {

  @Nested
  @SpringBootTest(properties = "gitpulse.ai.api-key=")
  class WithoutAKey {
    @Autowired ExplanationService service;
    @Autowired AiProperties properties;

    @Test
    void featureIsOff() {
      assertThat(service.enabled()).isFalse();
      assertThat(properties.toString()).contains("apiKey=<none>");
    }
  }

  @Nested
  @SpringBootTest(properties = "gitpulse.ai.api-key=sk-ant-test-secret")
  class WithAKey {
    @Autowired ExplanationService service;
    @Autowired ExplanationModel model;
    @Autowired AiProperties properties;

    @Test
    void usesTheAnthropicSdk_andMasksTheKey() {
      assertThat(service.enabled()).isTrue();
      assertThat(model).isInstanceOf(AnthropicExplanationModel.class);
      assertThat(properties.toString()).doesNotContain("sk-ant-test-secret").contains("****");
    }
  }
}
