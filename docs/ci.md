# CI Pipeline

Three parallel jobs run on every pull request targeting `main`. All must pass before merging.
This document describes the workflow configuration, the rationale behind each job, and the
required setup steps.

## Jobs

| Job        | Command                                           | Notes                                   |
| ---------- | ------------------------------------------------- | --------------------------------------- |
| **Test**   | `scala-cli test .`                                | Runs all test suites                    |
| **Lint**   | `scala-cli fix --power . && git diff --exit-code` | Fails if any file was modified          |
| **Format** | `scala-cli fmt --check .`                         | Fails with reformatting instructions    |

> **Why `git diff --exit-code` for lint?** `scala-cli fix` has no `--check` flag. The CI pattern
> is to run the fixer against a clean checkout and then assert no files were modified. A non-empty
> diff means lint issues were not fixed locally before pushing.

## Workflow

```yaml
name: CI

on:
  pull_request:
    branches: [main]

jobs:
  test:
    name: Test
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0
      - uses: coursier/cache-action@v6
      - uses: VirtusLab/scala-cli-setup@v1
        with:
          jvm: temurin:21
      - name: Run tests
        run: scala-cli test .

  lint:
    name: Lint
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0
      - uses: coursier/cache-action@v6
      - uses: VirtusLab/scala-cli-setup@v1
        with:
          jvm: temurin:21
          power: true
      - name: Check lint
        run: |
          scala-cli fix --power .
          git diff --exit-code || (
            echo "Run 'scala-cli fix --power .' locally to fix lint issues."
            exit 1
          )

  format:
    name: Format check
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0
      - uses: coursier/cache-action@v6
      - uses: VirtusLab/scala-cli-setup@v1
        with:
          jvm: temurin:21
      - name: Check formatting
        run: |
          scala-cli fmt --check . || (
            echo "Run 'scala-cli fmt .' locally to fix formatting."
            exit 1
          )
```

## Setup actions

| Action                         | Purpose                                                                       |
| ------------------------------ | ----------------------------------------------------------------------------- |
| `actions/checkout@v4`          | Checks out the repository. `fetch-depth: 0` provides the full git history.   |
| `coursier/cache-action@v6`     | Caches Coursier artifacts and JVM dependency downloads between runs.          |
| `VirtusLab/scala-cli-setup@v1` | Installs Scala CLI via Coursier. `power: true` is required for the lint job. |

## Required status checks

Once the workflow has run at least once, add `Test`, `Lint`, and `Format check` as required
status checks in branch protection settings for `main`. Also enable "Require branches to be up to
date before merging" — this prevents a PR that passed CI on a stale base from landing on a `main`
that has since moved.

## Branch strategy

Merges to `main` use rebase only — no merge commits, no squash. This keeps git history linear
and bisectable.

To enforce this:

1. **Repository settings** → General → Pull Requests — disable "Allow merge commits" and "Allow
   squash merging"; leave only "Allow rebase merging" enabled.
2. **Branch protection on `main`** — enable "Require linear history" to block any merge commit
   from being pushed directly.

## `pull_request` vs `pull_request_target`

This workflow uses `pull_request`, which runs in the context of the PR branch with a read-only
`GITHUB_TOKEN` and no access to repository secrets. `pull_request_target` runs with full write
access and secrets — it is unsafe to use with any step that checks out PR code, since an
untrusted contributor could access secrets or modify repository state.

## References

- [Scala CLI — GitHub Action cookbook](https://scala-cli.virtuslab.org/docs/cookbooks/introduction/gh-action/)
- [VirtusLab/scala-cli-setup](https://github.com/VirtusLab/scala-cli-setup)
- [Preventing pwn requests — GitHub Security Lab](https://securitylab.github.com/resources/github-actions-preventing-pwn-requests/)
