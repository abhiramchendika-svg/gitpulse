# GitPulse REST API

Base URL (local): `http://localhost:8080`. All responses are JSON. All timestamps are ISO-8601 in
UTC. Field definitions and formulas are in [metrics.md](metrics.md).

## Errors

Every error is an [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem detail
(`Content-Type: application/problem+json`) with a stable `code`. Branch on `code`, not on `detail`.

| HTTP | `code`                 | Meaning                                                                   |
| ---- | ---------------------- | ------------------------------------------------------------------------- |
| 400  | `INVALID_INPUT`        | Bad owner/repo name, bad date format, or an invalid date range.           |
| 404  | `REPOSITORY_NOT_FOUND` | Repository does not exist **or is private** (GitHub reports both as 404). |
| 404  | `USER_NOT_FOUND`       | No GitHub user or organization with that name.                            |
| 404  | `NOT_FOUND`            | Unknown route.                                                            |
| 429  | `RATE_LIMITED`         | GitHub rate limit reached. Has `Retry-After` header and `resetAt` field.  |
| 502  | `GITHUB_ERROR`         | GitHub answered unexpectedly.                                             |
| 503  | `AI_NOT_CONFIGURED`    | Explanations are off: the server has no `ANTHROPIC_API_KEY`.              |
| 429  | `AI_LIMIT_REACHED`     | Hourly explanation limit reached. Has `Retry-After` and `resetAt`.        |
| 503  | `AI_UNAVAILABLE`       | The Anthropic API could not be reached or rejected the server's key.      |
| 502  | `AI_UNRELIABLE`        | The AI answer failed verification (or was declined); nothing is shown.    |
| 503  | `GITHUB_UNAVAILABLE`   | GitHub unreachable or returned 5xx.                                       |
| 503  | `GITHUB_AUTH_FAILED`   | The server's `GITHUB_TOKEN` was rejected (server misconfiguration).       |
| 500  | `INTERNAL_ERROR`       | Unexpected server error. No internal details are exposed.                 |

```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "Repository 'octocat/nope' was not found. It may not exist, or it may be private (GitHub reports both the same way).",
  "instance": "/api/v1/repositories/octocat/nope",
  "code": "REPOSITORY_NOT_FOUND"
}
```

## Path parameters

| Name    | Rule                                                               |
| ------- | ------------------------------------------------------------------ |
| `owner` | `^[A-Za-z0-9][A-Za-z0-9-]{0,38}$` (GitHub user/organization names) |
| `repo`  | `^[A-Za-z0-9._-]{1,100}$`, not `.` or `..`                         |

Names are case-insensitive; `Spring-Projects/Spring-Petclinic` and
`spring-projects/spring-petclinic` share one cache entry.

## Caching and cost

GitHub data is cached in memory for 10 minutes, so repeated requests (including different
parameters over the same data, e.g. `excludeBots`) cost no GitHub quota. Approximate GitHub
requests on a cold cache:

| Endpoint      | GitHub requests                                                                           |
| ------------- | ----------------------------------------------------------------------------------------- |
| overview      | 1                                                                                         |
| languages     | 1                                                                                         |
| commits       | 1 per 100 commits in the window (max 10) + 1 for the all-time count                       |
| contributors  | 1 per 100 contributors (max 5) + 1 for line statistics                                    |
| pull-requests | 1 per 100 pull requests (max 5, stops early) + 2 counts + 1 Search API request            |
| issues        | 1 per 100 issues and PRs (max 5, stops early) + 1 count + 1 Search API request            |
| activity      | usually 0: it reuses the cached default-window data                                       |
| files         | 1 per sampled commit (20 anonymous, up to 300 with a token); cached 24 h by SHA           |
| compare       | the sum of each repository's overview, languages, commits, contributor count and activity |

The Search API has its own, smaller limit (10 requests/minute anonymous, 30 with a token). When it
is exhausted, counts that depend on it are returned as `null` and the rest of the response is
unaffected.

---

## `GET /actuator/health`

Liveness check. `200 {"status":"UP"}`.

## `GET /api/v1/rate-limit`

Remaining GitHub quota of the backend. Does not consume quota.

```json
{
  "authenticated": false,
  "core": {
    "limit": 60,
    "remaining": 54,
    "used": 6,
    "resetAt": "2026-09-24T19:38:04Z"
  },
  "search": {
    "limit": 10,
    "remaining": 10,
    "used": 0,
    "resetAt": "2026-09-24T18:56:30Z"
  }
}
```

## `GET /api/v1/repositories/{owner}/{repo}`

Repository metadata.

```bash
curl http://localhost:8080/api/v1/repositories/spring-projects/spring-petclinic
```

```json
{
  "fullName": "spring-projects/spring-petclinic",
  "name": "spring-petclinic",
  "owner": {
    "login": "spring-projects",
    "type": "Organization",
    "avatarUrl": "…",
    "htmlUrl": "…"
  },
  "description": "A sample Spring-based application",
  "htmlUrl": "https://github.com/spring-projects/spring-petclinic",
  "homepage": null,
  "stars": 9539,
  "forks": 30621,
  "watchers": 1234,
  "openIssuesAndPullRequests": 4,
  "primaryLanguage": "CSS",
  "license": { "spdxId": "Apache-2.0", "name": "Apache License 2.0" },
  "topics": [],
  "createdAt": "2013-01-09T02:13:48Z",
  "updatedAt": "2026-09-24T10:00:00Z",
  "pushedAt": "2026-08-26T10:57:55Z",
  "sizeKb": 9000,
  "defaultBranch": "main",
  "archived": false,
  "fork": false,
  "hasIssues": true
}
```

Errors: `400 INVALID_INPUT`, `404 REPOSITORY_NOT_FOUND`, `429`, `503`.

## `GET /api/v1/repositories/{owner}/{repo}/languages`

```json
{
  "repository": "spring-projects/spring-petclinic",
  "statistics": {
    "totalBytes": 250000,
    "languages": [
      { "name": "CSS", "bytes": 156750, "percent": 62.7 },
      { "name": "Java", "bytes": 77750, "percent": 31.1 }
    ]
  }
}
```

## `GET /api/v1/repositories/{owner}/{repo}/commits`

Commit activity for a window of UTC days.

| Query param   | Type         | Default               | Rules                               |
| ------------- | ------------ | --------------------- | ----------------------------------- |
| `since`       | `yyyy-MM-dd` | 364 days before until | ≤ `until`; range ≤ 3650 days        |
| `until`       | `yyyy-MM-dd` | today (UTC)           | not in the future                   |
| `excludeBots` | boolean      | `false`               | removes bot commits before analysis |

```bash
curl "http://localhost:8080/api/v1/repositories/spring-projects/spring-petclinic/commits?since=2026-01-01&excludeBots=true"
```

Response (abridged):

```json
{
  "repository": "spring-projects/spring-petclinic",
  "meta": {
    "generatedAt": "2026-09-24T19:13:11Z",
    "since": "2026-01-01T00:00:00Z",
    "until": "2026-09-24T19:13:11Z",
    "requestedSince": "2026-01-01T00:00:00Z",
    "sampleSize": 40,
    "truncated": false,
    "botsExcluded": true,
    "timezone": "UTC"
  },
  "emptyRepository": false,
  "totalCommitsAllTime": 1042,
  "statistics": {
    "totalCommits": 38,
    "mergeCommits": 2,
    "botCommits": 0,
    "distinctAuthors": 17,
    "firstCommitAt": "2026-01-03T09:12:00Z",
    "lastCommitAt": "2026-08-26T10:57:55Z",
    "averagePerWeek": 1.0,
    "averagePerMonth": 4.34,
    "activeWeeks": 20,
    "totalWeeks": 39,
    "weekly": [{ "weekStart": "2025-12-29", "commits": 1 }],
    "monthly": [{ "month": "2026-01", "commits": 6 }],
    "byDayOfWeek": [5, 12, 14, 5, 4, 10, 2],
    "byHourOfDay": [0, 0, 1, "… 24 values"],
    "heatmap": [[0, 0, "… 24 values"], "… 7 rows"],
    "topAuthors": [
      {
        "key": "snicoll",
        "name": "snicoll",
        "login": "snicoll",
        "bot": false,
        "commits": 16,
        "sharePercent": 30.8
      }
    ],
    "inactivityPeriods": [
      {
        "from": "2026-08-26T10:57:55Z",
        "to": "2026-09-24T19:13:11Z",
        "days": 29.3,
        "ongoing": true
      }
    ],
    "longestInactivity": {
      "from": "…",
      "to": "…",
      "days": 36.8,
      "ongoing": false
    },
    "recentCommits": [
      {
        "sha": "818c413…",
        "shortSha": "818c413",
        "headline": "docs: Fix formatting in Docker container instructions",
        "authorName": "snicoll",
        "authorLogin": "snicoll",
        "authoredAt": "2026-08-26T10:57:55Z",
        "merge": false,
        "htmlUrl": "https://github.com/spring-projects/spring-petclinic/commit/818c413…"
      }
    ]
  }
}
```

An empty repository returns `200` with `"emptyRepository": true` and zero counts, not an error.

Errors: `400 INVALID_INPUT` (bad date, reversed or too-long range, future `until`), `404`, `429`, `503`.

## `GET /api/v1/repositories/{owner}/{repo}/contributors`

All-time contributors with linked GitHub accounts (top 100 listed; totals cover up to 500).

```json
{
  "repository": "octocat/hello-world",
  "generatedAt": "2026-09-24T19:13:12Z",
  "available": true,
  "truncated": false,
  "statistics": {
    "contributorCount": 3,
    "totalCommits": 3,
    "topContributorSharePercent": 33.3,
    "contributorsForHalfOfCommits": 2,
    "botCount": 0,
    "lineStatsStatus": "AVAILABLE",
    "contributors": [
      {
        "login": "Spaceghost",
        "avatarUrl": "…",
        "htmlUrl": "…",
        "bot": false,
        "commits": 1,
        "sharePercent": 33.3,
        "additions": 1,
        "deletions": 1
      },
      {
        "login": "octocat",
        "avatarUrl": "…",
        "htmlUrl": "…",
        "bot": false,
        "commits": 1,
        "sharePercent": 33.3,
        "additions": null,
        "deletions": null
      }
    ]
  }
}
```

When `lineStatsStatus` is `PENDING`, call again after a few seconds. Errors: `400`, `404`, `429`, `503`.

## `GET /api/v1/repositories/{owner}/{repo}/pull-requests`

Same query parameters as `/commits` (`since`, `until`, `excludeBots`). Statistics describe pull
requests **opened** in the window; totals are all-time.

```json
{
  "repository": "spring-projects/spring-petclinic",
  "meta": {
    "since": "2025-12-05T10:12:26Z",
    "requestedSince": "2025-09-25T00:00:00Z",
    "sampleSize": 500,
    "truncated": true
  },
  "totals": {
    "open": 4,
    "closed": 2269,
    "merged": 205,
    "closedWithoutMerge": 2064
  },
  "statistics": {
    "opened": 500,
    "merged": 19,
    "closedWithoutMerge": 477,
    "stillOpen": 4,
    "openedByBots": 0,
    "mergedPercentOfClosed": 3.8,
    "timeToMerge": { "count": 19, "medianHours": 165.5, "p90Hours": 760.6 },
    "weekly": [{ "weekStart": "2025-12-01", "opened": 4, "merged": 0 }],
    "topAuthors": [{ "login": "Gopikatla", "bot": false, "count": 8 }],
    "recent": [
      {
        "number": 2669,
        "title": "…",
        "authorLogin": "…",
        "draft": false,
        "createdAt": "…",
        "status": "closed",
        "htmlUrl": "…"
      }
    ]
  }
}
```

(`meta` abridged.) `totals.merged` and `totals.closedWithoutMerge` are `null` when GitHub's Search
API is rate limited; the response is still `200`. Errors: `400`, `404`, `429`, `503`.

## `GET /api/v1/repositories/{owner}/{repo}/issues`

Same parameters. Pull requests are excluded.

```json
{
  "repository": "spring-projects/spring-petclinic",
  "issuesEnabled": true,
  "totals": { "open": 0, "closed": 392 },
  "statistics": {
    "opened": 23,
    "closed": 23,
    "stillOpen": 0,
    "openedByBots": 0,
    "closedAsCompleted": 22,
    "closedAsNotPlanned": 1,
    "closedOther": 0,
    "timeToClose": { "count": 23, "medianHours": 54.2, "p90Hours": 1312.2 },
    "weekly": [{ "weekStart": "2025-12-15", "opened": 1, "closed": 1 }],
    "topOpeners": [{ "login": "…", "bot": false, "count": 2 }],
    "recent": [
      {
        "number": 2600,
        "title": "…",
        "authorLogin": "…",
        "createdAt": "…",
        "open": false,
        "comments": 3,
        "htmlUrl": "…"
      }
    ]
  }
}
```

(`meta` omitted.) With Issues turned off: `"issuesEnabled": false, "totals": null,
"statistics": null`. `totals.closed` is `null` when the Search API is rate limited.

## `GET /api/v1/repositories/{owner}/{repo}/activity`

No parameters. Factual indicators for the last 30/90 days; there is no score.

```json
{
  "repository": "spring-projects/spring-petclinic",
  "generatedAt": "2026-09-24T19:56:23Z",
  "indicators": {
    "lastCommitAt": "2026-08-19T13:20:54Z",
    "daysSinceLastCommit": 36.3,
    "lastPushAt": "2026-08-26T10:57:55Z",
    "commitsLast30Days": 0,
    "commitsLast90Days": 8,
    "activeWeeksOfLast12": 5,
    "commitsPartial": false,
    "pullRequestsOpenedLast90Days": 114,
    "pullRequestsMergedLast90Days": 8,
    "pullRequestsPartial": false,
    "issuesOpenedLast90Days": 5,
    "issuesClosedLast90Days": 5,
    "issuesPartial": false
  }
}
```

## `GET /api/v1/repositories/{owner}/{repo}/files`

Most-changed files and directories in a sample of recent commits. **Costs one GitHub request per
sampled commit** (unless cached), so clients should call it only on demand.

| Query param | Type    | Default                              | Rules                                   |
| ----------- | ------- | ------------------------------------ | --------------------------------------- |
| `sample`    | integer | 20 without a token, 100 with a token | 1–1000; capped by the server (20 / 300) |

```json
{
  "repository": "spring-projects/spring-petclinic",
  "meta": {
    "generatedAt": "2026-09-24T20:23:12Z",
    "requestedSample": 20,
    "sampleLimit": 20,
    "authenticated": false,
    "candidateCommits": 55,
    "mergeCommitsSkipped": 2
  },
  "statistics": {
    "commitsAnalyzed": 20,
    "sampleFrom": "2026-03-07T17:53:21Z",
    "sampleTo": "2026-08-19T13:20:54Z",
    "filesTouched": 47,
    "commitsWithTruncatedFiles": 0,
    "mostFrequentlyChanged": [
      {
        "path": "README.md",
        "commits": 4,
        "additions": 12,
        "deletions": 5,
        "churn": 17,
        "distinctAuthors": 3,
        "lastChangedAt": "2026-08-19T13:20:54Z",
        "deleted": false
      }
    ],
    "highestChurn": ["… same shape …"],
    "directories": [
      {
        "path": "src/test/java/org/springframework/samples/petclinic/owner",
        "commits": 7,
        "filesTouched": 5,
        "churn": 160
      }
    ],
    "recentlyChanged": ["… same shape …"]
  }
}
```

Errors: `400` (bad name or `sample` out of range), `404`, `429`, `503`.

## `GET /api/v1/compare?repos={owner}/{repo},{owner}/{repo}`

Side-by-side facts for exactly two different repositories (fetched in parallel). No score.

```bash
curl "http://localhost:8080/api/v1/compare?repos=spring-projects/spring-petclinic,spring-guides/gs-rest-service"
```

```json
{
  "generatedAt": "2026-09-24T20:24:00Z",
  "repositories": [
    {
      "fullName": "spring-projects/spring-petclinic",
      "stars": 9539,
      "forks": 30621,
      "contributors": 131,
      "ageYears": 13.7,
      "primaryLanguage": "CSS",
      "topLanguages": [{ "name": "CSS", "bytes": 156750, "percent": 62.7 }],
      "license": "Apache-2.0",
      "commits": {
        "total": 52,
        "averagePerWeek": 1.0,
        "activeWeeks": 25,
        "totalWeeks": 53,
        "distinctAuthors": 22,
        "truncated": false,
        "since": "2025-09-25T00:00:00Z"
      },
      "weekly": [{ "weekStart": "2025-09-22", "commits": 1 }],
      "activity": { "commitsLast30Days": 0, "commitsLast90Days": 8, "…": "…" },
      "…": "…"
    },
    { "fullName": "spring-guides/gs-rest-service", "…": "…" }
  ]
}
```

Errors: `400 INVALID_INPUT` (not exactly two, invalid, or identical repositories),
`404 REPOSITORY_NOT_FOUND` (the detail names the missing repository), `429`, `503`.

## `GET /api/v1/users/{username}`

Public profile analysis for a user or organization. Costs about 1 + (1 per 100 repositories,
max 3) + (up to 3 for events) GitHub requests.

```bash
curl http://localhost:8080/api/v1/users/torvalds
```

```json
{
  "profile": {
    "login": "torvalds",
    "type": "User",
    "name": "Linus Torvalds",
    "company": "Linux Foundation",
    "location": "Portland, OR",
    "publicRepos": 12,
    "followers": 325097,
    "following": 0,
    "createdAt": "2011-09-03T15:26:22Z",
    "…": "…"
  },
  "meta": {
    "generatedAt": "2026-09-25T06:09:31Z",
    "repositoriesTruncated": false,
    "eventsAnalyzed": true
  },
  "statistics": {
    "repositoriesAnalyzed": 12,
    "originalRepositories": 9,
    "forkedRepositories": 3,
    "archivedRepositories": 1,
    "starsReceived": 262078,
    "forksReceived": 66657,
    "languages": [{ "name": "C", "repositories": 8, "percent": 88.9 }],
    "pushedLast30Days": 2,
    "pushedLast90Days": 3,
    "pushedLastYear": 6,
    "mostStarred": [
      { "fullName": "torvalds/linux", "stars": 250078, "…": "…" }
    ],
    "recentlyPushed": ["… same shape …"],
    "createdPerYear": [{ "year": 2011, "repositories": 1 }],
    "events": {
      "count": 112,
      "activeDays": 33,
      "repositoriesTouched": 2,
      "byType": [{ "type": "PushEvent", "label": "Pushes", "count": 99 }],
      "topRepositories": [{ "repository": "torvalds/linux", "events": 110 }]
    }
  }
}
```

Errors: `400 INVALID_INPUT` (invalid name), `404 USER_NOT_FOUND`, `429`, `503`.

## `GET /api/v1/features`

Which optional features this server has enabled. The frontend hides what is off.

```json
{ "explanations": false }
```

## `POST /api/v1/repositories/{owner}/{repo}/explanation`

**Optional, off by default.** A short plain-English summary of the dashboard's numbers, written
by Claude (Anthropic) and **verified against those numbers** before it is returned. Enabled only
when the server has `ANTHROPIC_API_KEY`. Query parameters: `since`, `until`, `excludeBots`, as for
the windowed endpoints.

It is `POST` because every uncached call costs money: link prefetchers and crawlers never send
`POST`. Answers are cached for 10 minutes per repository and window, and the server allows at most
`GITPULSE_AI_MAX_PER_HOUR` (default 20) model calls per hour in total. Cached answers don't
count; failed calls do, because they can cost money too.

What is sent to the model: only numbers GitPulse has already calculated (with ids and labels). No
logins, names, commit messages, titles or descriptions. Counts that are lower bounds are left out.

```json
{
  "repository": "spring-projects/spring-petclinic",
  "generatedAt": "2026-09-25T12:00:00Z",
  "model": "claude-opus-5",
  "window": {
    "since": "2025-09-26T00:00:00Z",
    "until": "2026-09-25T12:00:00Z",
    "botsExcluded": false
  },
  "sentences": [
    {
      "text": "In the last 365 days there were 240 commits.",
      "basedOn": [
        {
          "id": "window.days",
          "label": "Length of the selected period, in days",
          "value": 365
        },
        {
          "id": "commits.total",
          "label": "Commits in the selected period",
          "value": 240
        }
      ]
    }
  ],
  "sentencesRemoved": 1
}
```

`sentencesRemoved` counts sentences the model wrote that failed verification. How verification
works: [metrics.md](metrics.md#ai-explanation-not-a-metric).

Errors: `AI_NOT_CONFIGURED`, `AI_LIMIT_REACHED`, `AI_UNAVAILABLE`, `AI_UNRELIABLE`, plus the usual
GitHub errors (these happen before any model call, so they cost nothing).

---

## Exported files

Export happens **in the browser**, from the responses above that the page already holds. It
makes no extra requests and costs no GitHub quota, and the file always matches what was on
screen. There is no export endpoint.

### JSON report

The "JSON report" button on the dashboard, a profile or a comparison saves:

```json
{
  "generator": "GitPulse",
  "schemaVersion": 1,
  "kind": "repository",
  "subject": "octocat/hello-world",
  "exportedAt": "2026-09-25T10:00:00.000Z",
  "notes": ["Public GitHub data only. …"],
  "data": {
    "filters": { "range": "90d", "since": "2026-06-27", "excludeBots": false },
    "notIncluded": [{ "section": "fileActivity", "reason": "NOT_REQUESTED" }],
    "overview": { "…": "GET /api/v1/repositories/{owner}/{repo}" },
    "activity": { "…": "…/activity" },
    "commits": { "…": "…/commits" },
    "pullRequests": { "…": "…/pull-requests" },
    "issues": { "…": "…/issues" },
    "contributors": { "…": "…/contributors" },
    "languages": { "…": "…/languages" },
    "fileActivity": null
  }
}
```

- `kind` is `repository`, `profile` or `comparison`. For `profile` and `comparison`, `data` is
  the unchanged response of `GET /api/v1/users/{username}` or `GET /api/v1/compare`.
- For `repository`, each section is the unchanged response of its endpoint, **including its
  `meta`** (analysis window, sample size, truncation), so an export is self-describing. A section
  that failed or was never requested is `null` and listed in `notIncluded` with the error `code`
  (e.g. `RATE_LIMITED`) or `NOT_REQUESTED`.
- The button is disabled while any section is loading, so a report never mixes two time ranges.
- `schemaVersion` increases only on breaking changes to this envelope. Fields inside `data`
  follow this API reference.

### CSV

Every chart's table view, and the full contributor list, has a "Download CSV" button.

- RFC 4180: comma-separated, CRLF line endings, fields quoted when they contain `,` `"` or a
  line break. UTF-8 with a byte-order mark so Excel shows non-ASCII names correctly.
- Numbers are exact (never `12.3K`). Missing values (e.g. line counts GitHub did not provide) are
  empty cells, not `0`.
- **CSV injection protection:** text cells that start with `=`, `+`, `-`, `@`, tab or carriage
  return are prefixed with `'`, so a commit message or name such as `=HYPERLINK(…)` is shown as
  text instead of running as a spreadsheet formula.
- File names: `gitpulse-<subject>-<table>-<yyyymmdd>.csv`, e.g.
  `gitpulse-octocat-hello-world-contributors-20260925.csv` (date in UTC).
