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
scala-cli run . -- results.txt --output-file rankings.txt
scala-cli run . -- --help
```

## Input format

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

```
LineReader[F]       read lines — file, piped stdin, and interactive TTY  (Sync)
RankingCalculator   calculate  — List[GameResult]  →  List[RankedEntry]  (pure)
ResultWriter[F]     write output — file or stdout                         (Sync)
Main                CommandIOApp — CLI option parsing, wiring, entry point
```

Line parsing and rendering are typeclasses called explicitly in `Main`:

```
LineParseable[A]   String  →  Either[Throwable, A]   (pure)
LineRenderable[A]  A       →  String                  (pure)
```

Source lives under `src/io/github/decarb/rankingtable/` and tests under
`test/io/github/decarb/rankingtable/`, mirroring the package structure.

See [docs/extending.md](docs/extending.md) for how to extend the pipeline to support multiple
game formats.

## Stack

| Library                   | Version | Why                                                                                    |
| ------------------------- | ------- | -------------------------------------------------------------------------------------- |
| **Scala 3.3 LTS**         | 3.3.7   | Long-term support; opaque types and cleaner syntax vs Scala 2                          |
| **Cats Effect**           | 3.6.3   | Principled effect model, `IOApp` integration, structured concurrency                   |
| **Decline**               | 2.6.0   | Purely functional CLI parsing; `CommandIOApp` integrates with Cats Effect directly     |
| **munit + munit-cats-effect** | 1.2.3 / 2.1.0 | Lightweight; `CatsEffectSuite` handles effectful tests with minimal boilerplate |
| **Scala CLI**             | 1.12.3  | Zero-config build tool; single `project.scala` replaces a full sbt/Mill project        |

## Testing

| Suite                               | Style            | What it covers                                               |
| ----------------------------------- | ---------------- | ------------------------------------------------------------ |
| `input/GameResultLineParseSuite`    | `FunSuite`       | Valid and invalid lines; whitespace trimming; error messages |
| `input/LineReaderSuite`             | `CatsEffectSuite` | File reading (lines, empty filtering)                       |
| `calculator/RankingCalculatorSuite` | `FunSuite`       | Point accumulation, tie-breaking, rank numbering             |
| `output/RankedEntryLineRenderSuite` | `FunSuite`       | Singular "pt" vs plural "pts"; rank prefixes                 |
| `output/ResultWriterSuite`          | `CatsEffectSuite` | File writing                                                |
| `IntegrationSuite`                  | `CatsEffectSuite` | CLI arg parsing, pipeline wiring, error exit codes          |

```bash
scala-cli test .
```

## Development

The solution was built across ten sequential stages. Each was independently shippable — no
stage depended on a later one.

| Stage | What changed                 | Key decision                                                                          |
| ----- | ---------------------------- | ------------------------------------------------------------------------------------- |
| 1     | Core implementation          | Tagless final scoped to `InputParser`; pure traits for the rest                       |
| 2     | Package refactor             | Feature packages; pure vs effectful split made explicit                               |
| 3     | Tooling                      | scalafmt, scalafix, CI; `project.scala` excluded from scalafmt                        |
| 4     | `--output-file` option       | Decline `Opts` compose; output routing lives in `Main`                                |
| 5     | Interactive stdin            | TTY via `System.console()`; inline validation as a follow-up                          |
| 6     | Docker + release             | Three-stage Dockerfile; `scratch` isolates JAR from Bloop socket                      |
| 7     | Input boundary validation    | Trim whitespace at parse boundaries; reject negative scores                           |
| 8     | Architecture cleanup         | Extract `RankingIO`; drop `Program`; clean error output to stderr                     |
| 9     | I/O algebras and typeclasses | `LineReader[F]`/`ResultWriter[F]`; `LineParseable[A]`/`LineRenderable[A]`; drop `InputParser` |
| 10    | Narrow algebras              | Strip type params from algebras; routing and dispatch move to `Main`                  |

See [docs/development.md](docs/development.md) for the full rationale behind each stage.

## CI

Three parallel jobs run on every pull request:

| Job           | Command                                              | Notes                               |
| ------------- | ---------------------------------------------------- | ----------------------------------- |
| **Test**      | `scala-cli test .`                                   | All test suites                     |
| **Lint**      | `scala-cli fix --power . && git diff --exit-code`    | No `--check` flag; diff detects changes |
| **Format**    | `scala-cli fmt --check .`                            | Fails with reformatting instructions |

All three must pass before merging. Branches must be up to date with `main` before merge.

See [docs/ci.md](docs/ci.md) for the full workflow configuration and setup steps.

## Distribution

### Docker (no JDK required)

```bash
docker build -t ranking-table .
echo "Lions 3, Snakes 3" | docker run --rm -i ranking-table
docker run --rm -v $(pwd)/results.txt:/app/results.txt ranking-table results.txt
```

### JAR (requires Java 21+)

Download `ranking-table.jar` from the [latest release](../../releases/latest):

```bash
java -jar ranking-table.jar
java -jar ranking-table.jar results.txt --output-file rankings.txt
```

### Releases

Pushing a version tag builds and publishes a Docker image to `ghcr.io/decarb/ranking-table:<tag>`
and attaches the assembly JAR to a GitHub Release:

```bash
git tag v1.0.0 && git push --tags
```

See [docs/release.md](docs/release.md) for the full release process.
