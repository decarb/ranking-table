# CLAUDE.md

## Project

League ranking table CLI — a backend coding test solution written in Scala 3 using Scala CLI.

## Commands

```bash
# Run (stdin)
scala-cli run .

# Run (file)
scala-cli run . -- results.txt

# Run (file, write output to file)
scala-cli run . -- results.txt --output-file rankings.txt

# Tests
scala-cli test .

# Help
scala-cli run . -- --help

# Format
scala-cli fmt .

# Lint / fix (--power required for experimental sub-command)
scala-cli fix --power .
```

## Stack

- **Scala 3.3** (LTS) via Scala CLI
- **Cats Effect 3** — `IOApp` / `IO`
- **Decline** — CLI option parsing (`CommandIOApp`)
- **munit + munit-cats-effect** — test framework

## Architecture

Tagless final only where the effect is real. Each feature package contains a trait and a `private Live` implementation nested in its companion object.

All source lives under `src/io/github/decarb/rankingtable/` and tests under `test/io/github/decarb/rankingtable/`, mirroring the package structure:

```
domain/types.scala            TeamName, Score (opaque types)
domain/GameResult.scala       GameResult
domain/RankedEntry.scala      RankedEntry

input/InputParser[F]          parse lines → F[List[GameResult]]  (ApplicativeThrow — can fail)
calculator/RankingCalculator  GameResult  → List[RankedEntry]    (pure)
output/OutputFormatter        List[RankedEntry] → List[String]   (pure)

Program[F]                    composes the three (requires Functor)
Main                          CommandIOApp wiring + decline CLI options
```

`InputParser` is the only effectful algebra; `RankingCalculator` and `OutputFormatter` are pure traits with no `F[_]`. `Program` only requires `Functor` to `map` the parser result through the two pure steps.

## Workflow

Before committing, always run in this order:

1. `git fetch origin && git rebase origin/main` — stay current with main
2. `scala-cli fix --power .` — lint/fix
3. `scala-cli fmt .` — format
4. `scala-cli test .` — all tests must pass
5. Check that `CLAUDE.md` and `docs/` are consistent with any code changes
6. `git add` all modified files (code, docs, formatted sources)
7. `git status` — confirm everything is staged, nothing unexpected
8. `git commit` — only if all steps above succeed

## Git conventions

Commits use conventional commit format with a bulleted body:

```
<prefix>: <short imperative summary>

- <individual change>
- <individual change>
```

Prefixes: `feat`, `fix`, `refactor`, `test`, `chore`, `docs`

Rules:
- Title is lowercase after the prefix, imperative mood, no trailing period
- One logical change per bullet point
- Omit the body only when the title is entirely self-explanatory
- Do not include `Co-Authored-By` trailers

## Key conventions

- `opaque type` for domain newtypes (`TeamName`, `Score`)
- Case classes marked `final`
- `make[F[_]: ...]` smart constructors on companion objects
- No unused imports; `cats.syntax.all.*` covers traverse/liftTo/etc.
