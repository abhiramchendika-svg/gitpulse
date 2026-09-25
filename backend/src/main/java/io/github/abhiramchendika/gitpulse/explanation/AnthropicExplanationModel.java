package io.github.abhiramchendika.gitpulse.explanation;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import io.github.abhiramchendika.gitpulse.config.AiProperties;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** {@link ExplanationModel} backed by Claude through the official Anthropic Java SDK. */
public class AnthropicExplanationModel implements ExplanationModel, AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(AnthropicExplanationModel.class);

  /** Opts into server-side refusal fallbacks ({@code fallbacks: "default"}). */
  static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";

  /** The JSON shape the model must return (structured outputs guarantee it parses). */
  static final JsonOutputFormat.Schema SCHEMA =
      JsonOutputFormat.Schema.builder()
          .putAdditionalProperty("type", JsonValue.from("object"))
          .putAdditionalProperty(
              "properties",
              JsonValue.from(
                  Map.of(
                      "sentences",
                      Map.of(
                          "type",
                          "array",
                          "items",
                          Map.of(
                              "type",
                              "object",
                              "properties",
                              Map.of(
                                  "text", Map.of("type", "string"),
                                  "facts",
                                      Map.of("type", "array", "items", Map.of("type", "string"))),
                              "required",
                              List.of("text", "facts"),
                              "additionalProperties",
                              false)))))
          .putAdditionalProperty("required", JsonValue.from(List.of("sentences")))
          .putAdditionalProperty("additionalProperties", JsonValue.from(false))
          .build();

  private final AnthropicClient client;
  private final AiProperties properties;
  private final JsonMapper json;

  public AnthropicExplanationModel(AiProperties properties, JsonMapper json) {
    this.properties = properties;
    this.json = json;
    this.client =
        AnthropicOkHttpClient.builder()
            .apiKey(properties.apiKey())
            .baseUrl(properties.baseUrl().toString())
            .timeout(properties.timeout())
            .build();
  }

  @Override
  public Reply draft(String systemPrompt, String userMessage) {
    Message message;
    try {
      message = client.messages().create(params(systemPrompt, userMessage));
    } catch (UnauthorizedException | PermissionDeniedException e) {
      log.error("Anthropic rejected the configured ANTHROPIC_API_KEY (HTTP {}).", e.statusCode());
      throw new ExplanationUnavailableException(
          "The server's Anthropic credentials were rejected.", e);
    } catch (AnthropicServiceException e) {
      log.warn("Anthropic API error: HTTP {} {}", e.statusCode(), e.errorType().orElse(null));
      throw new ExplanationUnavailableException("The Anthropic API returned an error.", e);
    } catch (AnthropicException e) {
      // Network problems and timeouts (after the SDK's own retries).
      log.warn("Anthropic API unreachable: {}", e.getClass().getSimpleName());
      throw new ExplanationUnavailableException("The Anthropic API could not be reached.", e);
    }
    return new Reply(parse(message), message.model().asString());
  }

  MessageCreateParams params(String systemPrompt, String userMessage) {
    MessageCreateParams.Builder builder =
        MessageCreateParams.builder()
            .model(properties.model())
            .maxTokens(properties.maxTokens())
            .system(systemPrompt)
            // Rephrasing given numbers is easy: low effort keeps thinking (and cost) small.
            // Thinking itself is left at the model default (adaptive on Claude Opus 5).
            .outputConfig(
                OutputConfig.builder()
                    .effort(OutputConfig.Effort.LOW)
                    .format(JsonOutputFormat.builder().schema(SCHEMA).build())
                    .build())
            .addUserMessage(userMessage);
    if (properties.refusalFallback()) {
      builder
          .putAdditionalHeader("anthropic-beta", FALLBACK_BETA)
          .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
    }
    return builder.build();
  }

  ExplanationDraft parse(Message message) {
    StopReason stop = message.stopReason().orElse(null);
    if (StopReason.REFUSAL.equals(stop)) {
      throw new UnreliableExplanationException("The model declined to answer.");
    }
    if (StopReason.MAX_TOKENS.equals(stop)) {
      throw new UnreliableExplanationException("The model's answer was cut off.");
    }
    String text =
        message.content().stream()
            .map(ContentBlock::text)
            .flatMap(java.util.Optional::stream)
            .map(t -> t.text())
            .collect(Collectors.joining());
    try {
      return json.readValue(text, ExplanationDraft.class);
    } catch (JacksonException e) {
      throw new UnreliableExplanationException("The model's answer was not valid JSON.");
    }
  }

  @Override
  public void close() {
    client.close();
  }
}
