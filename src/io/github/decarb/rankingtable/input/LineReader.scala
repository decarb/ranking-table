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

  def interactive[F[_]: Sync](console: java.io.Console): LineReader[F] =
    new Live(
      Sync[F].delay(println("Enter game results (one per line, empty line to finish):")) *>
        readLoop(console, List.empty)
    )

  final private class Live[F[_]](effect: F[List[String]]) extends LineReader[F]:
    def read: F[List[String]] = effect

  private def readLoop[F[_]: Sync](console: java.io.Console, acc: List[String]): F[List[String]] =
    Sync[F].blocking(console.readLine("> ")).flatMap {
      case null | "" => Sync[F].pure(acc.reverse)
      case line      => readLoop(console, line :: acc)
    }
