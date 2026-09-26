# Deployment

GitPulse ships as **one Docker image**: the React frontend is built and packaged inside the Spring
Boot jar, so a single service serves the app, the API and the health check from one URL. There is
no CORS setup to get wrong and the frontend needs no configuration.

```
Browser ──HTTPS──▶ host's proxy ──HTTP──▶ container :$PORT
                                           Spring Boot
                                           ├── /            → index.html, /assets/* (built React app)
                                           ├── /api/v1/...  → REST API
                                           └── /actuator/health
```

## Run the image locally

Needs Docker (Docker Desktop on Windows/macOS).

```bash
docker build -t gitpulse .
docker run --rm -p 8080:8080 -e GITHUB_TOKEN=<your token> gitpulse
```

Open <http://localhost:8080>. Without `GITHUB_TOKEN` it works too, limited to 60 GitHub requests
per hour.

The image:

- is built in three stages (Node → Maven → JRE), so only a Java 21 runtime and one jar ship;
- runs as an unprivileged user, never root;
- sizes the Java heap to the container (75% of its memory) for small instances;
- does not run tests. CI runs them, and also builds the image and smoke-tests it on every push.

## Deploy to Render (free tier)

The repository contains a [Render Blueprint](https://render.com/docs/blueprint-spec)
(`render.yaml`).

1. Sign up at [render.com](https://render.com) with your GitHub account.
2. **New → Blueprint**, pick the `gitpulse` repository, and confirm.
3. When asked for `GITHUB_TOKEN`, paste a **fine-grained, read-only token with access to public
   repositories only** (see the README). Render stores it as a secret; it never goes into git.
4. Wait for the first build (a few minutes). Your app is at `https://<service-name>.onrender.com`.

After that, every push to `main` deploys automatically **once CI has passed**
(`autoDeployTrigger: checksPass`).

What `render.yaml` configures, and why:

| Setting                                     | Why                                                                                                                                                                     |
| ------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `runtime: docker`, `plan: free`             | Builds the Dockerfile on Render's free instance type                                                                                                                    |
| `healthCheckPath: /actuator/health`         | Render only switches traffic to a new deploy once it reports healthy                                                                                                    |
| `GITHUB_TOKEN` (`sync: false`)              | Asked for once, stored as a secret                                                                                                                                      |
| `SERVER_FORWARD_HEADERS_STRATEGY=framework` | Render ends HTTPS at its proxy; trusting its `X-Forwarded-*` headers lets the app see the public `https://` URL, so same-site requests are not rejected as cross-origin |
| No `ANTHROPIC_API_KEY`                      | On a public site strangers could run up the bill, so the AI summary stays off                                                                                           |

`PORT` is set by Render (10000) and GitPulse already listens on `$PORT`.

### Free-tier behaviour to expect

- **Sleeps after 15 minutes without traffic;** the next visit takes about a minute while it wakes.
  Open it before a demo.
- **750 free instance hours per month** per workspace: enough for one service running all month.
- **The cache is in memory,** so it is empty after a wake-up or redeploy: the first dashboard after
  that is slower and costs GitHub requests again.

Render's limits change; check [render.com/docs/free](https://render.com/docs/free).

## Other hosts

Any host that runs a Docker image works. Set:

| Variable                          | Value                                                                 |
| --------------------------------- | --------------------------------------------------------------------- |
| `GITHUB_TOKEN`                    | Secret: fine-grained, read-only, public repositories                  |
| `PORT`                            | Usually set by the host; defaults to 8080                             |
| `SERVER_FORWARD_HEADERS_STRATEGY` | `framework` when behind an HTTPS-terminating proxy (almost all hosts) |

Point the host's health check at `/actuator/health`.

## Before making it public

- **One shared GitHub quota.** Every visitor uses your token's 5,000 requests per hour. Fine for a
  portfolio; a busier site needs per-visitor rate limiting (for example at a proxy) or "Sign in
  with GitHub" so each user brings their own quota.
- **Keep the token minimal.** Read-only and public repositories only: the worst case is someone
  using up its hourly quota.
- **Leave `ANTHROPIC_API_KEY` unset** on public deployments.
