# Interview preparation

A study guide for talking about GitPulse in internship and junior-developer interviews. It covers
what to say, the questions you're likely to get, and real stories from building it.

**Before any interview: make sure you can explain every part yourself.** Open each file mentioned
here, run the tests, change something and watch a test fail. Interviewers ask "why?" two or three
levels deep; memorised answers don't survive that, and understanding does. If you used AI tools
or tutorials while building the project, be ready to say so plainly and to explain what _you_
decided, checked and learned. Honesty about how you work is valued, and the follow-up questions
will show whether you understand the code anyway.

## The pitch

**30 seconds:**

> GitPulse analyses public GitHub repositories and profiles: commit activity, pull requests,
> issues, contributors, languages and file churn, shown in an interactive dashboard. It's a Spring
> Boot backend and a React/TypeScript frontend. The interesting part is working within GitHub's
> API limits: anonymous users get only 60 requests an hour, so it's built around caching, capped
> pagination and being honest when data is incomplete. It also deliberately doesn't score
> developers: it describes activity and documents every formula.

**2 minutes:** add the architecture (next section), one hard problem (the truncation story
below), and one thing you would do next (see [What I'd do next](#what-id-do-next)).

## Architecture in one breath

React frontend → Spring Boot REST API → GitHub REST API. The backend is layered: controllers
validate input; services resolve the time window and fetch data through a cache; plain-Java
analyzers compute statistics; one `GitHubClient` class handles HTTP, pagination and rate-limit
errors. No database: everything can be re-derived from GitHub, so the only state is a 10-minute
in-memory cache (Caffeine) of raw GitHub data.

Draw it as: `Browser → Controller → Service → (Cache → GitHubClient → GitHub) + Analyzer`.
Details: [architecture.md](architecture.md).

## Likely questions, with answers to build on

### Design

**Why no database?**
All data comes from GitHub and can be fetched again, so a database would add setup, migrations and
stored personal data without adding information. The trade-off: the cache is lost on restart and
isn't shared between instances. If I needed several instances, I would add Redis as a shared
cache before adding a database.

**Why cache raw GitHub data instead of the final results?**
GitHub requests are the scarce resource; running an analyzer takes microseconds. Caching raw data
means different views of the same data (another filter, "exclude bots", the activity summary)
cost no GitHub requests, and changing a formula never needs cache invalidation.

**Why REST and not GitHub's GraphQL API?**
GraphQL requires a token, and I wanted anyone to be able to try GitPulse anonymously. REST also
counts one request as one unit, which is easier to budget. With a mandatory token, GraphQL would
cut round trips; that's a reasonable future change.

**Why are the analyzers plain Java with no Spring?**
They contain every formula, so they're where bugs matter most. As pure functions they're easy to
unit-test with no mocks, network or application context. The service layer is thin glue around
them.

**How do you handle GitHub's rate limits?**
Several layers: cache first; cap pagination (e.g. 1,000 commits per window); fetch expensive data
only on demand (file activity costs one request per commit, so the user starts it and sees the
cost first); limit concurrency to 4 parallel requests; the UI requests the overview first so a
typo costs one request instead of seven. When GitHub refuses anyway, the backend returns `429`
with `Retry-After` and a reset time, and the UI shows when to try again. When only the separate
Search API limit is hit, those counts show "Unavailable" and everything else still works.

**How does pagination work, and what's the `per_page=1` trick?**
GitHub returns a `Link` header with `rel="next"` and `rel="last"` URLs. To follow pages, I use
`next`. To _count_ items without downloading them, I request one item per page: then the page
number in the `last` link is the total count. One request instead of thousands.

**How do you stop the token leaking?**
It's only read from the environment or a git-ignored `.env`; only the backend uses it; the config
class masks it in `toString()`; errors never include internals. One subtle case: pagination URLs
come from GitHub's response headers, so the client only follows them if they point at the GitHub
API host. Otherwise a manipulated header could make it send the token to another server.

**Why do the export features run in the browser?**
The page already has every response. A server export endpoint would spend GitHub quota again or
duplicate the API. In the browser it's free and matches the screen exactly. For CSV I added
protection against CSV injection: a commit message starting with `=` could run as a formula in
Excel, so formula-like text gets a leading apostrophe.

**You added AI. How do you know it doesn't make things up?**
It can only rephrase numbers GitPulse already calculated. The model receives a fact sheet (ids
and values, no names or free text) and must return JSON where each sentence lists the fact ids it
uses. Before anything is shown, a verifier checks that every number in a sentence equals one of
its cited facts and that there's no evaluative wording. Failing sentences are dropped, and if too
few survive, nothing is shown. The honest limit: it can't prove a sentence uses a correct number
with the right _meaning_, so each sentence shows its sources and the card says it isn't a metric.
Cost is controlled with POST-only requests, a cache and an hourly cap.

**Why not let the AI analyse the commits itself?**
Commit messages are written by strangers, so sending them would let anyone put instructions in
front of the model (prompt injection). The value-to-risk ratio of "summarise verified numbers" was
much better, and a deterministic parser does commit categorisation without AI anyway.

### Java and Spring

- **`@Cacheable` pitfall:** it works through a proxy, so a call from inside the same class skips
  the cache. That's why cached methods live in a separate data service.
- **Virtual threads (Java 21):** used for fetching commit details in parallel. They're cheap to
  create and cheap while blocked on I/O; a semaphore still caps concurrency, because the limit
  that matters is GitHub's, not the JVM's.
- **Error handling:** a `@RestControllerAdvice` maps exceptions to RFC 9457 problem details with a
  stable `code` field, so the frontend can branch on codes rather than messages.
- **Records** for immutable data (DTOs, analysis inputs and results).
- **Configuration:** `@ConfigurationProperties` records with validation, so page caps and
  timeouts are typed and documented in `application.yml`.

### Frontend

- **State in the URL** (`?repo=…&range=90d`): links are shareable and the back button works.
- **`useAsync` hook:** aborts superseded requests with `AbortController`, keeps old data dimmed
  while new data loads, and clears it when the repository changes so one repository's numbers are
  never shown under another's name.
- **Lazy loading:** the dashboard and the chart library are split out, so the landing page stays
  small.
- **Accessibility:** every chart has a table view; values are printed, not only encoded in colour.

### Testing

- Analyzers: unit tests for every formula and edge case (empty data, ties, time-zone boundaries).
- GitHub client: tests against a mock server (`MockRestServiceServer`) for parsing, pagination and
  error mapping. **No test calls the real GitHub API**, so tests are fast, deterministic and cost
  no quota.
- Frontend: React Testing Library, tested through roles and labels like a user would, with a fake
  backend.
- CI runs formatting checks, all tests and a production build on every push, with the backend on
  JDK 21 and 25.
- Know the current numbers: run `./mvnw verify` and `npm test` before the interview.

## Stories (use the STAR format)

These actually happened while building GitPulse. Pick two or three and practise telling them in
about two minutes each: **S**ituation, **T**ask, **A**ction, **R**esult.

### 1. The bug that only showed up on a huge repository

- **Situation:** commit analysis caps at 1,000 commits. For busy repositories, the window is
  shortened to the period the sample fully covers, so missing weeks aren't reported as zero.
- **Task:** checking against a real, very large repository (`torvalds/linux`), the covered period
  looked wrong.
- **Action:** I found I was using the commit's _author_ date to find where coverage ended. GitHub
  orders the list by _commit_ date, and in the Linux kernel patches are often authored weeks
  before they're committed. I switched to the commit date and added a regression test with that
  exact pattern.
- **Result / lesson:** tests with tidy data passed; real data exposed the assumption. Verify
  against real-world data, and know which field an API sorts by.

### 2. The closed-issues count that was always zero

- **Situation:** all-time counts use the `per_page=1` / `rel="last"` trick.
- **Task:** closed issues showed 0 for repositories with thousands of closed issues.
- **Action:** I checked GitHub's actual response headers. The issues endpoint uses cursor
  pagination, which has no `last` link, so the code fell back to "one page" and the count was
  wrong (the UI showed 0). I made the counting method throw when there's a `next` link but no `last`
  (refuse to guess), and switched that count to the Search API, which reports totals.
- **Result / lesson:** a silent wrong number is worse than an error. Make assumptions fail loudly.

### 3. A duplicate history entry that only appeared in development

- **Situation:** dashboard filters are stored in the URL with `history.pushState`.
- **Task:** in development, the back button sometimes needed two presses.
- **Action:** I traced it to calling `pushState` inside a React state updater. React's Strict Mode
  runs updaters twice in development to expose side effects, so there were two history entries.
  I moved the side effect out of the updater and added a test that renders under `<StrictMode>`.
- **Result / lesson:** state updaters must be pure; Strict Mode exists to catch exactly this.

### 4. A dependency upgrade that broke parsing

- **Situation:** the project uses Jackson 3 (with Spring Boot 4).
- **Task:** parsing failed for repositories whose API response omitted a field.
- **Action:** I found that Jackson 3 rejects a missing value for a primitive `boolean` field by
  default, where older versions silently used `false`. I switched to a boxed `Boolean` with an
  explicit accessor for the default, and later gave list responses their own smaller model
  instead of reusing the single-repository one.
- **Result / lesson:** read the migration notes of major versions; model optional fields as
  optional.

### 5. A bug only a real browser could find

- **Situation:** the AI summary is the only `POST` endpoint; all its tests passed.
- **Task:** in the live check, clicking the button returned a bare `403` with no error body.
- **Action:** a `curl` with an `Origin` header reproduced it: Spring's CORS filter. I had assumed
  the Vite dev proxy makes requests same-origin, but it forwards the browser's `Origin` header,
  and the CORS config allowed only `GET`. I allowed `POST`, fixed the misleading code comment, and
  added a test that fails with the old setting and also checks that other origins are still
  refused.
- **Result / lesson:** unit tests only test what you think is true. An end-to-end check in a
  real browser tests the assumptions.

### Other good topics

- Deciding _not_ to build something: no developer scores, because activity counts don't measure
  people and scores invite misuse.
- Deciding _not yet_: ETag conditional requests only save quota when authenticated, so they
  wouldn't help most users yet.
- Moving the project out of a cloud-synced folder, and Windows line-ending issues with the
  formatter: small but real "works on my machine" problems.

## Weaknesses to own

Interviewers respect knowing the limits of your own work:

- **Single instance, in-memory cache:** it doesn't scale out as is. The fix is a shared cache and,
  for a public deployment, each user bringing their own GitHub token via OAuth.
- **Sampling:** at most 1,000 commits and 500 pull requests/issues per window, flagged in the
  response, but a limitation for huge repositories.
- **Default branch only**, and UTC time grouping, because GitHub doesn't keep the author's time
  zone.
- **Public data only:** private contributions are invisible, so a profile is never the full
  picture (and the UI says so).

## What I'd do next

1. OAuth "Sign in with GitHub" so each user uses their own quota (and could see their private
   repositories).
2. ETag conditional requests once most requests are authenticated.
3. A shared cache (Redis) and background refresh for frequently viewed repositories.
4. GraphQL for authenticated requests to cut round trips.

## Demo checklist

- Start both apps before the call (`README` quick start), with a GitHub token in `.env`, so the
  quota doesn't run out mid-demo.
- Warm the cache: open the demo repositories once beforehand (cached for 10 minutes).
- Good demo repositories: a mid-sized active project (e.g. `spring-projects/spring-petclinic`),
  then compare it with another, then open a profile.
- Show: range filter and "exclude bots" (no reload of other cards), a chart's table view, the
  shared link in the address bar, the JSON export, and `docs/metrics.md` for "where does this
  number come from?".
- Have one code walkthrough ready: `GitHubClient` pagination or `CommitAnalyticsService` →
  `CommitAnalyzer`.
