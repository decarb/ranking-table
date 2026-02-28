# CLAUDE.md

## Project

League ranking table CLI — a backend coding test solution written in Scala 3 using Scala CLI.

## Commands

```bash
# Run (stdin)
scala-cli run .

# Run (file)
scala-cli run . -- results.txt

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

Tagless final pattern. Each feature package contains a trait and a `private Live` implementation nested in its companion object:

```
model/types/package.scala     TeamName, Score (opaque types)
model/GameResult.scala        GameResult
model/RankedEntry.scala       RankedEntry

input/InputParser[F]          parse lines → GameResult
calculator/RankingCalculator  GameResult  → RankedEntry
output/OutputFormatter[F]     RankedEntry → String

Program[F]                    composes the three algebras (requires FlatMap)
Main                          IOApp wiring + decline CLI options
```

Constraints are kept minimal: interpreters only require `Applicative` or `ApplicativeThrow`; `Program` only requires `FlatMap`.

## Key conventions

- `opaque type` for domain newtypes (`TeamName`, `Score`)
- Case classes marked `final`
- `make[F[_]: ...]` smart constructors on companion objects
- No unused imports; `cats.syntax.all.*` covers traverse/liftTo/etc.
