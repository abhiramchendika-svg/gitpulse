package io.github.abhiramchendika.gitpulse.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.abhiramchendika.gitpulse.explanation.ExplanationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FeaturesController.class)
class FeaturesControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private ExplanationService explanationService;

  @Test
  void reportsWhetherExplanationsAreEnabled() throws Exception {
    when(explanationService.enabled()).thenReturn(true);
    mockMvc
        .perform(get("/api/v1/features"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.explanations").value(true));

    when(explanationService.enabled()).thenReturn(false);
    mockMvc.perform(get("/api/v1/features")).andExpect(jsonPath("$.explanations").value(false));
  }
}
