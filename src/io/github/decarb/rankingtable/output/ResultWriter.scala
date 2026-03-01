package io.github.decarb.rankingtable.output

import cats.effect.kernel.Sync
import cats.syntax.all.*
import java.nio.file.Path

trait ResultWriter[F[_], A]:
  def write(results: List[A]): F[Unit]

object ResultWriter:

  def make[F[_]: Sync, A: LineRenderable](maybeFile: Option[Path]): ResultWriter[F, A] =
    new Live(maybeFile)

  final private class Live[F[_]: Sync, A: LineRenderable](
    maybeFile: Option[Path]
  ) extends ResultWriter[F, A]:

    def write(results: List[A]): F[Unit] =
      val lines = results.map(LineRenderable[A].renderLine)
      maybeFile match
        case Some(path) =>
          Sync[F].blocking {
            val pw = new java.io.PrintWriter(path.toFile)
            try lines.foreach(pw.println)
            finally pw.close()
          }
        case None =>
          lines.traverse_(line => Sync[F].delay(println(line)))
