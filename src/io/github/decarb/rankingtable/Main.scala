package io.github.decarb.rankingtable

import cats.effect.{ExitCode, IO}
import cats.effect.std.Console
import cats.syntax.all.*
import com.monovore.decline.*
import com.monovore.decline.effect.*
import java.nio.file.Path
import io.github.decarb.rankingtable.calculator.RankingCalculator
import io.github.decarb.rankingtable.input.InputParser
import io.github.decarb.rankingtable.output.OutputFormatter

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
      val parser     = InputParser.make[IO]
      val calculator = RankingCalculator.make
      val formatter  = OutputFormatter.make

      (for
        lines   <- RankingIO.readLines(maybeInput, parser)
        results <- parser.parseLines(lines)
        _ <- RankingIO.writeOutput(formatter.format(calculator.calculate(results)), maybeOutput)
      yield ExitCode.Success).handleErrorWith { e =>
        Console[IO].errorln(s"Error: ${e.getMessage}").as(ExitCode.Error)
      }
    }
