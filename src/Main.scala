package ranking

import cats.effect.{ExitCode, IO}
import cats.syntax.all.*
import com.monovore.decline.*
import com.monovore.decline.effect.*
import java.nio.file.Path
import ranking.interpreter.{LiveInputParser, LiveOutputFormatter, LiveRankingCalculator}

object Main extends CommandIOApp(
      name = "ranking-table",
      header = "Calculate a league ranking table from game results"
    ):
  private val inputFileOpt: Opts[Option[Path]] =
    Opts.argument[Path]("input-file").orNone

  def main: Opts[IO[ExitCode]] =
    inputFileOpt.map { maybeFile =>
      val program = Program.make[IO](
        LiveInputParser.make[IO],
        LiveRankingCalculator.make[IO],
        LiveOutputFormatter.make[IO]
      )

      for
        lines  <- readLines(maybeFile)
        output <- program.run(lines)
        _      <- output.traverse_(IO.println)
      yield ExitCode.Success
    }

  private def readLines(maybeFile: Option[Path]): IO[List[String]] =
    maybeFile match
      case Some(path) =>
        IO.blocking {
          val src = scala.io.Source.fromFile(path.toFile)
          try src.getLines().toList.filter(_.nonEmpty)
          finally src.close()
        }
      case None =>
        IO.blocking(scala.io.Source.stdin.getLines().toList.filter(_.nonEmpty))
