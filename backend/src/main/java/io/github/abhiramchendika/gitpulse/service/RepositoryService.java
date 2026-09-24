package io.github.abhiramchendika.gitpulse.service;

import io.github.abhiramchendika.gitpulse.analysis.LanguageAnalyzer;
import io.github.abhiramchendika.gitpulse.api.dto.LanguageResponse;
import io.github.abhiramchendika.gitpulse.api.dto.RepositoryOverviewResponse;
import io.github.abhiramchendika.gitpulse.github.model.GitHubRepository;
import io.github.abhiramchendika.gitpulse.github.model.GitHubUser;
import java.util.List;
import org.springframework.stereotype.Service;

/** Repository metadata and language breakdown. */
@Service
public class RepositoryService {

  private final RepositoryDataService data;
  private final LanguageAnalyzer languageAnalyzer;

  public RepositoryService(RepositoryDataService data, LanguageAnalyzer languageAnalyzer) {
    this.data = data;
    this.languageAnalyzer = languageAnalyzer;
  }

  public RepositoryOverviewResponse overview(RepositoryRef ref) {
    return toOverview(data.repository(ref));
  }

  public LanguageResponse languages(RepositoryRef ref) {
    return new LanguageResponse(ref.fullName(), languageAnalyzer.analyze(data.languages(ref)));
  }

  /** Maps GitHub's JSON shape to ours, so a GitHub API change cannot silently change our API. */
  static RepositoryOverviewResponse toOverview(GitHubRepository repo) {
    GitHubUser owner = repo.owner();
    GitHubRepository.License license = repo.license();
    return new RepositoryOverviewResponse(
        repo.fullName(),
        repo.name(),
        owner == null
            ? null
            : new RepositoryOverviewResponse.Owner(
                owner.login(), owner.type(), owner.avatarUrl(), owner.htmlUrl()),
        repo.description(),
        repo.htmlUrl(),
        repo.homepage() == null || repo.homepage().isBlank() ? null : repo.homepage(),
        repo.stargazersCount(),
        repo.forksCount(),
        repo.subscribersCount(),
        repo.openIssuesCount(),
        repo.language(),
        license == null
            ? null
            : new RepositoryOverviewResponse.License(license.spdxId(), license.name()),
        repo.topics() == null ? List.of() : repo.topics(),
        repo.createdAt(),
        repo.updatedAt(),
        repo.pushedAt(),
        repo.size(),
        repo.defaultBranch(),
        repo.archived(),
        repo.fork());
  }
}
