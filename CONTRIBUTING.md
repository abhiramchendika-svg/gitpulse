# Contributing to GitPulse

Thanks for your interest! GitPulse is a small project, and bug reports, documentation fixes and
code are all welcome. This guide explains how to get set up and what a good change looks like.

By taking part you agree to follow the [Code of Conduct](CODE_OF_CONDUCT.md). Please report
security problems privately as described in [SECURITY.md](SECURITY.md), not in a public issue.

## Ground rules

These are what make GitPulse GitPulse. A change that breaks one will be asked to change, however
good the code is.

1. **Describe activity; never judge people.** No productivity scores, rankings of developers,
   "health scores" or anything that turns counts into a verdict. Comparisons show facts side by
   side and never pick a winner.
2. **Every number has a source.** A metric is either _GitHub-provided_ (copied from the API) or
   _GitPulse-calculated_. Calculated metrics need a formula in [docs/metrics.md](docs/metrics.md),
   and the UI must say which kind a number is. If the code and the docs disagree, that is a bug.
3. **Missing is not zero.** When GitHub doesn't provide a value (rate-limited Search API, pending
   statistics), show "Unavailable", never `0`. When a sample is truncated, say so.
4. **Public data only, and as little of it as needed.** Never read or show email addresses. Never
   log or return the GitHub token.
5. **Respect the GitHub API budget.** Anonymous users get 60 requests per hour. Any change that
   adds GitHub requests must update the cost table in [docs/api.md](docs/api.md#caching-and-cost),
   cap pagination, and go through the cache. Tests never call the real GitHub API.

## Getting set up

You need JDK 21 or newer and Node.js 24 or newer. See the [README](README.md#quick-start) for
running the app; a GitHub token is optional but makes manual testing much easier.

```bash
git clone <your fork URL>
cd <repository folder>
cp .env.example .env         # optional: add a fine-grained, read-only GITHUB_TOKEN

cd backend && ./mvnw verify  # Windows: mvnw.cmd verify
cd ../frontend && npm ci && npm test
```

**Windows notes:** stop a running backend before `./mvnw verify` (the running jar locks
`target/`). If Spotless reports line-ending changes, run `./mvnw spotless:apply`; the repository
uses LF line endings (see `.gitattributes`).

## Before you open a pull request

Run the same checks as CI. All of them must pass.

```bash
# backend/
./mvnw spotless:apply        # format (google-java-format)
./mvnw verify                # compile + all tests

# frontend/
npm run format               # Prettier
npm run lint                 # oxlint
npm run typecheck
npm test
npm run build
```

### Tests

- **Backend:** analyzers in `analysis/` are plain Java: test every formula there, including edge
  cases (empty input, a single item, ties, time-zone boundaries). Test GitHub parsing and error
  mapping in `GitHubClient*Test` against recorded-shape JSON, not the live API. Test controllers
  with `@WebMvcTest`.
- **Frontend:** test behaviour through the UI with React Testing Library (roles and labels, not
  class names). `App.test.tsx` fakes the backend with a mocked `fetch`.
- A bug fix should come with a test that fails without the fix.

### Style

- Follow the formatters; don't hand-format.
- Match the surrounding code: comments explain _why_, not _what_.
- Charts need an accessible table view (`ChartTable`) and must not rely on colour alone.
- Keep new dependencies to a minimum and explain why in the pull request.

## Commit messages

GitPulse uses [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>: <short summary in the imperative>

<optional body: what and why>
```

Common types: `feat`, `fix`, `docs`, `test`, `refactor`, `perf`, `build`, `ci`, `chore`.
Examples: `fix: count closed issues with the Search API`, `docs: explain the commit window`.

## Pull request process

1. For anything larger than a small fix, open an issue first so we can agree on the approach.
2. Create a branch from `main` (e.g. `fix/issue-count`, `feat/export-csv`).
3. Keep the pull request focused on one change, and fill in the template.
4. Update the docs in the same pull request: `docs/api.md` for API changes, `docs/metrics.md` for
   any new or changed number, and the README for user-visible features.
5. A maintainer reviews it. Expect questions: they are about the code, not about you.

## Finding something to work on

Look for issues labelled `good first issue` or `help wanted`. Questions about whether a number is
right are welcome too: use the "A number looks wrong" issue form. Explaining a metric better is a
real contribution.

## Labels

| Label              | Meaning                                                     |
| ------------------ | ----------------------------------------------------------- |
| `bug`              | Something does not work as documented                       |
| `enhancement`      | A new feature or an improvement                             |
| `metric`           | Questions or changes about how a number is calculated       |
| `documentation`    | Docs only                                                   |
| `backend`          | Spring Boot backend                                         |
| `frontend`         | React frontend                                              |
| `github-api`       | GitHub API usage, pagination or rate limits                 |
| `security`         | Security hardening (report vulnerabilities privately)       |
| `dependencies`     | Dependency updates (used by Dependabot)                     |
| `good first issue` | Small and well described; a good first contribution         |
| `help wanted`      | The maintainer would welcome a contribution                 |
| `needs triage`     | New and not yet reviewed                                    |
| `question`         | Further information is requested                            |
| `wontfix`          | Out of scope, e.g. anything that scores or ranks developers |

Maintainers create these labels with [`scripts/create-labels.sh`](scripts/create-labels.sh).
