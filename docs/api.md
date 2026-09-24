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
| 404  | `NOT_FOUND`            | Unknown route.                                                            |
| 429  | `RATE_LIMITED`         | GitHub rate limit reached. Has `Retry-After` header and `resetAt` field.  |
| 502  | `GITHUB_ERROR`         | GitHub answered unexpectedly.                                             |
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

| Endpoint     | GitHub requests                                                     |
| ------------ | ------------------------------------------------------------------- |
| overview     | 1                                                                   |
| languages    | 1                                                                   |
| commits      | 1 per 100 commits in the window (max 10) + 1 for the all-time count |
| contributors | 1 per 100 contributors (max 5) + 1 for line statistics              |

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
  "fork": false
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
