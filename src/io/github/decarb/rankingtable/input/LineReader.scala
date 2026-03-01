package io.github.decarb.rankingtable.input

import cats.effect.kernel.Sync
import cats.syntax.all.*
import java.nio.file.Path

trait LineReader[F[_]]:
  def read: F[List[String]]

object LineReader:

  def fromFile[F[_]: Sync](path: Path): LineReader[F] =
    new Live(Sync[F].blocking {
      val src = scala.io.Source.fromFile(path.toFile)
      try src.getLines().toList.filter(_.nonEmpty)
      finally src.close()
    })

  def fromStdin[F[_]: Sync]: LineReader[F] =
    new Live(Sync[F].blocking(scala.io.Source.stdin.getLines().toList.filter(_.nonEmpty)))

  def interactive[F[_]: Sync](
    console: java.io.Console,
    validate: String => Either[Throwable, ?]
  ): LineReader[F] =
    new Live(
      Sync[F].delay(println("Enter game results (one per line, empty line to finish):")) *>
        readLoop(console, validate, List.empty)
    )

  final private class Live[F[_]](effect: F[List[String]]) extends LineReader[F]:
    def read: F[List[String]] = effect

  private def readLoop[F[_]: Sync](
    console: java.io.Console,
    validate: String => Either[Throwable, ?],
    acc: List[String]
  ): F[List[String]] =
    Sync[F].blocking(console.readLine("> ")).flatMap {
      case null | "" => Sync[F].pure(acc.reverse)
      case line      =>
        validate(line) match
          case Right(_) => readLoop(console, validate, line :: acc)
          case Left(_)  =>
            Sync[F].delay(println(s"  Error: could not parse line. Try again.")) *>
              readLoop(console, validate, acc)
    }
