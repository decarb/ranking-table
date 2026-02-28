package ranking.output

import cats.Applicative
import cats.syntax.all.*
import ranking.domain.RankedEntry

trait OutputFormatter[F[_]]:
  def format(entries: List[RankedEntry]): F[List[String]]

object OutputFormatter:

  def make[F[_]: Applicative]: OutputFormatter[F] = new Live[F]

  private final class Live[F[_]: Applicative] extends OutputFormatter[F]:

    def format(entries: List[RankedEntry]): F[List[String]] =
      entries.map(formatEntry).pure[F]

    private def formatEntry(entry: RankedEntry): String =
      val unit = if entry.points == 1 then "pt" else "pts"
      s"${entry.rank}. ${entry.team.value}, ${entry.points} $unit"
