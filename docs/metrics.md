# Metrics reference

Every number GitPulse shows is either **GitHub-provided** (copied from the GitHub REST API) or
**GitPulse-calculated** (derived by GitPulse from GitHub data using the formula below). This page
is the source of truth for both. If code and this page disagree, that is a bug.

GitPulse describes _activity_; it does not measure productivity, skill or code quality. A commit
count says how many commits exist, not how much work they represent.

## Conventions

- **Time zone:** all day/week/month/hour grouping uses **UTC**. GitHub's commit API returns UTC
  timestamps and does not preserve the author's local time zone, so "hour of day" means UTC hour.
- **Weeks** are ISO weeks, Monday 00:00 UTC to Sunday 23:59 UTC.
- **Windows** are half-open: `[since, until)`. A request for `since=2026-01-01&until=2026-01-31`
  covers 1 Jan 00:00 UTC up to (not including) 1 Feb 00:00 UTC, capped at the time of the request.
- **Branch:** commit data covers the repository's **default branch** only.
- **Rounding:** percentages to 1 decimal, averages to 2 decimals, half-up. Percentages may not sum
  to exactly 100.

## Repository overview: `GET /api/v1/repositories/{owner}/{repo}`

All fields are **GitHub-provided**.

| Field                       | GitHub source       | Notes                                                                     |
| --------------------------- | ------------------- | ------------------------------------------------------------------------- |
| `stars`                     | `stargazers_count`  |                                                                           |
| `forks`                     | `forks_count`       |                                                                           |
| `watchers`                  | `subscribers_count` | GitHub's `watchers_count` is a legacy alias for stars, so it is not used. |
| `openIssuesAndPullRequests` | `open_issues_count` | **GitHub counts open pull requests as issues here.** Not "open issues".   |
| `primaryLanguage`           | `language`          | May be `null` (e.g. repositories with no detected code).                  |
| `sizeKb`                    | `size`              | Kilobytes, as computed by GitHub.                                         |
| `pushedAt`                  | `pushed_at`         | Last push to **any** branch.                                              |
| `updatedAt`                 | `updated_at`        | Last change to the repository object (including metadata such as stars).  |

## Languages: `GET /api/v1/repositories/{owner}/{repo}/languages`

| Field     | Source              | Definition                                                           |
| --------- | ------------------- | -------------------------------------------------------------------- |
| `bytes`   | GitHub-provided     | Bytes of code per language, detected by GitHub Linguist.             |
| `percent` | GitPulse-calculated | `bytes / totalBytes × 100`. Measures file size, not lines or effort. |

## Commit activity: `GET /api/v1/repositories/{owner}/{repo}/commits`

| Field                       | Source              | Definition                                                                                                                                                                                                                                                                  |
| --------------------------- | ------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `totalCommitsAllTime`       | GitHub-provided     | Commits on the default branch, all time, read from the pagination `Link` header (`per_page=1`, last page number).                                                                                                                                                           |
| `totalCommits`              | GitPulse-calculated | Commits in the sample whose **author date** is inside the analysed window.                                                                                                                                                                                                  |
| `mergeCommits`              | GitPulse-calculated | Commits with more than one parent.                                                                                                                                                                                                                                          |
| `botCommits`                | GitPulse-calculated | Commits whose linked account has type `Bot` or a login ending in `[bot]`.                                                                                                                                                                                                   |
| `distinctAuthors`           | GitPulse-calculated | Distinct author keys (see _Author identity_).                                                                                                                                                                                                                               |
| `weekly`, `monthly`         | GitPulse-calculated | Commits per UTC week/month; every week/month in the window is listed, including zero-commit ones.                                                                                                                                                                           |
| `byDayOfWeek`               | GitPulse-calculated | 7 counts, index 0 = Monday.                                                                                                                                                                                                                                                 |
| `byHourOfDay`               | GitPulse-calculated | 24 counts, UTC hours.                                                                                                                                                                                                                                                       |
| `heatmap`                   | GitPulse-calculated | `heatmap[day][hour]` counts; rows sum to `byDayOfWeek`, columns to `byHourOfDay`.                                                                                                                                                                                           |
| `averagePerWeek`            | GitPulse-calculated | `totalCommits / (windowDays / 7)`. Quiet weeks count, so bursts do not inflate the average.                                                                                                                                                                                 |
| `averagePerMonth`           | GitPulse-calculated | `totalCommits / (windowDays / 30.436875)` (average Gregorian month).                                                                                                                                                                                                        |
| `activeWeeks`               | GitPulse-calculated | Weeks with at least one commit, out of `totalWeeks`. First and last weeks may be partial.                                                                                                                                                                                   |
| `topAuthors[].sharePercent` | GitPulse-calculated | `author's commits in window / totalCommits × 100`.                                                                                                                                                                                                                          |
| `inactivityPeriods`         | GitPulse-calculated | Gaps **longer than 14 days** between consecutive commits (by author date), plus an `ongoing` gap from the last commit to the end of the window. The gap before the first commit is ignored (the repository may not have existed). The 10 longest are listed, in date order. |
| `longestInactivity`         | GitPulse-calculated | The longest of all such gaps.                                                                                                                                                                                                                                               |
| `recentCommits`             | GitHub-provided     | The 10 most recent commits by author date: SHA, first line of message, author.                                                                                                                                                                                              |

### Author date vs commit date

Git stores two timestamps. The **author date** is when a change was written, and survives rebases
and cherry-picks. The **commit date** is when it landed on the branch. GitPulse analyses activity by
**author date**. GitHub, however, filters and orders its commit list by **commit date**. Two
consequences:

1. `meta.sampleSize` can be larger than `totalCommits`: commits that _landed_ inside the window but
   were _written_ before it are fetched but not counted.
2. **Truncation.** At most 1,000 commits (10 pages) are fetched per window. If a window holds more,
   `meta.truncated` is `true` and the window is **shortened** to start at the oldest _commit date_ in
   the sample (`meta.since` > `meta.requestedSince`). Every commit authored after that point is
   guaranteed to be in the sample, so the reported weeks are complete rather than silently
   under-counted.

### Author identity

A commit is attributed to its linked GitHub account (`login`) when GitHub can match the commit
email to an account. Otherwise it is grouped by Git author name under the key `git:<name>`.
The same person using two unlinked emails or names therefore appears twice; GitPulse does not guess
merges. Emails are never read or stored.

## Contributors: `GET /api/v1/repositories/{owner}/{repo}/contributors`

Covers **all-time** commits on the default branch by contributors with a linked GitHub account.

| Field                          | Source              | Definition                                                                                                                                          |
| ------------------------------ | ------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------- |
| `commits`                      | GitHub-provided     | `contributions` from GitHub's contributors list.                                                                                                    |
| `sharePercent`                 | GitPulse-calculated | `commits / totalCommits × 100`, where `totalCommits` is the sum over listed contributors.                                                           |
| `topContributorSharePercent`   | GitPulse-calculated | Share of the contributor with the most commits.                                                                                                     |
| `contributorsForHalfOfCommits` | GitPulse-calculated | Fewest contributors whose commits add up to ≥ 50% of `totalCommits` (sort descending, running sum). A description of distribution, not a judgement. |
| `additions`, `deletions`       | GitHub-provided     | All-time lines added/removed, summed from GitHub's weekly contributor statistics. `null` = GitHub did not provide a value for this person.          |
| `lineStatsStatus`              | GitPulse-calculated | See below.                                                                                                                                          |

`lineStatsStatus`:

- `AVAILABLE`: line counts are included where GitHub provides them.
- `PENDING`: GitHub is still computing statistics (it answered `202 Accepted`). Retry after a
  few seconds; for some repositories this takes minutes. Pending results are never cached.
- `UNAVAILABLE`: GitHub provides no line counts. This happens for repositories with 10,000+
  commits, where GitHub returns zeros; GitPulse reports that as "unavailable", never as zero.

Known gaps in GitHub's data, surfaced rather than hidden:

- The contributor list links at most **500** accounts (`truncated: true` if the page cap is hit).
- Line statistics cover only the **top 100** contributors, and **exclude merge commits**. A
  contributor whose only commits are merges has `additions: null`.
- `totalCommits` here usually differs from `totalCommitsAllTime` on the commits endpoint: it
  excludes commits not linked to any GitHub account.
- For some very large repositories GitHub refuses to list contributors; the response then has
  `available: false`.

## Pull requests: `GET /api/v1/repositories/{owner}/{repo}/pull-requests`

**Totals** are all-time:

| Field                       | How it is obtained                                                                                                                                                                                   |
| --------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `totals.open`, `.closed`    | GitHub-provided. `GET /pulls?state=…&per_page=1`: the page number of the `rel="last"` link is the total (one request each). `closed` includes merged.                                                |
| `totals.merged`             | GitHub-provided via the Search API (`repo:o/r is:pr is:merged`); the pulls endpoint cannot filter by merged. **`null`** when the Search API rate limit is hit or GitHub reports an incomplete count. |
| `totals.closedWithoutMerge` | GitPulse-calculated: `closed - merged`; `null` when `merged` is.                                                                                                                                     |

**Statistics** describe the **cohort of pull requests opened in the window** (by `created_at`):

| Field                                       | Definition                                                                                                                                                                                                               |
| ------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `opened`                                    | Pull requests created in the window.                                                                                                                                                                                     |
| `merged`, `closedWithoutMerge`, `stillOpen` | What has happened to that cohort **so far**. A pull request opened last week may simply still be open.                                                                                                                   |
| `mergedPercentOfClosed`                     | `merged / (merged + closedWithoutMerge) × 100`; `null` if none are closed. Open ones are excluded because their outcome is unknown.                                                                                      |
| `timeToMerge`                               | `merged_at - created_at` over merged pull requests of the cohort: **median** and **90th percentile** (nearest-rank), in hours. Not a mean: one pull request left open for years would distort it. `null` if none merged. |
| `weekly[].opened` / `.merged`               | Cohort pull requests created / merged in each week.                                                                                                                                                                      |
| `topAuthors`                                | Pull requests opened in the window, per author.                                                                                                                                                                          |

The pulls endpoint has no date filter. GitPulse lists newest first and **stops paging** once a page
ends before the window (at most 5 pages = 500 pull requests). If the cap is reached first,
`meta.truncated` is `true` and the window is shortened to the oldest `created_at` fetched.

## Issues: `GET /api/v1/repositories/{owner}/{repo}/issues`

GitHub treats every pull request as an issue. **GitPulse removes pull requests everywhere**
(items carrying a `pull_request` field).

| Field                                     | Definition                                                                                                                                                                                                  |
| ----------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `issuesEnabled`                           | `false` when the repository has Issues turned off; totals and statistics are then `null`.                                                                                                                   |
| `totals.open`                             | Repository `open_issues_count` (which includes open pull requests) **minus** open pull requests.                                                                                                            |
| `totals.closed`                           | Search API `repo:o/r is:issue is:closed`. The issues list uses **cursor pagination** (no `rel="last"` link), so the `per_page=1` counting trick cannot be used. `null` when the Search API is rate limited. |
| `opened`, `closed`, `stillOpen`           | Cohort of issues created in the window, as for pull requests.                                                                                                                                               |
| `closedAsCompleted`, `closedAsNotPlanned` | GitHub's `state_reason` of closed cohort issues. `closedOther` = `duplicate` or no reason recorded (common for older issues).                                                                               |
| `timeToClose`                             | `closed_at - created_at`: median and 90th percentile, in hours.                                                                                                                                             |
| `weekly[].opened` / `.closed`             | Cohort issues created / closed in each week.                                                                                                                                                                |

Pull requests and issues share the same pages of GitHub's issues list, so in
pull-request-heavy repositories a page holds few issues and the 5-page cap is reached sooner.

## Recent activity: `GET /api/v1/repositories/{owner}/{repo}/activity`

Fixed look-back periods ending at the time of the request. **There is no overall score.**

| Field                                                    | Definition                                                                                                 |
| -------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------- |
| `lastCommitAt`, `daysSinceLastCommit`                    | Newest author date on the default branch within the last year; `null` if none.                             |
| `lastPushAt`                                             | Repository `pushed_at` (any branch).                                                                       |
| `commitsLast30Days`, `commitsLast90Days`                 | Commits authored in `(now - N days, now]`.                                                                 |
| `activeWeeksOfLast12`                                    | The last 84 days as twelve **rolling** 7-day periods ending now; how many contain a commit.                |
| `pullRequestsOpenedLast90Days`                           | Pull requests created in the last 90 days.                                                                 |
| `pullRequestsMergedLast90Days`                           | Pull requests merged in the last 90 days (among those created within the last year).                       |
| `issuesOpenedLast90Days`, `issuesClosedLast90Days`       | Likewise for issues; `null` when issues are disabled.                                                      |
| `commitsPartial`, `pullRequestsPartial`, `issuesPartial` | `true` when the sample did not reach back 90 days (page cap): counts are **lower bounds**, shown as "12+". |

The activity endpoint reuses the cached samples of the default one-year views, so it normally
costs no extra GitHub requests.
