package ranking.algebra

import ranking.domain.{GameResult, RankedEntry}

trait RankingCalculator[F[_]]:
  def calculate(results: List[GameResult]): F[List[RankedEntry]]
