package io.github.abhiramchendika.gitpulse.explanation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import io.github.abhiramchendika.gitpulse.config.AiProperties;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Runs the real Anthropic SDK against a local stand-in for the Messages API, so the request
 * GitPulse sends and its handling of each kind of answer are tested without a key or any cost.
 */
class AnthropicExplanationModelTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private HttpServer server;
  private final AtomicReference<String> requestBody = new AtomicReference<>();
  private final AtomicReference<String> betaHeader = new AtomicReference<>();
  private final AtomicReference<String> apiKeyHeader = new AtomicReference<>();
  private volatile int status = 200;
  private volatile String responseBody = "";
  private AnthropicExplanationModel model;

  @BeforeEach
  void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/v1/messages",
        exchange -> {
          requestBody.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          betaHeader.set(exchange.getRequestHeaders().getFirst("anthropic-beta"));
          apiKeyHeader.set(exchange.getRequestHeaders().getFirst("x-api-key"));
          byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(status, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    model = new AnthropicExplanationModel(properties(true), JSON);
  }

  @AfterEach
  void stop() {
    model.close();
    server.stop(0);
  }

  private AiProperties properties(boolean fallback) {
    return new AiProperties(
        "test-key",
        URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
        "claude-opus-5",
        20,
        8000,
        Duration.ofSeconds(5),
        fallback);
  }

  private static String message(String stopReason, String text) {
    String content =
        text == null
            ? "[]"
            : "[{\"type\":\"text\",\"text\":" + JSON.writeValueAsString(text) + "}]";
    return """
        {"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-5",
         "content":%s,"stop_reason":"%s","stop_sequence":null,
         "usage":{"input_tokens":10,"output_tokens":20}}
        """
        .formatted(content, stopReason);
  }

  @Test
  void sendsTheConfiguredRequest_andParsesTheStructuredAnswer() {
    responseBody =
        message(
            "end_turn",
            "{\"sentences\":[{\"text\":\"There were 240 commits.\",\"facts\":[\"commits.total\"]}]}");

    ExplanationModel.Reply reply = model.draft("system rules", "facts json");

    assertThat(reply.model()).isEqualTo("claude-opus-5");
    assertThat(reply.draft().sentences())
        .containsExactly(
            new ExplanationDraft.Sentence(
                "There were 240 commits.", java.util.List.of("commits.total")));

    JsonNode body = JSON.readTree(requestBody.get());
    assertThat(body.get("model").asString()).isEqualTo("claude-opus-5");
    assertThat(body.get("max_tokens").asInt()).isEqualTo(8000);
    assertThat(body.get("system").asString()).isEqualTo("system rules");
    assertThat(body.get("output_config").get("effort").asString()).isEqualTo("low");
    assertThat(body.get("output_config").get("format").get("type").asString())
        .isEqualTo("json_schema");
    assertThat(
            body.get("output_config").get("format").get("schema").get("required").get(0).asString())
        .isEqualTo("sentences");
    assertThat(body.get("fallbacks").asString()).isEqualTo("default");
    assertThat(betaHeader.get()).contains(AnthropicExplanationModel.FALLBACK_BETA);
    assertThat(apiKeyHeader.get()).isEqualTo("test-key");
    // Thinking is left at the model's default (adaptive on Claude Opus 5).
    assertThat(body.has("thinking")).isFalse();
  }

  @Test
  void fallbackCanBeTurnedOff() {
    model.close();
    model = new AnthropicExplanationModel(properties(false), JSON);
    responseBody = message("end_turn", "{\"sentences\":[]}");

    model.draft("s", "u");

    assertThat(JSON.readTree(requestBody.get()).has("fallbacks")).isFalse();
    assertThat(betaHeader.get()).isNull();
  }

  @Test
  void refusal_isReportedAsUnreliable() {
    responseBody = message("refusal", null);

    assertThatThrownBy(() -> model.draft("s", "u"))
        .isInstanceOf(UnreliableExplanationException.class)
        .hasMessageContaining("declined");
  }

  @Test
  void truncatedOrMalformedAnswers_areUnreliable() {
    responseBody = message("max_tokens", "{\"sentences\":[{\"text\":\"There were");
    assertThatThrownBy(() -> model.draft("s", "u"))
        .isInstanceOf(UnreliableExplanationException.class)
        .hasMessageContaining("cut off");

    responseBody = message("end_turn", "not json");
    assertThatThrownBy(() -> model.draft("s", "u"))
        .isInstanceOf(UnreliableExplanationException.class)
        .hasMessageContaining("not valid JSON");
  }

  @Test
  void rejectedKey_isUnavailable() {
    status = 401;
    responseBody =
        "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key\"}}";

    assertThatThrownBy(() -> model.draft("s", "u"))
        .isInstanceOf(ExplanationUnavailableException.class)
        .hasMessageContaining("credentials");
  }

  @Test
  void badRequest_isUnavailable() {
    status = 400;
    responseBody =
        "{\"type\":\"error\",\"error\":{\"type\":\"invalid_request_error\",\"message\":\"bad\"}}";

    assertThatThrownBy(() -> model.draft("s", "u"))
        .isInstanceOf(ExplanationUnavailableException.class);
  }
}
