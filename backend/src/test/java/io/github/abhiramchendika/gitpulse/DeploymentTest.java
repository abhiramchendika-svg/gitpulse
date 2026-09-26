package io.github.abhiramchendika.gitpulse;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * How the single Docker image behaves: the backend serves the built frontend next to the API.
 * {@code src/test/resources/static} stands in for the frontend build that the Dockerfile copies
 * into the jar.
 */
class DeploymentTest {

  @Nested
  @SpringBootTest
  @AutoConfigureMockMvc
  class ServingTheFrontend {

    @Autowired MockMvc mockMvc;

    @Test
    void rootServesTheApp_revalidatedOnEveryVisit() throws Exception {
      mockMvc.perform(get("/")).andExpect(status().isOk()).andExpect(forwardedUrl("index.html"));
      mockMvc
          .perform(get("/index.html"))
          .andExpect(status().isOk())
          .andExpect(content().string(containsString("GitPulse test page")))
          .andExpect(header().string("Cache-Control", containsString("no-cache")));
    }

    @Test
    void hashedAssetsAreCachedForAYear() throws Exception {
      mockMvc
          .perform(get("/assets/index-Test123.js"))
          .andExpect(status().isOk())
          .andExpect(header().string("Cache-Control", containsString("max-age=31536000")))
          .andExpect(header().string("Cache-Control", containsString("immutable")));
    }

    @Test
    void apiAndErrorsWorkAlongsideIt() throws Exception {
      mockMvc
          .perform(get("/api/v1/features"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.explanations").isBoolean())
          // API responses are not given the static files' caching rules.
          .andExpect(header().string("Cache-Control", not(containsString("immutable"))));
      mockMvc
          .perform(get("/assets/missing.js"))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("NOT_FOUND"));
      mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
  }

  /**
   * Behind a proxy that terminates HTTPS (Render), the app receives plain HTTP on an internal port.
   * Without trusting the proxy's X-Forwarded-* headers, a same-site POST from
   * https://gitpulse.example looks cross-origin and CORS rejects it with a bare 403.
   */
  @Nested
  @SpringBootTest(
      properties = {
        "server.forward-headers-strategy=framework",
        "gitpulse.cors.allowed-origins=http://localhost:5173"
      })
  @AutoConfigureMockMvc
  class BehindAnHttpsProxy {

    @Autowired MockMvc mockMvc;

    @Test
    void sameSitePostIsNotMistakenForCrossOrigin() throws Exception {
      mockMvc
          .perform(
              post("/api/v1/repositories/octocat/hello/explanation")
                  .header("Origin", "https://gitpulse.example")
                  .header("X-Forwarded-Proto", "https")
                  .header("X-Forwarded-Host", "gitpulse.example"))
          // Reaches the application (no AI key in tests), instead of CORS's bare 403.
          .andExpect(status().isServiceUnavailable())
          .andExpect(jsonPath("$.code").value("AI_NOT_CONFIGURED"));
    }

    @Test
    void otherSitesAreStillRefused() throws Exception {
      mockMvc
          .perform(
              post("/api/v1/repositories/octocat/hello/explanation")
                  .header("Origin", "https://evil.example")
                  .header("X-Forwarded-Proto", "https")
                  .header("X-Forwarded-Host", "gitpulse.example"))
          .andExpect(status().isForbidden());
    }
  }
}
