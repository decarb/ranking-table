package io.github.decarb.rankingtable

import cats.effect.{ExitCode, IO}
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
      val program = Program.make[IO](
        InputParser.make[IO],
        RankingCalculator.make,
        OutputFormatter.make
      )

      for
        lines  <- readLines(maybeInput)
        output <- program.run(lines)
        _      <- writeOutput(output, maybeOutput)
      yield ExitCode.Success
    }

  private def writeOutput(lines: List[String], maybeFile: Option[Path]): IO[Unit] =
    maybeFile match
      case Some(path) =>
        IO.blocking {
          val pw = new java.io.PrintWriter(path.toFile)
          try lines.foreach(pw.println)
          finally pw.close()
        }
      case None =>
        lines.traverse_(IO.println)

  private def readLines(maybeFile: Option[Path]): IO[List[String]] =
    maybeFile match
      case Some(path) => readLinesFromFile(path)
      case None       =>
        IO.blocking(System.console()).flatMap {
          case null    => readLinesFromStdin
          case console => readLinesInteractive(console)
        }

  private def readLinesFromFile(path: Path): IO[List[String]] =
    IO.blocking {
      val src = scala.io.Source.fromFile(path.toFile)
      try src.getLines().toList.filter(_.nonEmpty)
      finally src.close()
    }

  private def readLinesFromStdin: IO[List[String]] =
    IO.blocking(scala.io.Source.stdin.getLines().toList.filter(_.nonEmpty))

  private def readLinesInteractive(console: java.io.Console): IO[List[String]] =
    IO.println("Enter game results (one per line, empty line to finish):") *>
      IO.blocking {
        Iterator
          .continually(console.readLine("> "))
          .takeWhile(line => line != null && line.nonEmpty)
          .toList
      }
