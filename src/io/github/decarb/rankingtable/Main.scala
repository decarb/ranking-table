package io.github.decarb.rankingtable

import cats.effect.{ExitCode, IO}
import cats.effect.std.Console
import cats.syntax.all.*
import com.monovore.decline.*
import com.monovore.decline.effect.*
import java.nio.file.Path
import io.github.decarb.rankingtable.calculator.RankingCalculator
import io.github.decarb.rankingtable.domain.{GameResult, RankedEntry}
import io.github.decarb.rankingtable.input.LineReader
import io.github.decarb.rankingtable.output.ResultWriter

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
      val reader     = LineReader.make[IO, GameResult](maybeInput)
      val writer     = ResultWriter.make[IO, RankedEntry](maybeOutput)

      (for
        results <- reader.read
        _       <- writer.write(calculator.calculate(results))
      yield ExitCode.Success).handleErrorWith { e =>
        Console[IO].errorln(s"Error: ${e.getMessage}").as(ExitCode.Error)
      }
    }
