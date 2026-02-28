# Branch Protection

Recommended branch protection rules for the `main` branch. This project is solo-developed — PRs
are used to gate CI and provide a self-review point and audit trail, but no approval step is
needed.

## Recommended rules

| Rule                                      | Reason                                                                              |
| ----------------------------------------- | ----------------------------------------------------------------------------------- |
| **Require a pull request before merging** | Prevents direct pushes to `main`; every change goes through CI and a diff review   |
| **Require status checks to pass**         | Blocks merges when `Test`, `Lint`, or `Format check` fail                          |
| **Require branches to be up to date**     | Prevents a stale-base CI pass from landing on a `main` that has since moved        |
| **Require linear history**                | Enforces rebase merges; keeps `git log` bisectable and readable                    |
| **Do not allow bypassing for admins**     | Ensures CI gates apply even to the repository owner                                |

## Merge strategy

"Allow merge commits" and "Allow squash merging" should both be disabled in repository settings
(Settings → General → Pull Requests), leaving only "Allow rebase merging". Combined with "Require
linear history" in branch protection, this makes rebase the only available merge method.

## What to skip for solo development

| Rule                                       | Reason to skip                              |
| ------------------------------------------ | ------------------------------------------- |
| **Require approvals**                      | No other reviewers                          |
| **Dismiss stale approvals**                | Irrelevant without approvals                |
| **Require conversation resolution**        | Only meaningful when others leave comments  |

## Optional

| Rule                          | Notes                                                                    |
| ----------------------------- | ------------------------------------------------------------------------ |
| **Require signed commits**    | Cryptographic proof of authorship; good for auditability, has setup overhead |
| **Allow deletions: OFF**      | Default — keep off to prevent accidental branch deletion                 |
| **Allow force pushes: OFF**   | Default — keep off to prevent history rewrites on `main`                 |

## Reference

[GitHub Docs — Managing a branch protection rule](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/managing-a-branch-protection-rule)
