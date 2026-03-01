package io.github.decarb.rankingtable

import cats.effect.{ExitCode, IO}
import cats.effect.std.Console
import cats.syntax.all.*
import com.monovore.decline.*
import com.monovore.decline.effect.*
import java.nio.file.Path
import io.github.decarb.rankingtable.calculator.RankingCalculator
import io.github.decarb.rankingtable.domain.{GameResult, RankedEntry}
import io.github.decarb.rankingtable.input.{LineParseable, LineReader}
import io.github.decarb.rankingtable.output.{LineRenderable, ResultWriter}

object Main extends CommandIOApp(
  name = "ranking-table",
  header = "Calculate a league ranking table from game results"
):

  private val inputFileOpt: Opts[Option[Path]] =
    Opts.argument[Path]("input-file").orNone

  private val outputFileOpt: Opts[Option[Path]] =
    Opts.option[Path](
      "output-file",
      short = "o",
      help = "Write output to file instead of stdout"
    ).orNone

  def main: Opts[IO[ExitCode]] =
    (inputFileOpt, outputFileOpt).mapN { (maybeInput, maybeOutput) =>
      val calculator = RankingCalculator.make

      val reader: IO[List[String]] = maybeInput match
        case Some(path) => LineReader.fromFile[IO](path).read
        case None       =>
          IO.blocking(System.console()).flatMap {
            case null    => LineReader.fromStdin[IO].read
            case console => LineReader.interactive[IO](console).read
          }

      val writer: List[String] => IO[Unit] = maybeOutput match
        case Some(path) => ResultWriter.toFile[IO](path).write
        case None       => ResultWriter.toStdout[IO].write

      (for
        raw     <- reader
        results <- raw.traverse(LineParseable[GameResult].parseLine(_).liftTo[IO])
        _       <- writer(calculator.calculate(results).map(LineRenderable[RankedEntry].renderLine))
      yield ExitCode.Success).handleErrorWith { e =>
        Console[IO].errorln(s"Error: ${e.getMessage}").as(ExitCode.Error)
      }
    }
