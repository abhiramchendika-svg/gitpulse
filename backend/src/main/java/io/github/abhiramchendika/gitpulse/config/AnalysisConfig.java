package io.github.abhiramchendika.gitpulse.config;

import io.github.abhiramchendika.gitpulse.analysis.ActivityAnalyzer;
import io.github.abhiramchendika.gitpulse.analysis.CommitAnalyzer;
import io.github.abhiramchendika.gitpulse.analysis.ContributorAnalyzer;
import io.github.abhiramchendika.gitpulse.analysis.IssueAnalyzer;
import io.github.abhiramchendika.gitpulse.analysis.LanguageAnalyzer;
import io.github.abhiramchendika.gitpulse.analysis.PullRequestAnalyzer;
import java.time.Clock;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Creates the analyzers. They are plain Java classes (no Spring annotations) so they can be
 * constructed directly in unit tests; Spring only wires them here.
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching
public class AnalysisConfig {

  /** Injected wherever "now" matters, so tests can pin time with {@code Clock.fixed(...)}. */
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  CommitAnalyzer commitAnalyzer(AnalysisProperties properties) {
    return new CommitAnalyzer(
        properties.inactivityThreshold(),
        properties.topAuthors(),
        properties.recentCommits(),
        properties.inactivityPeriods());
  }

  @Bean
  ContributorAnalyzer contributorAnalyzer(AnalysisProperties properties) {
    return new ContributorAnalyzer(properties.contributorLimit());
  }

  @Bean
  LanguageAnalyzer languageAnalyzer() {
    return new LanguageAnalyzer();
  }

  @Bean
  PullRequestAnalyzer pullRequestAnalyzer(AnalysisProperties properties) {
    return new PullRequestAnalyzer(properties.topAuthors(), properties.recentCommits());
  }

  @Bean
  IssueAnalyzer issueAnalyzer(AnalysisProperties properties) {
    return new IssueAnalyzer(properties.topAuthors(), properties.recentCommits());
  }

  @Bean
  ActivityAnalyzer activityAnalyzer() {
    return new ActivityAnalyzer();
  }
}
