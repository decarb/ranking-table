package io.github.decarb.rankingtable.output

import cats.effect.kernel.Sync
import cats.syntax.all.*
import java.nio.file.Path

trait ResultWriter[F[_]]:
  def write(lines: List[String]): F[Unit]

object ResultWriter:

  def toFile[F[_]: Sync](path: Path): ResultWriter[F] =
    new Live(lines =>
      Sync[F].blocking {
        val pw = new java.io.PrintWriter(path.toFile)
        try lines.foreach(pw.println)
        finally pw.close()
      }
    )

  def toStdout[F[_]: Sync]: ResultWriter[F] =
    new Live(lines => lines.traverse_(line => Sync[F].delay(println(line))))

  final private class Live[F[_]](writeEffect: List[String] => F[Unit]) extends ResultWriter[F]:
    def write(lines: List[String]): F[Unit] = writeEffect(lines)
