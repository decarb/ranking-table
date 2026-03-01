# Extending to multiple game types

The pipeline is already generic at the I/O layer — `LineReader[F]` and `ResultWriter[F]` work
with raw strings, and `LineParseable[A]` / `LineRenderable[A]` are the extension points. What
remains hardcoded is `Main`, which names `GameResult` and `RankedEntry` directly, and
`RankingCalculator`, which is typed to those two domain types.

## CLI design: subcommands via Decline

The natural fit for multiple game types in a Decline-based CLI is subcommands, one per format:

```
scala-cli run . -- football results.txt
scala-cli run . -- basketball results.txt --output-file standings.txt
```

`CommandIOApp` already wraps a single `Command`. Adding subcommands means composing with
`Opts.subcommand` and combining with `orElse`:

```scala
def main: Opts[IO[ExitCode]] = footballCmd orElse basketballCmd
```

Each subcommand returns `Opts[IO[ExitCode]]` and resolves its own type-level witnesses at
compile time — no runtime string dispatch, no `Map[String, ?]`.

## What each new format requires

**1. Domain types**

```scala
final case class BasketballResult(...)
final case class BasketballStanding(...)
```

**2. Typeclass instances**

```scala
given LineParseable[BasketballResult] with
  def parseLine(line: String): Either[Throwable, BasketballResult] = ...

given LineRenderable[BasketballStanding] with
  def renderLine(entry: BasketballStanding): String = ...
```

**3. A calculator**

`RankingCalculator` needs type parameters to be reusable:

```scala
trait RankingCalculator[In, Out]:
  def calculate(results: List[In]): List[Out]
```

Each format provides its own instance via `RankingCalculator.make[BasketballResult, BasketballStanding]`.

**4. A subcommand in Main**

A shared `pipeline` function holds the wiring — the subcommand supplies the calculator and
lets the compiler resolve `LineParseable[In]` and `LineRenderable[Out]` from the `given` instances:

```scala
def pipeline[In: LineParseable, Out: LineRenderable](
  calculator: RankingCalculator[In, Out],
  maybeInput: Option[Path],
  maybeOutput: Option[Path]
): IO[ExitCode] = ...
```

```scala
private val basketballCmd: Opts[IO[ExitCode]] =
  Opts.subcommand("basketball", "Basketball standings") {
    (inputFileOpt, outputFileOpt).mapN { (maybeInput, maybeOutput) =>
      pipeline(RankingCalculator.make[BasketballResult, BasketballStanding], maybeInput, maybeOutput)
    }
  }
```

## Interactive validation

`LineReader.interactive` already accepts `String => Either[Throwable, ?]` rather than a
concrete type. Inside `pipeline`, the call becomes:

```scala
LineReader.interactive[IO](console, LineParseable[In].parseLine)
```

No change to `LineReader` is needed.
