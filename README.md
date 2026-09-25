# GitPulse

**GitHub repository & commit analyzer.** GitPulse turns public GitHub activity (commits,
contributors, languages, pull requests, issues) into factual, descriptive analytics.

> **Status: early development (Phase 6 complete).** Analyse a repository (overview, activity,
> commits, pull requests, issues, contributors, languages, file activity), compare two repositories,
> or look at a GitHub user's public profile, and export any of it as JSON or CSV. See
> [Roadmap](#roadmap).

<!-- Screenshots: add docs/screenshots/dashboard.png once the repository is published. -->

## Features

- **Overview:** stars, forks, watchers, open issues + PRs, license, dates, size.
- **Recent activity:** last commit, commits in the last 30/90 days, active weeks, pull requests
  and issues opened/merged/closed in the last 90 days. Plain counts; no "health score".
- **Commit activity** for the last 30 days, 90 days or year: commits per week, when commits
  happen (weekday × UTC hour), most active authors, periods of inactivity, recent commits, with
  an option to exclude bots.
- **Pull requests and issues** for the same period: opened vs merged/closed per week, median
  and 90th-percentile time to merge/close, close reasons, most active authors, and all-time
  totals (pull requests are never counted as issues).
- **Contributors:** commit shares, how many people account for half of all commits, and lines
  added/deleted where GitHub provides them.
- **Languages:** share of code by size.
- **File activity** (on demand): most frequently changed files, highest churn, busiest
  directories, based on the most recent commits; renames are followed.
- **Profiles:** enter a username for public facts: repositories, stars received (own repos
  only), languages by repository, recent public activity. No score; nothing private.
- **Compare** two repositories: the same facts side by side, charts on a shared scale, no winner.
- **Export:** a JSON report of any dashboard, profile or comparison, and CSV for every chart
  table and the contributor list. Runs in the browser, so it costs no GitHub requests; CSV
  files are protected against formula injection. Format: [docs/api.md](docs/api.md#exported-files).
- Every view is a shareable link (`?repo=owner/name&range=90d`), and every chart has a table
  view. Paste `owner/repo`, a username or any GitHub URL.

GitPulse analyses _data_, not _people_: it reports activity, frequency and distribution, and
every calculated metric has a documented formula in [docs/metrics.md](docs/metrics.md). It
never scores developer productivity or code quality.

## Architecture

```
React + TypeScript (Vite)  ──HTTP/JSON──▶  Spring Boot backend  ──REST──▶  api.github.com
   (no GitHub access,                      Controller → Service →
    never sees a token)                    GitHubClient / Analyzers
```

- `backend/`: Java 21+, Spring Boot 4, Maven. Owns all GitHub communication, the token,
  pagination, rate-limit handling, caching (Caffeine) and analytics. Analyzers in
  `analysis/` are plain Java with no Spring or HTTP dependencies, so every formula is unit-tested.
- `frontend/`: React 19 + TypeScript + Vite, Recharts for charts. Talks only to the backend;
  dashboard state lives in the URL. The dashboard (and its chart library) is lazy-loaded.

Design decisions, caching, rate-limit strategy and security: [docs/architecture.md](docs/architecture.md).

## Prerequisites

| Tool    | Version                                           |
| ------- | ------------------------------------------------- |
| JDK     | 21 or newer                                       |
| Node.js | 24 (LTS) or newer, with npm                       |
| Maven   | not required: use the included wrapper (`./mvnw`) |

## Quick start

```bash
# 1. Configure (optional but recommended): add a GitHub token
cp .env.example .env        # then edit .env and set GITHUB_TOKEN

# 2. Backend (terminal 1) -> http://localhost:8080
cd backend
./mvnw spring-boot:run      # Windows: mvnw.cmd spring-boot:run

# 3. Frontend (terminal 2) -> http://localhost:5173
cd frontend
npm install
npm run dev
```

Open http://localhost:5173 and enter a repository, e.g. `spring-projects/spring-petclinic`.
The header shows how much GitHub API quota the backend has left.

## Environment variables

| Variable                        | Required | Default                 | Purpose                                              |
| ------------------------------- | -------- | ----------------------- | ---------------------------------------------------- |
| `GITHUB_TOKEN`                  | No       | _(none)_                | Raises GitHub's limit from 60 to 5,000 requests/hour |
| `PORT`                          | No       | `8080`                  | Backend port                                         |
| `GITPULSE_CORS_ALLOWED_ORIGINS` | No       | `http://localhost:5173` | Browser origins allowed to call the API              |

Variables can be set in the shell or in a `.env` file at the repository root (git-ignored).
Use a **fine-grained, read-only, public-repositories** token. Never commit it.

## API (so far)

| Method | Path                                                | Description                                       |
| ------ | --------------------------------------------------- | ------------------------------------------------- |
| GET    | `/actuator/health`                                  | Liveness: `{"status":"UP"}`                       |
| GET    | `/api/v1/rate-limit`                                | Remaining GitHub quota (core and search APIs)     |
| GET    | `/api/v1/repositories/{owner}/{repo}`               | Repository metadata                               |
| GET    | `/api/v1/repositories/{owner}/{repo}/languages`     | Language breakdown                                |
| GET    | `/api/v1/repositories/{owner}/{repo}/commits`       | Commit activity (`since`, `until`, `excludeBots`) |
| GET    | `/api/v1/repositories/{owner}/{repo}/contributors`  | Contributor distribution and line changes         |
| GET    | `/api/v1/repositories/{owner}/{repo}/pull-requests` | Pull request activity + all-time totals           |
| GET    | `/api/v1/repositories/{owner}/{repo}/issues`        | Issue activity + all-time totals (PRs excluded)   |
| GET    | `/api/v1/repositories/{owner}/{repo}/activity`      | Recent-activity indicators (30/90 days)           |
| GET    | `/api/v1/repositories/{owner}/{repo}/files`         | File activity from recent commits (`sample`)      |
| GET    | `/api/v1/users/{username}`                          | Public profile analysis                           |
| GET    | `/api/v1/compare?repos=a/b,c/d`                     | Two repositories side by side                     |

Full reference with examples: [docs/api.md](docs/api.md). Metric definitions:
[docs/metrics.md](docs/metrics.md).

Errors use [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem details with a stable
`code` field, e.g.:

```json
{
  "status": 429,
  "title": "Too Many Requests",
  "code": "RATE_LIMITED",
  "detail": "GitHub API rate limit reached. ...",
  "resetAt": "2026-09-25T10:00:00Z"
}
```

## Testing

```bash
cd backend && ./mvnw verify                 # unit + web + client tests (no live GitHub calls)
cd frontend && npm test                     # Vitest + React Testing Library
```

Formatting: `./mvnw spotless:apply` (Java, google-java-format) and `npm run format` (Prettier).

## Roadmap

1. ✅ Project foundation: backend, frontend, GitHub client, health, CI
2. ✅ Repository analysis: metadata, commits, contributors, languages
3. ✅ Dashboard: charts and responsive layout
4. ✅ Pull request and issue analytics, recent activity, rate-limit handling
5. ✅ (5a) File activity, repository comparison, per-cache lifetimes, bounded parallel fetching
   · ✅ (5b) GitHub profile analysis
6. ✅ Export (CSV/JSON) and open-source documentation
7. Optional AI features (evaluated, not assumed)

## Known limitations

- Public repositories only. GitHub returns 404 for private repositories, so "private" and
  "not found" look the same.
- Without a token, the 60 requests/hour limit is enough for only a few analyses.
- Commit analytics cover the default branch and at most 1,000 commits per window; larger windows
  are shortened and flagged (`meta.truncated`). Times are grouped in UTC.
- Line statistics come from GitHub and may be pending, missing for 10,000+-commit repositories,
  and exclude merge commits. See [docs/metrics.md](docs/metrics.md).
- Pull request and issue statistics use at most 500 items per window (flagged when shortened).
  All-time merged and closed-issue counts use GitHub's Search API and show "Unavailable" when
  its separate rate limit is reached.
- File activity samples recent commits (20 without a token) rather than the whole history, since
  each commit costs one GitHub request.

## Documentation

| Document                                                       | Contents                                                 |
| -------------------------------------------------------------- | -------------------------------------------------------- |
| [docs/api.md](docs/api.md)                                     | REST API reference, errors, request costs, export format |
| [docs/metrics.md](docs/metrics.md)                             | Definition and formula of every number                   |
| [docs/architecture.md](docs/architecture.md)                   | How it's built and why                                   |
| [docs/interview-preparation.md](docs/interview-preparation.md) | Talking about the project in interviews                  |

## Contributing

Contributions are welcome: see [CONTRIBUTING.md](CONTRIBUTING.md) for setup, the ground rules
(descriptive metrics only, every number documented) and the pull request process. Everyone
taking part is expected to follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## Security

Please report vulnerabilities privately, as described in [SECURITY.md](SECURITY.md), not in a
public issue.

## License

[MIT](LICENSE)
