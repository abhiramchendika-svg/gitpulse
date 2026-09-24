package io.github.abhiramchendika.gitpulse.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.abhiramchendika.gitpulse.api.dto.ComparisonResponse;
import io.github.abhiramchendika.gitpulse.service.ComparisonService;
import io.github.abhiramchendika.gitpulse.service.RepositoryNotFoundException;
import io.github.abhiramchendika.gitpulse.service.RepositoryRef;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CompareController.class)
class CompareControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ComparisonService comparisonService;

  @Test
  void compare_passesBothRepositories() throws Exception {
    when(comparisonService.compare(any()))
        .thenReturn(new ComparisonResponse(Instant.parse("2026-09-25T00:00:00Z"), List.of()));

    mockMvc
        .perform(get("/api/v1/compare").param("repos", "facebook/react,vuejs/core"))
        .andExpect(status().isOk());

    verify(comparisonService)
        .compare(
            List.of(new RepositoryRef("facebook", "react"), new RepositoryRef("vuejs", "core")));
  }

  @Test
  void invalidInput_is400WithoutCallingTheService() throws Exception {
    mockMvc
        .perform(get("/api/v1/compare").param("repos", "facebook/react"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    mockMvc.perform(get("/api/v1/compare")).andExpect(status().isBadRequest());
    verifyNoInteractions(comparisonService);
  }

  @Test
  void missingRepository_namesWhichOne() throws Exception {
    when(comparisonService.compare(any()))
        .thenThrow(new RepositoryNotFoundException(new RepositoryRef("octocat", "nope")));

    mockMvc
        .perform(get("/api/v1/compare").param("repos", "facebook/react,octocat/nope"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("REPOSITORY_NOT_FOUND"))
        .andExpect(
            jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("octocat/nope")));
  }
}
