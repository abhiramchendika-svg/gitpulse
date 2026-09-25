# Security policy

## Supported versions

GitPulse is pre-1.0 and has no release branches. Security fixes go to `main` only; please make
sure a problem still happens on the latest `main` before reporting it.

## Reporting a vulnerability

**Please do not open a public issue for a security problem.**

Report it privately through GitHub: open the repository's **Security** tab and choose **Report a
vulnerability** (GitHub's
[private vulnerability reporting](https://docs.github.com/en/code-security/security-advisories/guidance-on-reporting-and-writing-information-about-vulnerabilities/privately-reporting-a-security-vulnerability)).
Please include:

- what the problem is and what an attacker could do with it;
- steps to reproduce (a request, input or proof of concept);
- the commit you tested, and your environment if it matters.

What to expect: an acknowledgement within **7 days**, then updates as the report is investigated.
If the report is confirmed, a fix is released and you are credited in the advisory, unless you
prefer not to be. GitPulse is maintained by one person in their spare time, so please allow
reasonable time before disclosing publicly.

## Scope

In scope, for example:

- exposure of the GitHub token (in responses, logs, errors or the frontend bundle);
- the backend sending the token, or any request, to a host other than the configured GitHub API
  (for example through a crafted pagination `Link` header);
- injection through GitHub data: HTML/script injection in the UI (repository descriptions,
  profile fields, commit messages) or formula injection in exported CSV files;
- input validation bypasses that reach GitHub with attacker-controlled paths;
- stack traces or internal details in API errors;
- vulnerable dependencies that are actually reachable in GitPulse.

Out of scope:

- GitHub's own rate limits, or a single user exhausting the quota of a GitPulse instance they
  run themselves;
- denial of service through request volume against a self-hosted instance (put a reverse proxy
  with rate limiting in front of any public deployment);
- findings that need a compromised machine, a malicious `.env`, or a malicious GitHub API;
- missing security headers on the Vite development server.

## How GitPulse protects secrets and users

For reviewers, a summary of the design (details in
[docs/architecture.md](docs/architecture.md#security)):

- The GitHub token is read from the environment or a git-ignored `.env` file and used only by the
  backend. It is never logged (the configuration's `toString()` masks it), never returned, and
  never sent to the browser.
- Use a **fine-grained, read-only token with public repositories access only**. GitPulse never
  needs write access or private repositories.
- Pagination links from GitHub are followed only when they point at the configured GitHub API
  host, so the token cannot be sent elsewhere.
- Owner, repository and user names are validated against GitHub's naming rules before any request
  is made, and path segments are encoded.
- Errors are RFC 9457 problem details with a stable code; stack traces and exception messages are
  never included.
- CORS allows only the configured origins. Actuator exposes only `/actuator/health`.
- The frontend renders GitHub data as text (React escaping); links from profiles are shown only
  for `http(s)` URLs, and CSV exports neutralise spreadsheet formulas.
- Dependabot keeps dependencies and GitHub Actions up to date.
