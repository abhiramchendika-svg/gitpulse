package io.github.abhiramchendika.gitpulse.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.abhiramchendika.gitpulse.analysis.LanguageStatistics;
import io.github.abhiramchendika.gitpulse.api.dto.LanguageResponse;
import io.github.abhiramchendika.gitpulse.service.CommitAnalyticsService;
import io.github.abhiramchendika.gitpulse.service.ContributorAnalyticsService;
import io.github.abhiramchendika.gitpulse.service.InvalidRequestException;
import io.github.abhiramchendika.gitpulse.service.RepositoryNotFoundException;
import io.github.abhiramchendika.gitpulse.service.RepositoryRef;
import io.github.abhiramchendika.gitpulse.service.RepositoryService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RepositoryController.class)
class RepositoryControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private RepositoryService repositoryService;
  @MockitoBean private CommitAnalyticsService commitAnalyticsService;
  @MockitoBean private ContributorAnalyticsService contributorAnalyticsService;

  @Test
  void languages_returns200() throws Exception {
    when(repositoryService.languages(new RepositoryRef("octocat", "hello")))
        .thenReturn(
            new LanguageResponse(
                "octocat/hello",
                new LanguageStatistics(
                    10, List.of(new LanguageStatistics.Language("Java", 10, 100.0)))));

    mockMvc
        .perform(get("/api/v1/repositories/octocat/hello/languages"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.repository").value("octocat/hello"))
        .andExpect(jsonPath("$.statistics.languages[0].name").value("Java"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/api/v1/repositories/-bad/hello", // owner cannot start with '-'
        "/api/v1/repositories/octocat/..", // path trick
        "/api/v1/repositories/under_score/hello", // '_' not allowed in owners
        "/api/v1/repositories/a234567890123456789012345678901234567890/x" // 40 chars
      })
  void invalidNames_return400WithoutCallingServices(String path) throws Exception {
    mockMvc
        .perform(get(path))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

    verifyNoInteractions(repositoryService);
  }

  @Test
  void validUnusualRepoNames_areAccepted() throws Exception {
    mockMvc.perform(get("/api/v1/repositories/octo-cat/my.repo_v2-x")).andExpect(status().isOk());
  }

  @Test
  void repositoryNotFound_returns404WithCode() throws Exception {
    when(repositoryService.overview(any()))
        .thenThrow(new RepositoryNotFoundException(new RepositoryRef("octocat", "missing")));

    mockMvc
        .perform(get("/api/v1/repositories/octocat/missing"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("REPOSITORY_NOT_FOUND"))
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("private")));
  }

  @Test
  void commits_parsesDatesAndFlag() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/repositories/octocat/hello/commits")
                .param("since", "2026-01-01")
                .param("until", "2026-06-30")
                .param("excludeBots", "true"))
        .andExpect(status().isOk());

    verify(commitAnalyticsService)
        .analyze(
            eq(new RepositoryRef("octocat", "hello")),
            eq(LocalDate.parse("2026-01-01")),
            eq(LocalDate.parse("2026-06-30")),
            eq(true));
  }

  @Test
  void commits_badDateFormat_returns400() throws Exception {
    mockMvc
        .perform(get("/api/v1/repositories/octocat/hello/commits").param("since", "01/01/2026"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
  }

  @Test
  void commits_invalidRange_returns400WithMessage() throws Exception {
    when(commitAnalyticsService.analyze(any(), any(), any(), eq(false)))
        .thenThrow(new InvalidRequestException("'since' must be on or before 'until'."));

    mockMvc
        .perform(get("/api/v1/repositories/octocat/hello/commits"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("'since' must be on or before 'until'."));
  }
}
