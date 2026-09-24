package io.github.abhiramchendika.gitpulse.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.abhiramchendika.gitpulse.api.dto.RateLimitResponse;
import io.github.abhiramchendika.gitpulse.github.exception.GitHubRateLimitException;
import io.github.abhiramchendika.gitpulse.github.exception.GitHubUnavailableException;
import io.github.abhiramchendika.gitpulse.service.RateLimitService;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Web-layer test: only the controller + exception handler are loaded; the service is mocked. */
@WebMvcTest(RateLimitController.class)
class RateLimitControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private RateLimitService rateLimitService;

  @Test
  void returnsQuota() throws Exception {
    Instant reset = Instant.parse("2026-09-25T10:00:00Z");
    when(rateLimitService.getRateLimit())
        .thenReturn(
            new RateLimitResponse(
                true,
                new RateLimitResponse.Quota(5000, 4990, 10, reset),
                new RateLimitResponse.Quota(30, 30, 0, reset)));

    mockMvc
        .perform(get("/api/v1/rate-limit"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.authenticated").value(true))
        .andExpect(jsonPath("$.core.remaining").value(4990))
        .andExpect(jsonPath("$.core.resetAt").value("2026-09-25T10:00:00Z"))
        .andExpect(jsonPath("$.search.limit").value(30));
  }

  @Test
  void rateLimited_returns429ProblemWithRetryAfter() throws Exception {
    when(rateLimitService.getRateLimit())
        .thenThrow(new GitHubRateLimitException(null, Duration.ofSeconds(42)));

    mockMvc
        .perform(get("/api/v1/rate-limit"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("Retry-After", "42"))
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
        .andExpect(jsonPath("$.status").value(429));
  }

  @Test
  void githubDown_returns503WithoutLeakingInternals() throws Exception {
    when(rateLimitService.getRateLimit())
        .thenThrow(
            new GitHubUnavailableException(
                "Could not reach GitHub", new RuntimeException("secret internal detail")));

    mockMvc
        .perform(get("/api/v1/rate-limit"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("GITHUB_UNAVAILABLE"))
        .andExpect(content().string(not(containsString("secret internal detail"))))
        .andExpect(content().string(not(containsString("Exception"))));
  }

  @Test
  void unexpectedError_returnsGeneric500() throws Exception {
    when(rateLimitService.getRateLimit()).thenThrow(new IllegalStateException("boom at line 42"));

    mockMvc
        .perform(get("/api/v1/rate-limit"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
        .andExpect(content().string(not(containsString("boom"))));
  }

  @Test
  void unknownRoute_returns404Problem() throws Exception {
    mockMvc
        .perform(get("/api/v1/does-not-exist"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }
}
