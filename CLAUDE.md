# CLAUDE.md

## Project

League ranking table CLI — a backend coding test solution written in Scala 3 using Scala CLI.

## Commands

```bash
# Run (interactive — enter games one at a time, empty line to finish)
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

# Docker build
docker build -t ranking-table .

# Docker run (interactive — prompts for input line by line)
docker run --rm -it ranking-table

# Docker run (pipe — simplest for small inputs)
echo "Lions 3, Snakes 3" | docker run --rm -i ranking-table

# Docker run (file input)
docker run --rm -v $(pwd)/results.txt:/app/results.txt ranking-table results.txt

# Docker run (file input + output file)
docker run --rm \
  -v $(pwd)/results.txt:/app/results.txt \
  -v $(pwd)/rankings.txt:/app/rankings.txt \
  ranking-table results.txt --output-file rankings.txt
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

input/LineParseable[A]        String → Either[Throwable, A]       (typeclass)
input/LineReader[F]           readLines — file, piped stdin, interactive TTY  (Sync)
calculator/RankingCalculator  GameResult  → List[RankedEntry]     (pure)
output/LineRenderable[A]      A → String                          (typeclass)
output/ResultWriter[F]        writeLines — file or stdout         (Sync)

Main                          CommandIOApp wiring + decline CLI options
```

`LineReader` and `ResultWriter` are effectful algebras. `RankingCalculator` is a pure trait with no `F[_]`. Parsing and rendering are typeclasses — `LineParseable[A]` and `LineRenderable[A]` — resolved implicitly by the I/O algebras. `Main` composes all stages inline.

Test suites mirror the source structure:

```
input/LineParseableSuite            FunSuite (pure — parse logic and error cases)
input/LineReaderSuite               CatsEffectSuite (file reading, empty filtering)
calculator/RankingCalculatorSuite   FunSuite (pure)
output/LineRenderableSuite          FunSuite (pure — rendering logic)
output/ResultWriterSuite            CatsEffectSuite (file writing)
IntegrationSuite                    CatsEffectSuite (CLI wiring through Main)
```

## Documentation

- `README.md` — usage, architecture overview, stack, development summary
- `docs/development.md` — full development history and rationale for each design decision
- `docs/ci.md` — CI workflow configuration and branch strategy
- `docs/release.md` — release process, Dockerfile stages, running released artifacts
- `docs/branch-protection.md` — recommended branch protection rules

## Workflow

Before committing, always run in this order:

1. `git fetch origin && git rebase origin/main` — stay current with main
2. `scala-cli fix --power .` — lint/fix
3. `scala-cli fmt .` — format
4. `scala-cli test .` — all tests must pass
5. Check that `README.md`, `CLAUDE.md`, and `docs/` are consistent with any code changes
6. `git add` all modified files (code, docs, formatted sources)
7. `git status` — confirm everything is staged, nothing unexpected
8. `git commit` — only if all steps above succeed

When opening a pull request, use the template in `.github/pull_request_template.md`. The checklist covers only what CI cannot enforce — docs consistency and test coverage.

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
