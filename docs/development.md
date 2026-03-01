# Development History

This document traces the ten stages of development, explaining the decisions made at each step.
Each stage was independently shippable — no stage depended on a later one.

---

## Stage 1 — Core implementation

**Commits:** `8171568`, `15ec5f4`, `0b7c741`

The first working version established the three-stage pipeline (`InputParser` →
`RankingCalculator` → `OutputFormatter`), composed by `Program` and wired to `IO` in `Main`.

### Tagless final, scoped to where it matters

`InputParser` can fail — a malformed line must raise an error, not be silently discarded. It is
expressed as a tagless-final algebra over `F[_]: ApplicativeThrow`, which makes the failure mode
explicit in the type and keeps the implementation testable without depending on `IO` directly.

`RankingCalculator` and `OutputFormatter` have no effects. Applying tagless final to them would
add `F[_]` parameters that serve no purpose. They are plain traits with a single `Live`
implementation, making their purity obvious from the signature alone.

> **Why `Functor` on `Program`?** `Program` only maps the parser result through two pure
> functions — it does not need to sequence effects or handle errors. `Functor` is the honest
> minimum, and it keeps `Program` testable without requiring a full effect runtime.

### Opaque types for domain newtypes

`TeamName` and `Score` are opaque types over `String` and `Int`. They have zero runtime overhead
but prevent type confusion at call sites — the compiler rejects a `Score` where a `TeamName` is
expected. Smart constructors on each companion object are the only creation path, keeping
validation centralised.

---

## Stage 2 — Package refactor

**Commits:** `570c6d9`, `6bc2e21`, `fc3e9f1`

Source was reorganised from a flat layout into feature-based packages:

```
src/io/github/decarb/rankingtable/
  domain/       types.scala, GameResult.scala, RankedEntry.scala
  input/        InputParser.scala
  calculator/   RankingCalculator.scala
  output/       OutputFormatter.scala
  Program.scala
  Main.scala
```

Tests were moved to mirror this structure exactly.

> **Why feature packages over layer packages?** A layer-based layout (`model/`, `service/`,
> `util/`) forces unrelated features to share directories and creates cross-package imports for
> every operation. Feature packages keep related code together — the parser algebra, its
> implementation, and its tests all live in `input/`. Adding a new feature means adding a new
> package, not touching an existing one.

> **Why repackage to `io.github.decarb`?** The Java convention uses a reversed domain name to
> guarantee global uniqueness. `io.github.decarb.rankingtable` is unambiguous and
> production-appropriate, which aligns with the "production ready" requirement in the test brief.

---

## Stage 3 — Tooling

**Commits:** `2a6bfaa`, `7267dfa`, `eb5948d`, `6517686`

Three tooling additions were made in sequence: scalafmt, scalafix, then CI.

### scalafmt

`.scalafmt.conf` specifies `runner.dialect = scala3` — not `dialect = scala3`, which is a subtly
different (and invalid) key. It also sets `maxColumn = 100`, `align.preset = more`, and
`rewrite.rules = [RedundantBraces, SortModifiers]`.

`project.scala` is excluded from scalafmt via `project.excludePaths`. The built-in rules run by
`scala-cli fix` and scalafmt conflict on the trailing newline in this file — `fix` owns it, so
scalafmt is told to leave it alone.

### scalafix

`.scalafix.conf` enables three rules: `DisableSyntax` (no `finalize`, `isInstanceOf`, `return`),
`RemoveUnused`, and `NoValInForComprehension`. The `-Wunused:all` compiler option in `project.scala`
is required for `RemoveUnused` to detect unused imports and bindings.

`scala-cli fix` requires the `--power` flag as it is currently an experimental sub-command.

### CI pipeline

Three parallel jobs run on every pull request — Test, Lint, Format — all must pass before
merging. See [docs/ci.md](ci.md) for the full configuration.

> **Why `git diff --exit-code` for the lint check?** `scala-cli fix` does not expose a `--check`
> flag. The CI pattern is to run the fixer against a clean checkout and then assert no files were
> modified. A non-empty diff means lint issues were not resolved locally before pushing.

---

## Stage 4 — `--output-file` option

**Commits:** `2efe53c`

An optional `--output-file` / `-o` flag was added to write ranking output to a file instead of
stdout. The two options are combined with `(inputFileOpt, outputFileOpt).mapN`, keeping the CLI
contract composable. `Program` and both pure stages were untouched — the output routing decision
lives entirely in `Main`.

> **Why Decline over manual argument parsing?** Decline models CLI options as values that compose
> naturally with `mapN`, `orNone`, and `withDefault`. It validates inputs before `main` runs,
> generates `--help` output automatically, and integrates with `CommandIOApp` directly. There is
> no custom parsing logic to test or maintain.

---

## Stage 5 — Interactive stdin

**Commits:** `27ddaa0`, `0dc55f1`, `256e96f`

When no file argument is given and stdin is a terminal, the application runs a prompt loop rather
than reading silently from stdin.

### TTY detection

The JVM's `System.console()` returns `null` when stdin is not a terminal (piped or redirected).
This is used as the branch condition:

```scala
IO.blocking(System.console()).flatMap {
  case null => readLinesFromStdin    // piped — existing behaviour
  case _    => readLinesInteractive  // TTY — prompt loop
}
```

This approach requires no external libraries and works correctly in both Docker (`-i` flag) and
native terminal sessions.

### Inline validation

The initial prompt loop collected all lines first, then parsed them in bulk. A follow-up commit
added per-line validation: each line is validated immediately on entry, printing an error and
re-prompting on failure rather than failing at batch-parse time.

`IntegrationSuite` was split from `ProgramSuite` at this point to test CLI wiring through `Main`
separately from the pipeline logic.

---

## Stage 6 — Docker and release

**Commits:** `4b91075`, `0382e45`, `6fbb143`, `deda0d5`

### Dockerfile

The Dockerfile uses three stages:

| Stage        | Base                            | Purpose                                       |
| ------------ | ------------------------------- | --------------------------------------------- |
| `builder`    | `virtuslab/scala-cli`           | Compiles source and produces the assembly JAR |
| `jar-export` | `scratch`                       | Holds only the JAR for clean CI extraction    |
| *(default)*  | `eclipse-temurin:21-jre-alpine` | Minimal runtime image for `docker run`        |

> **Why the `scratch` stage?** The `builder` stage leaves a Bloop Unix domain socket on disk.
> BuildKit's local exporter cannot handle socket files and fails when trying to copy the build
> output. The `jar-export` stage copies only the JAR into an empty image, giving the exporter a
> clean, portable artifact.

> **Why `eclipse-temurin:21-jre-alpine`?** The smallest official JRE image matching the JVM
> version used in CI. Alpine-based images are significantly smaller than Debian counterparts and
> have a minimal attack surface.

### Release workflow

The release workflow triggers on `v*` tags and runs three steps:

1. Builds and pushes a Docker image to `ghcr.io`, tagged with the version
2. Extracts the JAR via the `jar-export` stage — reuses the layer cache from step 1, so no
   recompilation occurs
3. Creates a GitHub Release with the JAR attached and auto-generated release notes

> **Why tag-triggered releases?** Merging to `main` and releasing are separate decisions. A patch
> merge may not warrant a release; several changes may be batched into one. Manual tagging keeps
> release control explicit.

See [docs/release.md](release.md) for the full release process.

---

## Stage 7 — Input boundary validation

**Commits:** `b7cfafb`

Three edge cases in `InputParser.Live` were identified and fixed after the core implementation.

### Whitespace trimming

Leading and trailing whitespace on a line, or extra spaces around a team name, were silently
included in the extracted `TeamName`, causing entries differing only in whitespace to be treated
as separate teams. The fix adds `.trim` at two points: the full line before splitting, and the
extracted name before constructing `TeamName`. Internal whitespace within a name (e.g. `FC
Awesome`) is preserved.

### Negative scores

`scoreStr.toIntOption` accepted any integer. The fix adds `.filter(_ >= 0)`: `None` falls through
to the existing `ParseError` case with no special handling.

### Empty team name after trim

A fragment like `"   3"` passes the `lastSpace <= 0` guard but produces an empty string after
`.trim`. The fix adds an emptiness check after trimming.

### Case sensitivity — documented, not normalised

`Map[TeamName, Int]` in `RankingCalculator` uses case-sensitive equality. Normalising correctly
for all team name formats is non-trivial, and the test spec is silent on casing while the sample
data is internally consistent. The behaviour is documented in `README.md` rather than normalised.

---

## Stage 8 — Architecture cleanup and error output

**Commits:** `8928cfa`

### Removing Program

`Program` composed the three pipeline stages in a single call:

```scala
parser.parseLines(lines).map(calculator.calculate).map(formatter.format)
```

This is not a meaningful abstraction — it adds a file, a class, and a `Functor` constraint
without encapsulating any complexity. `Program` was deleted and the pipeline moved inline to
`Main.main`'s `for` comprehension. `ProgramSuite` was deleted with it; the individual unit suites
already cover each stage and `IntegrationSuite` covers the end-to-end pipeline.

### Extracting RankingIO

Five private methods for reading and writing lived inside `Main`, burying I/O logic next to CLI
wiring. `RankingIO` extracted that boundary into its own file. `Main` was left with only what it
should own: decline option definitions and the pipeline `for` comprehension.

### Clean error output in batch mode

In batch mode, errors propagated unhandled and `CommandIOApp` printed the full JVM stack trace.
The fix adds `.handleErrorWith` after the `for` comprehension:

```scala
(for
  ...
yield ExitCode.Success).handleErrorWith { e =>
  Console[IO].errorln(s"Error: ${e.getMessage}").as(ExitCode.Error)
}
```

---

## Stage 9 — I/O algebras and typeclasses

**Commits:** `36dec7a`

### LineParseable[A] and LineRenderable[A]

Two typeclasses decouple parsing and rendering from the I/O algebras:

```
LineParseable[A]   — parseLine(line: String): Either[Throwable, A]
LineRenderable[A]  — renderLine(a: A): String
```

Each carries a `given` instance in its companion object. Supporting a new data type requires only
a new `given` — no algebra changes.

### Replacing RankingIO with LineReader[F] and ResultWriter[F]

`RankingIO` was a plain `object` with concrete `IO` methods. Stage 8 had argued against tagless
final on the grounds that the methods called JVM APIs directly. That conflates implementation
detail with capability: the *capability* — read lines, write lines — is a genuine I/O effect.
`Sync[F]` is the honest minimum constraint; it does not require `IO` specifically.

`RankingIO` is replaced by two algebras:

```
input/LineReader[F]    — read: F[List[String]]  (Sync)
output/ResultWriter[F] — write(lines): F[Unit]  (Sync)
```

### Removing InputParser and OutputFormatter

With `LineParseable[A]` in place, `InputParser` became a thin wrapper with no behaviour of its
own. `Main` calls `LineParseable[GameResult].parseLine` directly. `OutputFormatter` was absorbed
into `ResultWriter` in the same pass.

### Test alignment

Each typeclass is tested in a pure `FunSuite`:

```
input/GameResultLineParseSuite    FunSuite — parseLine results and all error cases
output/RankedEntryLineRenderSuite FunSuite — renderLine formatting, singular/plural pts
```

`InputParserSuite` is deleted alongside `InputParser`.

---

## Stage 10 — Narrow algebras

**Commits:** `b17fd93`, `f9972f4`, `96c4a42`

### Moving routing and typeclass dispatch to Main

`LineReader[F]` and `ResultWriter[F]` each bundled routing (which source or destination to use)
alongside raw I/O — a decision that belongs in `Main`. Three single-purpose constructors replace
the routing `make`:

```scala
LineReader.fromFile[F[_]: Sync](path: Path): LineReader[F]
LineReader.fromStdin[F[_]: Sync]: LineReader[F]
LineReader.interactive[F[_]: Sync](console: java.io.Console, validate: String => Either[Throwable, ?]): LineReader[F]

ResultWriter.toFile[F[_]: Sync](path: Path): ResultWriter[F]
ResultWriter.toStdout[F[_]: Sync]: ResultWriter[F]
```

`Main` selects the constructor and calls the typeclasses explicitly between I/O steps, making the
full pipeline visible: read raw strings → parse → calculate → render → write.

### Interactive validation

`LineReader.interactive` accepts a `String => Either[Throwable, ?]` validator rather than a
`LineParseable[A]` context bound. `Main` passes `LineParseable[GameResult].parseLine` at the
call site. The loop sees only the `Either` result; the domain type never leaks into the algebra.

### Test coverage

`LineReaderSuite` tests only `fromFile`. `fromStdin` reads directly from the process's stdin and
cannot be redirected cleanly in a test environment. `interactive` requires a `java.io.Console`,
which `System.console()` returns `null` for in any non-TTY context — it is a final class with no
public constructor, so there is no clean way to construct or inject a test instance. Both
constructors are covered at the integration level through `IntegrationSuite`.
