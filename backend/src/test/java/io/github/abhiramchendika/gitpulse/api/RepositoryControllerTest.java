package io.github.abhiramchendika.gitpulse.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.abhiramchendika.gitpulse.analysis.LanguageStatistics;
import io.github.abhiramchendika.gitpulse.api.dto.ExplanationResponse;
import io.github.abhiramchendika.gitpulse.api.dto.LanguageResponse;
import io.github.abhiramchendika.gitpulse.explanation.ExplanationLimitException;
import io.github.abhiramchendika.gitpulse.explanation.ExplanationService;
import io.github.abhiramchendika.gitpulse.explanation.ExplanationUnavailableException;
import io.github.abhiramchendika.gitpulse.explanation.ExplanationsDisabledException;
import io.github.abhiramchendika.gitpulse.explanation.Fact;
import io.github.abhiramchendika.gitpulse.explanation.UnreliableExplanationException;
import io.github.abhiramchendika.gitpulse.service.ActivityService;
import io.github.abhiramchendika.gitpulse.service.CommitAnalyticsService;
import io.github.abhiramchendika.gitpulse.service.ContributorAnalyticsService;
import io.github.abhiramchendika.gitpulse.service.FileActivityService;
import io.github.abhiramchendika.gitpulse.service.InvalidRequestException;
import io.github.abhiramchendika.gitpulse.service.IssueAnalyticsService;
import io.github.abhiramchendika.gitpulse.service.PullRequestAnalyticsService;
import io.github.abhiramchendika.gitpulse.service.RepositoryNotFoundException;
import io.github.abhiramchendika.gitpulse.service.RepositoryRef;
import io.github.abhiramchendika.gitpulse.service.RepositoryService;
import java.math.BigDecimal;
import java.time.Instant;
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
  @MockitoBean private PullRequestAnalyticsService pullRequestAnalyticsService;
  @MockitoBean private IssueAnalyticsService issueAnalyticsService;
  @MockitoBean private ActivityService activityService;
  @MockitoBean private FileActivityService fileActivityService;
  @MockitoBean private ExplanationService explanationService;

  @Test
  void files_passesSampleAndValidatesItsRange() throws Exception {
    mockMvc
        .perform(get("/api/v1/repositories/octocat/hello/files").param("sample", "25"))
        .andExpect(status().isOk());
    verify(fileActivityService).analyze(new RepositoryRef("octocat", "hello"), 25);

    mockMvc
        .perform(get("/api/v1/repositories/octocat/hello/files").param("sample", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    mockMvc
        .perform(get("/api/v1/repositories/octocat/hello/files").param("sample", "5000"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void explanation_isPostOnly_andPassesTheWindow() throws Exception {
    RepositoryRef ref = new RepositoryRef("octocat", "hello");
    when(explanationService.explain(ref, LocalDate.of(2026, 6, 27), null, true))
        .thenReturn(
            new ExplanationResponse(
                "octocat/hello",
                Instant.parse("2026-09-25T12:00:00Z"),
                "claude-opus-5",
                new ExplanationResponse.Window(
                    Instant.parse("2026-06-27T00:00:00Z"),
                    Instant.parse("2026-09-25T12:00:00Z"),
                    true),
                List.of(
                    new ExplanationResponse.Sentence(
                        "There were 240 commits.",
                        List.of(new Fact("commits.total", "Commits", BigDecimal.valueOf(240))))),
                0));

    mockMvc
        .perform(
            post("/api/v1/repositories/octocat/hello/explanation")
                .param("since", "2026-06-27")
                .param("excludeBots", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sentences[0].text").value("There were 240 commits."))
        .andExpect(jsonPath("$.sentences[0].basedOn[0].id").value("commits.total"))
        .andExpect(jsonPath("$.sentences[0].basedOn[0].value").value(240));

    // A GET (link prefetch, crawler) never triggers a paid call.
    mockMvc
        .perform(get("/api/v1/repositories/octocat/hello/explanation"))
        .andExpect(status().is4xxClientError());
    mockMvc
        .perform(post("/api/v1/repositories/-bad/hello/explanation"))
        .andExpect(status().isBadRequest());
  }

  /**
   * Regression: browsers send an Origin header with POST, and the Vite dev proxy forwards it, so
   * the explanation request is a CORS request. Allowing only GET made it fail with a bare 403.
   */
  @Test
  void explanation_isAllowedFromTheFrontendOrigin_butNotFromOthers() throws Exception {
    String url = "/api/v1/repositories/octocat/hello/explanation";
    mockMvc.perform(post(url).header("Origin", "http://localhost:5173")).andExpect(status().isOk());
    mockMvc
        .perform(post(url).header("Origin", "https://evil.example"))
        .andExpect(status().isForbidden());
  }

  @Test
  void explanationErrors_haveStableCodes() throws Exception {
    String url = "/api/v1/repositories/octocat/hello/explanation";

    doThrow(new ExplanationsDisabledException())
        .when(explanationService)
        .explain(any(), any(), any(), eq(false));
    mockMvc
        .perform(post(url))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("AI_NOT_CONFIGURED"));

    doThrow(new ExplanationLimitException(Instant.now().plusSeconds(600)))
        .when(explanationService)
        .explain(any(), any(), any(), eq(false));
    mockMvc
        .perform(post(url))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("AI_LIMIT_REACHED"))
        .andExpect(jsonPath("$.resetAt").exists())
        .andExpect(header().exists("Retry-After"));

    doThrow(new ExplanationUnavailableException("secret upstream detail", null))
        .when(explanationService)
        .explain(any(), any(), any(), eq(false));
    mockMvc
        .perform(post(url))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("AI_UNAVAILABLE"))
        .andExpect(
            jsonPath("$.detail")
                .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));

    doThrow(new UnreliableExplanationException("declined"))
        .when(explanationService)
        .explain(any(), any(), any(), eq(false));
    mockMvc
        .perform(post(url))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.code").value("AI_UNRELIABLE"));
  }

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
  void pullRequestsAndIssues_passWindowAndBotFilter() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/repositories/octocat/hello/pull-requests")
                .param("since", "2026-01-01")
                .param("excludeBots", "true"))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/v1/repositories/octocat/hello/issues").param("until", "2026-06-30"))
        .andExpect(status().isOk());
    mockMvc.perform(get("/api/v1/repositories/octocat/hello/activity")).andExpect(status().isOk());

    RepositoryRef ref = new RepositoryRef("octocat", "hello");
    verify(pullRequestAnalyticsService).analyze(ref, LocalDate.parse("2026-01-01"), null, true);
    verify(issueAnalyticsService).analyze(ref, null, LocalDate.parse("2026-06-30"), false);
    verify(activityService).analyze(ref);
  }

  @Test
  void newEndpoints_validateNamesToo() throws Exception {
    mockMvc
        .perform(get("/api/v1/repositories/-bad/hello/pull-requests"))
        .andExpect(status().isBadRequest());
    // ".." reaches the controller as the repo name and is rejected by the allow-list.
    mockMvc
        .perform(get("/api/v1/repositories/octocat/../activity"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(pullRequestAnalyticsService, activityService);
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
