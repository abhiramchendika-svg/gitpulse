package io.github.abhiramchendika.gitpulse.config;

import io.github.abhiramchendika.gitpulse.explanation.AnthropicExplanationModel;
import io.github.abhiramchendika.gitpulse.explanation.ExplanationModel;
import io.github.abhiramchendika.gitpulse.explanation.ExplanationsDisabledException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/** Wires the language model behind explanations, or a stand-in when no API key is configured. */
@Configuration(proxyBeanMethods = false)
public class AiConfig {

  @Bean
  ExplanationModel explanationModel(AiProperties properties, JsonMapper json) {
    if (!properties.enabled()) {
      // Never reached in practice: ExplanationService checks enabled() first.
      return (system, user) -> {
        throw new ExplanationsDisabledException();
      };
    }
    return new AnthropicExplanationModel(properties, json);
  }
}
