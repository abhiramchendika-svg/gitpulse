#!/usr/bin/env bash
# Creates (or updates) the issue labels described in CONTRIBUTING.md, using the GitHub CLI.
#
#   scripts/create-labels.sh              # the repository of the current directory
#   scripts/create-labels.sh owner/repo   # a specific repository
#   DRY_RUN=1 scripts/create-labels.sh    # print the commands without running them
#
# Needs `gh` signed in with permission to manage labels. Safe to run again: --force updates the
# colour and description of labels that already exist instead of failing.
set -euo pipefail

repo_args=()
if [[ $# -gt 0 ]]; then
  repo_args=(--repo "$1")
fi

# name|colour (hex, no #)|description
labels=(
  "bug|d73a4a|Something does not work as documented"
  "enhancement|a2eeef|A new feature or an improvement"
  "metric|5319e7|Questions or changes about how a number is calculated"
  "documentation|0075ca|Docs only"
  "backend|1d76db|Spring Boot backend"
  "frontend|0e8a16|React frontend"
  "github-api|6f42c1|GitHub API usage, pagination or rate limits"
  "security|b60205|Security hardening (report vulnerabilities privately)"
  "dependencies|0366d6|Dependency updates"
  "good first issue|7057ff|Small and well described; a good first contribution"
  "help wanted|008672|The maintainer would welcome a contribution"
  "needs triage|fbca04|New and not yet reviewed"
  "question|d876e3|Further information is requested"
  "wontfix|ffffff|Out of scope, e.g. anything that scores or ranks developers"
)

for entry in "${labels[@]}"; do
  IFS='|' read -r name color description <<<"$entry"
  command=(gh label create "$name" --color "$color" --description "$description" --force
    ${repo_args[@]+"${repo_args[@]}"})
  if [[ -n "${DRY_RUN:-}" ]]; then
    printf '%q ' "${command[@]}"
    printf '\n'
  else
    "${command[@]}"
  fi
done
