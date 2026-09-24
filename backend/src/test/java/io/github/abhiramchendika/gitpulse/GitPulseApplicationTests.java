package io.github.abhiramchendika.gitpulse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.abhiramchendika.gitpulse.config.GitHubProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** Smoke test: the full application context starts and the health endpoint reports UP. */
@SpringBootTest(properties = "gitpulse.github.token=super-secret-test-token")
@AutoConfigureMockMvc
class GitPulseApplicationTests {

  @Autowired private MockMvc mockMvc;

  @Autowired private GitHubProperties gitHubProperties;

  @Test
  void healthEndpointIsUp() throws Exception {
    mockMvc
        .perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  void onlyHealthActuatorEndpointIsExposed() throws Exception {
    mockMvc.perform(get("/actuator/env")).andExpect(status().isNotFound());
    mockMvc.perform(get("/actuator/beans")).andExpect(status().isNotFound());
  }

  @Test
  void tokenIsNeverRenderedByToString() {
    assertThat(gitHubProperties.hasToken()).isTrue();
    assertThat(gitHubProperties.toString()).doesNotContain("super-secret-test-token");
  }
}
