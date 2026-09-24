package io.github.abhiramchendika.gitpulse.service;

import io.github.abhiramchendika.gitpulse.analysis.ActivityAnalyzer;
import io.github.abhiramchendika.gitpulse.analysis.model.CommitRecord;
import io.github.abhiramchendika.gitpulse.analysis.model.PullRequestRecord;
import io.github.abhiramchendika.gitpulse.api.dto.ActivityResponse;
import io.github.abhiramchendika.gitpulse.github.model.GitHubRepository;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

/**
 * Recent-activity indicators (last 30/90 days).
 *
 * <p>Reuses the <em>default-window</em> samples (the last year) instead of fetching 90-day ones:
 * the dashboard loads those anyway, so with a warm cache this endpoint costs zero GitHub requests.
 */
@Service
public class ActivityService {

  private final RepositoryDataService data;
  private final ActivityAnalyzer analyzer;
  private final AnalysisWindows windows;

  public ActivityService(
      RepositoryDataService data, ActivityAnalyzer analyzer, AnalysisWindows windows) {
    this.data = data;
    this.analyzer = analyzer;
    this.windows = windows;
  }

  public ActivityResponse analyze(RepositoryRef ref) {
    AnalysisWindows.Resolved window = windows.resolve(null, null);
    GitHubRepository repository = data.repository(ref);

    CommitSample commits = data.commits(ref, window.requestedSince(), window.requestedUntil());
    PullRequestSample pullRequests = data.pullRequests(ref, window.requestedSince());
    IssueSample issues =
        repository.issuesEnabled() ? data.issues(ref, window.requestedSince()) : null;

    ActivityAnalyzer.Input input =
        new ActivityAnalyzer.Input(
            window.now(),
            repository.pushedAt(),
            commits.commits(),
            AnalysisWindows.effectiveSince(
                commits.truncated(),
                commits.commits().stream().map(CommitRecord::committedAt),
                window.requestedSince(),
                window.analysisUntil()),
            pullRequests.pullRequests(),
            AnalysisWindows.effectiveSince(
                pullRequests.truncated(),
                pullRequests.pullRequests().stream().map(PullRequestRecord::createdAt),
                window.requestedSince(),
                window.analysisUntil()),
            issues == null ? null : issues.issues(),
            issues == null
                ? window.requestedSince()
                : AnalysisWindows.effectiveSince(
                    issues.truncated(),
                    Stream.ofNullable(issues.oldestFetchedCreatedAt()),
                    window.requestedSince(),
                    window.analysisUntil()));

    return new ActivityResponse(ref.fullName(), window.now(), analyzer.analyze(input));
  }
}
