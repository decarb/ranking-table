package ranking.algebra

import ranking.domain.RankedEntry

trait OutputFormatter[F[_]]:
  def format(entries: List[RankedEntry]): F[List[String]]
