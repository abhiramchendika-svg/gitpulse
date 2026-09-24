package io.github.abhiramchendika.gitpulse.api;

import io.github.abhiramchendika.gitpulse.api.dto.CommitAnalyticsResponse;
import io.github.abhiramchendika.gitpulse.api.dto.ContributorAnalyticsResponse;
import io.github.abhiramchendika.gitpulse.api.dto.LanguageResponse;
import io.github.abhiramchendika.gitpulse.api.dto.RepositoryOverviewResponse;
import io.github.abhiramchendika.gitpulse.service.CommitAnalyticsService;
import io.github.abhiramchendika.gitpulse.service.ContributorAnalyticsService;
import io.github.abhiramchendika.gitpulse.service.RepositoryRef;
import io.github.abhiramchendika.gitpulse.service.RepositoryService;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Repository analytics endpoints. Controllers only validate input and delegate: all logic lives in
 * services, which keeps these methods trivial to read and test.
 */
@RestController
@RequestMapping("/api/v1/repositories/{owner}/{repo}")
public class RepositoryController {

  private final RepositoryService repositoryService;
  private final CommitAnalyticsService commitAnalyticsService;
  private final ContributorAnalyticsService contributorAnalyticsService;

  public RepositoryController(
      RepositoryService repositoryService,
      CommitAnalyticsService commitAnalyticsService,
      ContributorAnalyticsService contributorAnalyticsService) {
    this.repositoryService = repositoryService;
    this.commitAnalyticsService = commitAnalyticsService;
    this.contributorAnalyticsService = contributorAnalyticsService;
  }

  @GetMapping
  public RepositoryOverviewResponse overview(
      @PathVariable @Pattern(regexp = GitHubNames.OWNER) String owner,
      @PathVariable @Pattern(regexp = GitHubNames.REPO) String repo) {
    return repositoryService.overview(new RepositoryRef(owner, repo));
  }

  @GetMapping("/languages")
  public LanguageResponse languages(
      @PathVariable @Pattern(regexp = GitHubNames.OWNER) String owner,
      @PathVariable @Pattern(regexp = GitHubNames.REPO) String repo) {
    return repositoryService.languages(new RepositoryRef(owner, repo));
  }

  /**
   * @param since first day to include, {@code yyyy-MM-dd} (UTC); default: 365 days before until
   * @param until last day to include, {@code yyyy-MM-dd} (UTC); default: today
   * @param excludeBots drop commits made by bot accounts before analysing
   */
  @GetMapping("/commits")
  public CommitAnalyticsResponse commits(
      @PathVariable @Pattern(regexp = GitHubNames.OWNER) String owner,
      @PathVariable @Pattern(regexp = GitHubNames.REPO) String repo,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate since,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate until,
      @RequestParam(defaultValue = "false") boolean excludeBots) {
    return commitAnalyticsService.analyze(
        new RepositoryRef(owner, repo), since, until, excludeBots);
  }

  @GetMapping("/contributors")
  public ContributorAnalyticsResponse contributors(
      @PathVariable @Pattern(regexp = GitHubNames.OWNER) String owner,
      @PathVariable @Pattern(regexp = GitHubNames.REPO) String repo) {
    return contributorAnalyticsService.analyze(new RepositoryRef(owner, repo));
  }
}
