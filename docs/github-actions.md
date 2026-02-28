# GitHub Actions & Branch Strategy

Recommended CI/CD workflows and branch configuration for this project.

## Branch strategy: rebase-only merges

To enforce a linear history with rebase merges (no merge commits, no squash):

1. **Repository settings** (Settings → General → Pull Requests) — disable "Allow merge commits" and "Allow squash merging"; leave only "Allow rebase merging" enabled
2. **Branch protection on `main`** — enable "Require linear history" (blocks any merge commit from being pushed)

This ensures every commit on `main` traces directly to a PR commit, keeping `git log` bisectable and readable.

## Workflow: CI on pull requests

Triggers on every PR targeting `main`. Three jobs — `test`, `lint`, and `format` — run in parallel and all must pass before merging.

```yaml
# .github/workflows/ci.yml
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

**Why `pull_request` and not `pull_request_target`?**
`pull_request` runs in the context of the PR's code with a read-only token and no access to secrets — safe for running untrusted code. `pull_request_target` runs with full repo write access and secrets, making it dangerous to use with any step that checks out PR code. For CI (test, lint, format), always use `pull_request`.

**Why `git diff --exit-code` for lint?** `scala-cli fix` has no `--check` flag. The CI pattern is to run it, then fail if any files were modified — a diff against a clean checkout will be empty if no changes are needed.

**Required status checks:** Once the workflow has run at least once, go to branch protection settings for `main` and add `Test`, `Lint`, and `Format check` as required status checks. Also enable "Require branches to be up to date before merging" so a stale branch cannot pass CI and then merge on top of a broken `main`.

## Workflow: release on push to main

Triggers after a PR is rebased and merged into `main`. Builds a Docker image and pushes it to GitHub Container Registry (`ghcr.io`).

```yaml
# .github/workflows/release.yml
name: Release

on:
  push:
    branches: [main]

jobs:
  release:
    name: Docker image
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0
      - uses: coursier/cache-action@v6
      - uses: VirtusLab/scala-cli-setup@v1
        with:
          jvm: temurin:21
          power: true
      - name: Log in to GitHub Container Registry
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      - name: Build and push Docker image
        run: |
          scala-cli --power package . --docker \
            --docker-image-repository ghcr.io/${{ github.repository }} \
            --docker-image-tag ${{ github.sha }}
          docker push ghcr.io/${{ github.repository }}:${{ github.sha }}
          docker tag ghcr.io/${{ github.repository }}:${{ github.sha }} \
                     ghcr.io/${{ github.repository }}:latest
          docker push ghcr.io/${{ github.repository }}:latest
```

**Why Docker over a fat JAR?** Docker is the better fit for a CLI tool: anyone can run `docker run ghcr.io/org/ranking-table < results.txt` without a JVM installed. A fat JAR (`--assembly`) is simpler to build but requires the caller to have Java. A GraalVM native image (`--native-image`) avoids the JVM dependency without Docker but needs GraalVM in CI and a separate build per platform (Linux, macOS, Windows).

**`GITHUB_TOKEN` permissions:** The `packages: write` permission is required to push to `ghcr.io`. This token is provided automatically by GitHub Actions — no secrets setup needed.

## Setup actions used

| Action | Purpose |
|---|---|
| `actions/checkout@v4` | Checks out the repository; `fetch-depth: 0` ensures full git history |
| `coursier/cache-action@v6` | Caches Coursier and JVM dependency downloads between runs |
| `VirtusLab/scala-cli-setup@v1` | Installs Scala CLI (and optionally a JVM) via Coursier |
| `docker/login-action@v3` | Authenticates to `ghcr.io` using `GITHUB_TOKEN` |

## Complementary branch protection settings

Combined with the workflows above, these branch protection rules on `main` complete the picture:

| Setting | Reason |
|---|---|
| Require status checks to pass (`Test`, `Lint`, `Format check`) | Blocks merges when any CI job fails |
| Require branches to be up to date before merging | Prevents stale-base CI passes |
| Require linear history | Enforces rebase merges; blocks merge commits |
| Do not allow bypassing settings for administrators | Ensures rules apply to everyone |

## References

- [Scala CLI — GitHub Action cookbook](https://scala-cli.virtuslab.org/docs/cookbooks/introduction/gh-action/)
- [VirtusLab/scala-cli-setup](https://github.com/VirtusLab/scala-cli-setup)
- [Scala CLI — package command](https://scala-cli.virtuslab.org/docs/commands/package/)
- [Preventing pwn requests — GitHub Security Lab](https://securitylab.github.com/resources/github-actions-preventing-pwn-requests/)
