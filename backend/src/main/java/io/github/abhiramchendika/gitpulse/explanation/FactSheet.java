package io.github.abhiramchendika.gitpulse.explanation;

import io.github.abhiramchendika.gitpulse.analysis.ActivityIndicators;
import io.github.abhiramchendika.gitpulse.analysis.CommitStatistics;
import io.github.abhiramchendika.gitpulse.analysis.ContributorStatistics;
import io.github.abhiramchendika.gitpulse.analysis.Durations;
import io.github.abhiramchendika.gitpulse.analysis.IssueStatistics;
import io.github.abhiramchendika.gitpulse.analysis.LanguageStatistics;
import io.github.abhiramchendika.gitpulse.analysis.PullRequestStatistics;
import io.github.abhiramchendika.gitpulse.api.dto.ActivityResponse;
import io.github.abhiramchendika.gitpulse.api.dto.CommitAnalyticsResponse;
import io.github.abhiramchendika.gitpulse.api.dto.ContributorAnalyticsResponse;
import io.github.abhiramchendika.gitpulse.api.dto.IssueAnalyticsResponse;
import io.github.abhiramchendika.gitpulse.api.dto.LanguageResponse;
import io.github.abhiramchendika.gitpulse.api.dto.PullRequestAnalyticsResponse;
import io.github.abhiramchendika.gitpulse.api.dto.RepositoryOverviewResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The only input the model sees: numbers GitPulse has already calculated, with stable ids.
 *
 * <p>Deliberately left out: logins, names, commit messages, titles and descriptions. They are
 * written by other people (so they could carry instructions aimed at the model), and leaving them
 * out also keeps the explanation about activity rather than about individuals. Counts marked as
 * partial (lower bounds) are left out too, because a sentence could state them as exact.
 */
public record FactSheet(List<Fact> facts) {

  public FactSheet {
    facts = List.copyOf(facts);
  }

  public Map<String, Fact> byId() {
    Map<String, Fact> map = new LinkedHashMap<>();
    facts.forEach(f -> map.put(f.id(), f));
    return Collections.unmodifiableMap(map);
  }

  /** Everything the dashboard shows for one repository and analysis window. */
  public record Inputs(
      RepositoryOverviewResponse overview,
      ActivityResponse activity,
      CommitAnalyticsResponse commits,
      PullRequestAnalyticsResponse pullRequests,
      IssueAnalyticsResponse issues,
      ContributorAnalyticsResponse contributors,
      LanguageResponse languages) {}

  public static FactSheet from(Inputs in) {
    Builder b = new Builder();

    // The analysis window. Periods are referred to by length, never by date, so every number in
    // a sentence can be checked against a fact.
    var meta = in.commits().meta();
    b.number(
        "window.days",
        "Length of the selected period, in days",
        days(Duration.between(meta.requestedSince(), meta.until())));
    b.flag("window.botsExcluded", "Bot accounts are excluded from the counts", meta.botsExcluded());
    if (meta.truncated()) {
      b.number(
          "window.analysedDays",
          "Days actually covered by commit statistics (the rest was not fetched)",
          days(Duration.between(meta.since(), meta.until())));
    }

    RepositoryOverviewResponse o = in.overview();
    b.number("repository.stars", "Stars", o.stars());
    b.number("repository.forks", "Forks", o.forks());
    b.number("repository.watchers", "Watchers", o.watchers());
    b.number(
        "repository.openIssuesAndPullRequests",
        "Open issues and pull requests (all time)",
        o.openIssuesAndPullRequests());
    b.number(
        "repository.createdYear",
        "Year the repository was created",
        o.createdAt().atZone(ZoneOffset.UTC).getYear());
    b.flag("repository.archived", "The repository is archived (read-only)", o.archived());
    b.flag("repository.fork", "The repository is a fork of another repository", o.fork());
    if (o.primaryLanguage() != null) {
      b.text("repository.primaryLanguage", "Primary language", o.primaryLanguage());
    }

    if (!in.commits().emptyRepository()) {
      CommitStatistics c = in.commits().statistics();
      b.number("commits.total", "Commits in the selected period", c.totalCommits());
      b.number(
          "commits.allTime",
          "Commits on the default branch, all time",
          in.commits().totalCommitsAllTime());
      b.number(
          "commits.activeWeeks", "Weeks in the period with at least one commit", c.activeWeeks());
      b.number("commits.totalWeeks", "Weeks in the period", c.totalWeeks());
      b.number(
          "commits.averagePerWeek", "Average commits per week in the period", c.averagePerWeek());
      b.number(
          "commits.distinctAuthors", "Distinct commit authors in the period", c.distinctAuthors());
      b.number("commits.mergeCommits", "Merge commits in the period", c.mergeCommits());
      if (!meta.botsExcluded()) {
        b.number("commits.botCommits", "Commits by bot accounts in the period", c.botCommits());
      }
      if (c.longestInactivity() != null) {
        b.number(
            "commits.longestGapDays",
            "Longest period without commits in the selected period, in days",
            round(c.longestInactivity().days(), 0));
      }
    }

    ActivityIndicators a = in.activity().indicators();
    b.number("activity.shortPeriodDays", "Length of the short recent period, in days", 30);
    b.number("activity.longPeriodDays", "Length of the long recent period, in days", 90);
    b.number("activity.recentWeeks", "Number of recent weeks checked for activity", 12);
    if (a.daysSinceLastCommit() != null) {
      b.number(
          "activity.daysSinceLastCommit",
          "Days since the last commit",
          round(a.daysSinceLastCommit(), 0));
    }
    if (!a.commitsPartial()) {
      b.number("activity.commitsLast30Days", "Commits in the last 30 days", a.commitsLast30Days());
      b.number("activity.commitsLast90Days", "Commits in the last 90 days", a.commitsLast90Days());
      b.number(
          "activity.activeWeeksOfLast12",
          "Weeks with at least one commit, of the last 12",
          a.activeWeeksOfLast12());
    }
    if (!a.pullRequestsPartial()) {
      b.number(
          "activity.pullRequestsOpenedLast90Days",
          "Pull requests opened in the last 90 days",
          a.pullRequestsOpenedLast90Days());
      b.number(
          "activity.pullRequestsMergedLast90Days",
          "Pull requests merged in the last 90 days",
          a.pullRequestsMergedLast90Days());
    }
    if (!a.issuesPartial() && a.issuesOpenedLast90Days() != null) {
      b.number(
          "activity.issuesOpenedLast90Days",
          "Issues opened in the last 90 days",
          a.issuesOpenedLast90Days());
      b.number(
          "activity.issuesClosedLast90Days",
          "Issues closed in the last 90 days",
          a.issuesClosedLast90Days());
    }

    PullRequestStatistics pr = in.pullRequests().statistics();
    b.number("pullRequests.opened", "Pull requests opened in the selected period", pr.opened());
    b.number("pullRequests.merged", "Of those, merged", pr.merged());
    b.number(
        "pullRequests.closedWithoutMerge",
        "Of those, closed without merging",
        pr.closedWithoutMerge());
    b.number("pullRequests.stillOpen", "Of those, still open", pr.stillOpen());
    if (pr.mergedPercentOfClosed() != null) {
      b.number(
          "pullRequests.mergedPercentOfClosed",
          "Percent of closed pull requests that were merged",
          pr.mergedPercentOfClosed());
    }
    durations(b, "pullRequests.timeToMerge", "to merge a pull request", pr.timeToMerge());
    var prTotals = in.pullRequests().totals();
    b.number("pullRequests.openAllTime", "Open pull requests right now", prTotals.open());
    if (prTotals.merged() != null) {
      b.number("pullRequests.mergedAllTime", "Merged pull requests, all time", prTotals.merged());
    }

    b.flag("issues.enabled", "The repository accepts issues", in.issues().issuesEnabled());
    if (in.issues().issuesEnabled() && in.issues().statistics() != null) {
      IssueStatistics is = in.issues().statistics();
      b.number("issues.opened", "Issues opened in the selected period", is.opened());
      b.number("issues.closed", "Of those, closed", is.closed());
      b.number("issues.stillOpen", "Of those, still open", is.stillOpen());
      b.number("issues.closedAsCompleted", "Of those, closed as completed", is.closedAsCompleted());
      b.number(
          "issues.closedAsNotPlanned", "Of those, closed as not planned", is.closedAsNotPlanned());
      durations(b, "issues.timeToClose", "to close an issue", is.timeToClose());
      var totals = in.issues().totals();
      if (totals != null) {
        b.number("issues.openAllTime", "Open issues right now", totals.open());
        if (totals.closed() != null) {
          b.number("issues.closedAllTime", "Closed issues, all time", totals.closed());
        }
      }
    }

    if (in.contributors().available()) {
      ContributorStatistics cs = in.contributors().statistics();
      b.number(
          "contributors.count",
          "Contributors with linked GitHub accounts, all time",
          cs.contributorCount());
      b.number(
          "contributors.topSharePercent",
          "Share of all-time commits by the contributor with the most commits, percent",
          cs.topContributorSharePercent());
      b.number(
          "contributors.forHalfOfCommits",
          "Fewest contributors who together made half of all-time commits",
          cs.contributorsForHalfOfCommits());
      b.number("contributors.bots", "Bot accounts among contributors", cs.botCount());
    }

    List<LanguageStatistics.Language> languages = in.languages().statistics().languages();
    for (int i = 0; i < Math.min(3, languages.size()); i++) {
      LanguageStatistics.Language l = languages.get(i);
      String rank = Integer.toString(i + 1);
      b.text("languages." + rank + ".name", "Language ranked " + rank + " by code size", l.name());
      b.number("languages." + rank + ".percent", "Its share of the code, percent", l.percent());
    }

    return new FactSheet(b.facts);
  }

  private static void durations(Builder b, String id, String what, Durations.Summary summary) {
    if (summary == null) {
      return;
    }
    b.number(id + ".medianHours", "Median hours " + what, summary.medianHours());
    b.number(
        id + ".p90Hours", "Hours within which 90% of them happened, " + what, summary.p90Hours());
  }

  private static BigDecimal days(Duration duration) {
    return BigDecimal.valueOf(Math.round(duration.toSeconds() / 86_400.0));
  }

  private static BigDecimal round(double value, int scale) {
    return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
  }

  private static final class Builder {
    private final List<Fact> facts = new ArrayList<>();

    void number(String id, String label, long value) {
      facts.add(new Fact(id, label, BigDecimal.valueOf(value)));
    }

    void number(String id, String label, double value) {
      facts.add(new Fact(id, label, BigDecimal.valueOf(value)));
    }

    void number(String id, String label, BigDecimal value) {
      facts.add(new Fact(id, label, value));
    }

    void text(String id, String label, String value) {
      facts.add(new Fact(id, label, value));
    }

    void flag(String id, String label, boolean value) {
      facts.add(new Fact(id, label, value));
    }
  }
}
