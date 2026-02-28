# Development History

This document traces the six stages of development, explaining the decisions made at each step.
Each stage was independently shippable — no stage depended on a later one.

---

## Stage 1 — Core implementation

**Commits:** `8171568`, `15ec5f4`, `0b7c741`, `6bc2e21`, `570c6d9`

The first working version established the three-stage pipeline (`InputParser` →
`RankingCalculator` → `OutputFormatter`), composed by `Program` and wired to `IO` in `Main`.

### Tagless final, scoped to where it matters

`InputParser` can fail — a malformed line must raise an error, not be silently discarded. It is
expressed as a tagless-final algebra over `F[_]: ApplicativeThrow`, which makes the failure mode
explicit in the type and keeps the implementation testable against any effect type without
depending on `IO` directly.

`RankingCalculator` and `OutputFormatter` have no effects. Applying tagless final to them would
add `F[_]` parameters that serve no purpose. They are plain traits with a single `Live`
implementation, making their purity obvious from the signature alone.

> **Why `Functor` on `Program`?** `Program` only maps the parser result through two pure
> functions — it does not need to sequence effects or handle errors. Requiring `Monad` or `IO`
> would overstate what the component actually does. `Functor` is the honest minimum, and it keeps
> `Program` testable with any `Functor` instance rather than requiring a full effect runtime.

### Opaque types for domain newtypes

`TeamName` and `Score` are opaque types over `String` and `Int`. They have zero runtime overhead
— no boxing, no allocation — but prevent type confusion at call sites. The compiler rejects a
`Score` where a `TeamName` is expected, enforced entirely at compile time with no runtime cost.

Smart constructors (`TeamName.apply`, `Score.apply`) on each companion object are the only
creation path, keeping validation centralised.

### Domain split into separate files

The domain types (`TeamName`, `Score`, `GameResult`, `RankedEntry`) were split into separate
files from the outset — one responsibility per file, each independently navigable and reusable
without pulling in unrelated types.

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
> every operation. Feature packages keep related code together — the parser algebra, its live
> implementation, and its tests all live in `input/`. Adding a new feature means adding a new
> package, not modifying an existing one.

> **Why repackage to `io.github.decarb`?** The Java package naming convention uses a reversed
> domain name to guarantee global uniqueness. `io.github.decarb.rankingtable` is unambiguous and
> production-appropriate, which aligns with the "production ready" requirement in the test brief.

---

## Stage 3 — Tooling

**Commits:** `2a6bfaa`, `7267dfa`, `eb5948d`, `6517686`, `3d16101`, `30f74a7`

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

**Commits:** `2efe53c`, `e185adc`

An optional `--output-file` / `-o` flag was added to write ranking output to a file instead of
stdout.

`Main` was extended with a second `Opts` value:

```scala
val outputFileOpt: Opts[Option[Path]] =
  Opts.option[Path]("output-file", short = "o", help = "Write output to file").orNone
```

The two options are combined with `(inputFileOpt, outputFileOpt).mapN`, keeping the CLI contract
composable. `Program` and both pure stages were untouched — the output routing decision lives
entirely in `Main`.

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

The initial prompt loop (`27ddaa0`) collected all lines first, then parsed them in bulk. A
follow-up commit (`0dc55f1`) added per-line validation: `InputParser` is threaded into the loop
and each line is validated immediately on entry. An invalid line prints an error and re-prompts;
the user corrects it without starting over.

> **Why a separate commit for validation?** The prompt loop and the validation logic are
> independent concerns. Shipping the loop first confirmed TTY detection worked correctly before
> adding the parser dependency. The follow-up was a clean, focused change that was easy to review
> and easy to revert if needed.

The test suite was restructured at the same time: `IntegrationSuite` (which had been testing
`Program` directly) was renamed `ProgramSuite`, and a new `IntegrationSuite` was added to test
CLI wiring through `Main`.

---

## Stage 6 — Docker and release

**Commits:** `4b91075`, `6fbb143`, `0382e45`, `8e3706b`, `deda0d5`

### Dockerfile

The Dockerfile uses three stages:

| Stage       | Base                          | Purpose                                                |
| ----------- | ----------------------------- | ------------------------------------------------------ |
| `builder`   | `virtuslab/scala-cli`         | Compiles source and produces the assembly JAR          |
| `jar-export` | `scratch`                    | Holds only the JAR for clean CI extraction             |
| *(default)* | `eclipse-temurin:21-jre-alpine` | Minimal runtime image for `docker build` / `docker run` |

> **Why the `scratch` stage?** The `builder` stage leaves a Bloop Unix domain socket on disk.
> BuildKit's local exporter cannot handle socket files and fails when trying to copy the build
> output. The `jar-export` stage copies only the JAR into an empty (`scratch`) image, giving the
> exporter a clean, portable artifact. `docker build -t ranking-table .` always targets the
> default stage — local usage is unaffected by the intermediate stage.

> **Why `eclipse-temurin:21-jre-alpine`?** It is the smallest official JRE image matching the
> JVM version used in CI. Alpine-based images are significantly smaller than Debian counterparts
> and have a minimal attack surface.

### Release workflow

The release workflow triggers on `v*` tags and runs three steps in sequence:

1. Builds and pushes a Docker image to `ghcr.io`, tagged with the version
2. Extracts the JAR via the `jar-export` stage using BuildKit's local exporter — this reuses the
   layer cache from step 1, so no recompilation occurs
3. Creates a GitHub Release with the JAR attached and auto-generated release notes

> **Why tag-triggered releases rather than releasing on every merge?** Merging to `main` and
> releasing are separate decisions. A patch merge may not warrant a release; several changes may
> be batched into one. Manual tagging keeps release control explicit and prevents release noise.

See [docs/release.md](release.md) for the full release process.
