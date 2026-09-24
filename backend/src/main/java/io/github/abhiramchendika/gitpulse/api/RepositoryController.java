package io.github.abhiramchendika.gitpulse.api;

import io.github.abhiramchendika.gitpulse.api.dto.ActivityResponse;
import io.github.abhiramchendika.gitpulse.api.dto.CommitAnalyticsResponse;
import io.github.abhiramchendika.gitpulse.api.dto.ContributorAnalyticsResponse;
import io.github.abhiramchendika.gitpulse.api.dto.FileActivityResponse;
import io.github.abhiramchendika.gitpulse.api.dto.IssueAnalyticsResponse;
import io.github.abhiramchendika.gitpulse.api.dto.LanguageResponse;
import io.github.abhiramchendika.gitpulse.api.dto.PullRequestAnalyticsResponse;
import io.github.abhiramchendika.gitpulse.api.dto.RepositoryOverviewResponse;
import io.github.abhiramchendika.gitpulse.service.ActivityService;
import io.github.abhiramchendika.gitpulse.service.CommitAnalyticsService;
import io.github.abhiramchendika.gitpulse.service.ContributorAnalyticsService;
import io.github.abhiramchendika.gitpulse.service.FileActivityService;
import io.github.abhiramchendika.gitpulse.service.IssueAnalyticsService;
import io.github.abhiramchendika.gitpulse.service.PullRequestAnalyticsService;
import io.github.abhiramchendika.gitpulse.service.RepositoryRef;
import io.github.abhiramchendika.gitpulse.service.RepositoryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
 *
 * <p>Windowed endpoints share the same query parameters: {@code since} / {@code until} as {@code
 * yyyy-MM-dd} UTC days (default: the last 365 days including today) and {@code excludeBots}.
 */
@RestController
@RequestMapping("/api/v1/repositories/{owner}/{repo}")
public class RepositoryController {

  private final RepositoryService repositoryService;
  private final CommitAnalyticsService commitAnalyticsService;
  private final ContributorAnalyticsService contributorAnalyticsService;
  private final PullRequestAnalyticsService pullRequestAnalyticsService;
  private final IssueAnalyticsService issueAnalyticsService;
  private final ActivityService activityService;
  private final FileActivityService fileActivityService;

  public RepositoryController(
      RepositoryService repositoryService,
      CommitAnalyticsService commitAnalyticsService,
      ContributorAnalyticsService contributorAnalyticsService,
      PullRequestAnalyticsService pullRequestAnalyticsService,
      IssueAnalyticsService issueAnalyticsService,
      ActivityService activityService,
      FileActivityService fileActivityService) {
    this.repositoryService = repositoryService;
    this.commitAnalyticsService = commitAnalyticsService;
    this.contributorAnalyticsService = contributorAnalyticsService;
    this.pullRequestAnalyticsService = pullRequestAnalyticsService;
    this.issueAnalyticsService = issueAnalyticsService;
    this.activityService = activityService;
    this.fileActivityService = fileActivityService;
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

  @GetMapping("/pull-requests")
  public PullRequestAnalyticsResponse pullRequests(
      @PathVariable @Pattern(regexp = GitHubNames.OWNER) String owner,
      @PathVariable @Pattern(regexp = GitHubNames.REPO) String repo,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate since,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate until,
      @RequestParam(defaultValue = "false") boolean excludeBots) {
    return pullRequestAnalyticsService.analyze(
        new RepositoryRef(owner, repo), since, until, excludeBots);
  }

  @GetMapping("/issues")
  public IssueAnalyticsResponse issues(
      @PathVariable @Pattern(regexp = GitHubNames.OWNER) String owner,
      @PathVariable @Pattern(regexp = GitHubNames.REPO) String repo,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate since,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate until,
      @RequestParam(defaultValue = "false") boolean excludeBots) {
    return issueAnalyticsService.analyze(new RepositoryRef(owner, repo), since, until, excludeBots);
  }

  /**
   * Most-changed files and directories in a sample of recent commits.
   *
   * @param sample commits to analyse; costs one GitHub request each unless cached, and is capped by
   *     the server (lower without a GitHub token)
   */
  @GetMapping("/files")
  public FileActivityResponse files(
      @PathVariable @Pattern(regexp = GitHubNames.OWNER) String owner,
      @PathVariable @Pattern(regexp = GitHubNames.REPO) String repo,
      @RequestParam(required = false) @Min(1) @Max(1000) Integer sample) {
    return fileActivityService.analyze(new RepositoryRef(owner, repo), sample);
  }

  /** Factual indicators for the last 30/90 days; no parameters, no score. */
  @GetMapping("/activity")
  public ActivityResponse activity(
      @PathVariable @Pattern(regexp = GitHubNames.OWNER) String owner,
      @PathVariable @Pattern(regexp = GitHubNames.REPO) String repo) {
    return activityService.analyze(new RepositoryRef(owner, repo));
  }
}
