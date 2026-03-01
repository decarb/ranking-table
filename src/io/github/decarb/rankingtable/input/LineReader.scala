package io.github.decarb.rankingtable.input

import cats.effect.kernel.Sync
import cats.syntax.all.*
import java.nio.file.Path

trait LineReader[F[_], A]:
  def read: F[A]

object LineReader:

  def make[F[_]: Sync, A: LineParseable](maybeFile: Option[Path]): LineReader[F, List[A]] =
    new Live(maybeFile)

  final private class Live[F[_]: Sync, A: LineParseable](
    maybeFile: Option[Path]
  ) extends LineReader[F, List[A]]:

    def read: F[List[A]] =
      readRawLines.flatMap(_.traverse(LineParseable[A].parseLine(_).liftTo[F]))

    private def readRawLines: F[List[String]] =
      maybeFile match
        case Some(path) => readLinesFromFile(path)
        case None       =>
          Sync[F].blocking(System.console()).flatMap {
            case null    => readLinesFromStdin
            case console => readLinesInteractive(console)
          }

    private def readLinesFromFile(path: Path): F[List[String]] =
      Sync[F].blocking {
        val src = scala.io.Source.fromFile(path.toFile)
        try src.getLines().toList.filter(_.nonEmpty)
        finally src.close()
      }

    private def readLinesFromStdin: F[List[String]] =
      Sync[F].blocking(scala.io.Source.stdin.getLines().toList.filter(_.nonEmpty))

    private def readLinesInteractive(console: java.io.Console): F[List[String]] =
      Sync[F].delay(println("Enter game results (one per line, empty line to finish):")) *>
        readLoop(console, acc = List.empty)

    private def readLoop(console: java.io.Console, acc: List[String]): F[List[String]] =
      Sync[F].blocking(console.readLine("> ")).flatMap {
        case null | "" => Sync[F].pure(acc.reverse)
        case line      =>
          LineParseable[A].parseLine(line) match
            case Right(_) => readLoop(console, line :: acc)
            case Left(e)  =>
              Sync[F].delay(println(s"  Error: ${e.getMessage}. Try again.")) *>
                readLoop(console, acc)
      }
