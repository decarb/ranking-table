# Branch Protection Recommendations

Recommended GitHub branch protection rules for the `main` branch.

This project is solo-developed. PRs are still used — they gate CI and provide a self-review point and audit trail — but no approval step is needed. You merge your own PR once CI passes.

## Recommended set

- **Require a pull request before merging** — prevents direct pushes to `main`; ensures every change goes through CI and a self-review of the diff
- **Require status checks to pass before merging** — blocks merges when any CI job fails (`Test`, `Lint`, `Format check`); pair with "require branches to be up to date" so stale branches cannot slip through
- **Require linear history** — blocks merge commits; all PRs must be rebased, keeping `git log` bisectable and readable. Also disable "Allow merge commits" and "Allow squash merging" in repository settings (Settings → General → Pull Requests) so rebase is the only available merge method
- **Do not allow bypassing settings for administrators** — without this, you can push directly to `main` and bypass CI, defeating the purpose

## Not needed for solo development

- **Require approvals** — no other reviewers; skip this entirely
- **Dismiss stale approvals / require code owner review** — irrelevant without approvals
- **Require conversation resolution** — only meaningful when others leave review comments

## Optional / situational

- **Require signed commits** — cryptographic proof of authorship; good for auditability but has setup overhead
- **Allow deletions: OFF** (default) — keep off on `main` to prevent accidental branch deletion
- **Allow force pushes: OFF** (default) — keep off on `main` to prevent history rewrites
- **Lock branch** — only appropriate if `main` should be fully read-only (e.g. an archived project)

## Reference

[GitHub Docs — Managing a branch protection rule](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/managing-a-branch-protection-rule)
