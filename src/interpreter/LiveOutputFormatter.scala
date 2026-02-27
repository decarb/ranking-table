package ranking.interpreter

import cats.Applicative
import cats.syntax.all.*
import ranking.algebra.OutputFormatter
import ranking.domain.RankedEntry

final class LiveOutputFormatter[F[_]: Applicative] extends OutputFormatter[F]:

  def format(entries: List[RankedEntry]): F[List[String]] =
    entries.map(formatEntry).pure[F]

  private def formatEntry(entry: RankedEntry): String =
    val unit = if entry.points == 1 then "pt" else "pts"
    s"${entry.rank}. ${entry.team.value}, ${entry.points} $unit"

object LiveOutputFormatter:
  def make[F[_]: Applicative]: OutputFormatter[F] = new LiveOutputFormatter[F]
