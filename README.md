# ranking-table

A command-line application that calculates the ranking table for a football league. Given a list
of match results, it computes and prints team standings sorted by points, with ties broken
alphabetically.

This is a solution to a [backend coding test](docs/BE%20Coding%20Test%20-%20Candidate-2.pdf). The
implementation goes beyond the minimum requirements: the architecture is structured around
functional effect types with constraints scoped to where effects are real, input is validated
incrementally in interactive mode, and the project ships with a full CI pipeline and a
Docker-based release workflow.

## Quick start

```bash
echo "Lions 3, Snakes 3
Tarantulas 1, FC Awesome 0
Lions 1, FC Awesome 1
Tarantulas 3, Snakes 1
Lions 4, Grouches 0" | scala-cli run .
```

Expected output:

```
1. Tarantulas, 6 pts
2. Lions, 5 pts
3. FC Awesome, 1 pt
3. Snakes, 1 pt
5. Grouches, 0 pts
```

**Requirements:** [Scala CLI](https://scala-cli.virtuslab.org/install) and Java 21+. No other
setup is needed — Scala CLI downloads all dependencies on first run.

**Prefer Docker?** No Scala CLI or JVM required — build the image once and pipe input directly:

```bash
docker build -t ranking-table .
echo "Lions 3, Snakes 3
Tarantulas 1, FC Awesome 0
Lions 1, FC Awesome 1
Tarantulas 3, Snakes 1
Lions 4, Grouches 0" | docker run --rm -i ranking-table
```

## Usage

Four input modes are supported. All write to stdout unless `--output-file` is given.

### Interactive

```bash
scala-cli run .
```

When stdin is a terminal, the application prompts for one result per line. An empty line finishes
input and prints the ranking. Invalid lines are rejected immediately with an error message and the
prompt repeats — no need to start over.

```
Enter game results (one per line, empty line to finish):
> Lions 3, Snakes 3
> Tarantulas 1, FC Awesome 0
> bad input here
  Error: could not parse line. Try again.
> Tarantulas 3, Snakes 1
>
1. Tarantulas, 6 pts
2. Lions, 1 pt
2. Snakes, 1 pt
4. FC Awesome, 0 pts
```

### Piped input

```bash
echo "Lions 3, Snakes 3" | scala-cli run .
cat results.txt | scala-cli run .
```

### File input

```bash
scala-cli run . -- results.txt
```

### File input with output file

```bash
scala-cli run . -- results.txt --output-file rankings.txt
```

### Help

```bash
scala-cli run . -- --help
```

## Input format

Each line contains one match result:

```
<Home Team> <Home Score>, <Away Team> <Away Score>
```

Team names may contain spaces. The score is always the last whitespace-delimited token on each
side, so `FC Awesome 0` is parsed as team `FC Awesome`, score `0`. Scores are non-negative
integers. Lines that do not match this format are rejected with a parse error.

Team names are matched case-sensitively — `Lions` and `lions` are treated as separate teams.
Input must use consistent casing for the same team across all lines.

## Scoring rules

| Result | Points    |
| ------ | --------- |
| Win    | 3         |
| Draw   | 1 each    |
| Loss   | 0         |

Teams with equal points share the same rank number and are listed alphabetically. Point totals
are formatted as `1 pt` (singular) and `0 pts` / `2 pts` (plural).

## Architecture

The processing pipeline has three stages:

```
LineReader[F]       read lines — file, piped stdin, and interactive TTY  (Sync)
RankingCalculator   calculate  — List[GameResult]  →  List[RankedEntry]  (pure)
ResultWriter[F]     write output — file or stdout                         (Sync)
Main                CommandIOApp — CLI option parsing, wiring, entry point
```

Line parsing and rendering are governed by typeclasses:

```
LineParseable[A]   String  →  Either[Throwable, A]   (pure)
LineRenderable[A]  A       →  String                  (pure)
```

Source lives under `src/io/github/decarb/rankingtable/` and tests under
`test/io/github/decarb/rankingtable/`, mirroring the package structure.

### Tagless final, scoped to where effects are real

`LineReader` and `ResultWriter` perform real I/O — file access, TTY detection, stdout — and use
`F[_]: Sync`, the minimal constraint for blocking and suspending side effects.

`RankingCalculator` is a pure function — no effects, no error handling, no IO. Expressing it as
a tagless-final algebra would add `F[_]` parameters that serve no purpose. It is a plain trait
with a single `Live` implementation.

Parsing and rendering are expressed as typeclasses (`LineParseable[A]`, `LineRenderable[A]`)
rather than effectful algebras because neither operation requires `F[_]`. `LineReader` resolves
`LineParseable[A]` implicitly and lifts parse errors into `F` at the I/O boundary. Adding a new
data type requires only a new `given` instance — no algebra changes.

`Main` composes all stages inline in its `for` comprehension. There is no intermediate
orchestrator — the pipeline is short enough to read directly, and each stage is already
independently tested.

### Opaque types for domain newtypes

`TeamName` and `Score` are opaque types over `String` and `Int`. They carry zero runtime overhead
— no boxing, no wrapper allocation — but prevent accidental mix-ups at call sites: a `Score`
cannot be passed where a `TeamName` is expected. Smart constructors on each companion object are
the only creation path.

## Stack

| Library                   | Version | Why                                                                                    |
| ------------------------- | ------- | -------------------------------------------------------------------------------------- |
| **Scala 3.3 LTS**         | 3.3.7   | Long-term support; opaque types and cleaner syntax vs Scala 2                          |
| **Cats Effect**           | 3.6.3   | Principled effect model, `IOApp` integration, structured concurrency                   |
| **Decline**               | 2.6.0   | Purely functional CLI parsing; `CommandIOApp` integrates with Cats Effect directly     |
| **munit + munit-cats-effect** | 1.2.3 / 2.1.0 | Lightweight; `CatsEffectSuite` handles effectful tests with minimal boilerplate |
| **Scala CLI**             | 1.12.3  | Zero-config build tool; single `project.scala` replaces a full sbt/Mill project        |

## Testing

Tests mirror the source package structure:

| Suite                        | Style            | What it covers                                          |
| ---------------------------- | ---------------- | ------------------------------------------------------- |
| `input/LineParseableSuite`   | `FunSuite`        | Valid and invalid lines; whitespace trimming; error messages |
| `input/LineReaderSuite`      | `CatsEffectSuite` | File reading (lines, empty filtering)                  |
| `calculator/RankingCalculatorSuite` | `FunSuite`  | Point accumulation, tie-breaking, rank numbering      |
| `output/LineRenderableSuite` | `FunSuite`        | Singular "pt" vs plural "pts"; rank prefixes           |
| `output/ResultWriterSuite`   | `CatsEffectSuite` | File writing                                           |
| `IntegrationSuite`           | `CatsEffectSuite` | CLI arg parsing, pipeline wiring, error exit codes     |

`LineReaderSuite` and `ResultWriterSuite` use `CatsEffectSuite` because they exercise real I/O.
`LineParseableSuite`, `LineRenderableSuite`, and `RankingCalculatorSuite` use `FunSuite` — there
is no reason to bring in an effect type for pure functions.

```bash
scala-cli test .
```

## Development

The solution was built across ten sequential stages. Each was independently shippable — no
stage depended on a later one.

| Stage | What changed                 | Key decision                                                       |
| ----- | ---------------------------- | ------------------------------------------------------------------ |
| 1     | Core implementation          | Tagless final scoped to `InputParser`; pure traits for the rest    |
| 2     | Package refactor             | Feature packages; pure vs effectful split made explicit            |
| 3     | Tooling                      | scalafmt, scalafix, CI; `project.scala` excluded from scalafmt     |
| 4     | `--output-file` option       | Decline `Opts` compose; output routing lives in `Main`             |
| 5     | Interactive stdin            | TTY via `System.console()`; inline validation as a follow-up       |
| 6     | Docker + release             | Three-stage Dockerfile; `scratch` isolates JAR from Bloop socket   |
| 7     | Input boundary validation    | Trim whitespace at parse boundaries; reject negative scores        |
| 8     | Architecture cleanup         | Extract `RankingIO`; drop `Program`; clean error output to stderr  |
| 9     | I/O algebras                 | Split `RankingIO` into `LineReader[F]` and `ResultWriter[F]`       |
| 10    | Typeclasses                  | `LineParseable[A]`, `LineRenderable[A]`; drop `InputParser` and `OutputFormatter` |

See [docs/development.md](docs/development.md) for the full rationale behind each stage.

## CI

Three parallel jobs run on every pull request:

| Job           | Command                                              | Notes                               |
| ------------- | ---------------------------------------------------- | ----------------------------------- |
| **Test**      | `scala-cli test .`                                   | All test suites                     |
| **Lint**      | `scala-cli fix --power . && git diff --exit-code`    | No `--check` flag; diff detects changes |
| **Format**    | `scala-cli fmt --check .`                            | Fails with reformatting instructions |

All three must pass before merging. Branches must be up to date with `main` before merge,
preventing a stale-base CI pass from landing on a changed `main`.

See [docs/ci.md](docs/ci.md) for the full workflow configuration and setup steps.

## Distribution

### Docker (no JDK required)

```bash
# Build locally
docker build -t ranking-table .

# Pipe input
echo "Lions 3, Snakes 3" | docker run --rm -i ranking-table

# File input
docker run --rm -v $(pwd)/results.txt:/app/results.txt ranking-table results.txt

# File input with output file
docker run --rm \
  -v $(pwd)/results.txt:/app/results.txt \
  -v $(pwd)/rankings.txt:/app/rankings.txt \
  ranking-table results.txt --output-file rankings.txt
```

### JAR (requires Java 21+)

Download `ranking-table.jar` from the [latest release](../../releases/latest):

```bash
java -jar ranking-table.jar
java -jar ranking-table.jar results.txt
java -jar ranking-table.jar results.txt --output-file rankings.txt
```

### Releases

Pushing a version tag triggers the release workflow: a Docker image is pushed to `ghcr.io` and
the assembly JAR is attached to a GitHub Release.

```bash
git tag v1.0.0
git push --tags
```

Published images: `ghcr.io/decarb/ranking-table:<tag>`.

See [docs/release.md](docs/release.md) for the full release process, including how to test a
release locally before pushing a tag.
