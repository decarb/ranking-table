package io.github.decarb.rankingtable

import cats.effect.IO
import cats.syntax.all.*
import java.nio.file.Path
import io.github.decarb.rankingtable.input.InputParser

object RankingIO:

  def readLines(maybeFile: Option[Path], parser: InputParser[IO]): IO[List[String]] =
    maybeFile match
      case Some(path) => readLinesFromFile(path)
      case None       =>
        IO.blocking(System.console()).flatMap {
          case null    => readLinesFromStdin
          case console => readLinesInteractive(console, parser)
        }

  def writeOutput(lines: List[String], maybeFile: Option[Path]): IO[Unit] =
    maybeFile match
      case Some(path) =>
        IO.blocking {
          val pw = new java.io.PrintWriter(path.toFile)
          try lines.foreach(pw.println)
          finally pw.close()
        }
      case None =>
        lines.traverse_(IO.println)

  private def readLinesFromFile(path: Path): IO[List[String]] =
    IO.blocking {
      val src = scala.io.Source.fromFile(path.toFile)
      try src.getLines().toList.filter(_.nonEmpty)
      finally src.close()
    }

  private def readLinesFromStdin: IO[List[String]] =
    IO.blocking(scala.io.Source.stdin.getLines().toList.filter(_.nonEmpty))

  private def readLinesInteractive(
    console: java.io.Console,
    parser: InputParser[IO]
  ): IO[List[String]] =
    IO.println("Enter game results (one per line, empty line to finish):") *>
      readLoop(console, parser, acc = List.empty)

  private def readLoop(
    console: java.io.Console,
    parser: InputParser[IO],
    acc: List[String]
  ): IO[List[String]] =
    IO.blocking(console.readLine("> ")).flatMap {
      case null | "" => IO.pure(acc.reverse)
      case line      =>
        parser.parseLine(line).attempt.flatMap {
          case Right(_) => readLoop(console, parser, line :: acc)
          case Left(e)  =>
            IO.println(s"  Error: ${e.getMessage}. Try again.") *>
              readLoop(console, parser, acc)
        }
    }
