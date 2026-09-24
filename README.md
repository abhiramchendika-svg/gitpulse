# GitPulse

**GitHub repository & commit analyzer.** GitPulse turns public GitHub activity (commits,
contributors, languages, pull requests, issues) into factual, descriptive analytics.

> **Status: early development (Phase 2 – repository analysis).** The backend analyses repository
> metadata, languages, commit activity and contributors. The dashboard arrives in Phase 3; see
> [Roadmap](#roadmap).

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
- `frontend/`: React 19 + TypeScript + Vite. Talks only to the backend.

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

Open http://localhost:5173. The status panel shows whether the backend is up and how much
GitHub API quota remains.

## Environment variables

| Variable                        | Required | Default                 | Purpose                                              |
| ------------------------------- | -------- | ----------------------- | ---------------------------------------------------- |
| `GITHUB_TOKEN`                  | No       | _(none)_                | Raises GitHub's limit from 60 to 5,000 requests/hour |
| `PORT`                          | No       | `8080`                  | Backend port                                         |
| `GITPULSE_CORS_ALLOWED_ORIGINS` | No       | `http://localhost:5173` | Browser origins allowed to call the API              |

Variables can be set in the shell or in a `.env` file at the repository root (git-ignored).
Use a **fine-grained, read-only, public-repositories** token. Never commit it.

## API (so far)

| Method | Path                                               | Description                                       |
| ------ | -------------------------------------------------- | ------------------------------------------------- |
| GET    | `/actuator/health`                                 | Liveness: `{"status":"UP"}`                       |
| GET    | `/api/v1/rate-limit`                               | Remaining GitHub quota (core and search APIs)     |
| GET    | `/api/v1/repositories/{owner}/{repo}`              | Repository metadata                               |
| GET    | `/api/v1/repositories/{owner}/{repo}/languages`    | Language breakdown                                |
| GET    | `/api/v1/repositories/{owner}/{repo}/commits`      | Commit activity (`since`, `until`, `excludeBots`) |
| GET    | `/api/v1/repositories/{owner}/{repo}/contributors` | Contributor distribution and line changes         |

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
3. Dashboard: charts and responsive layout
4. Pull request and issue analytics
5. File activity, repository comparison, profile analysis, caching
6. Export (CSV/JSON) and full open-source documentation
7. Optional AI features (evaluated, not assumed)

## Known limitations

- Public repositories only. GitHub returns 404 for private repositories, so "private" and
  "not found" look the same.
- Without a token, the 60 requests/hour limit is enough for only a few analyses.
- Commit analytics cover the default branch and at most 1,000 commits per window; larger windows
  are shortened and flagged (`meta.truncated`). Times are grouped in UTC.
- Line statistics come from GitHub and may be pending, missing for 10,000+-commit repositories,
  and exclude merge commits. See [docs/metrics.md](docs/metrics.md).

## License

[MIT](LICENSE)
