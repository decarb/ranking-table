package ranking.calculator

import ranking.domain.{GameResult, RankedEntry, TeamName}

trait RankingCalculator:
  def calculate(results: List[GameResult]): List[RankedEntry]

object RankingCalculator:

  def make: RankingCalculator = new Live

  final private class Live extends RankingCalculator:

    def calculate(results: List[GameResult]): List[RankedEntry] =
      computeStandings(results)

    private def computeStandings(results: List[GameResult]): List[RankedEntry] =
      val pointsMap = results.foldLeft(Map.empty[TeamName, Int]) { (acc, result) =>
        val (homePoints, awayPoints) = pointsFor(result)
        acc
          .updatedWith(result.homeTeam)(pts => Some(pts.getOrElse(0) + homePoints))
          .updatedWith(result.awayTeam)(pts => Some(pts.getOrElse(0) + awayPoints))
      }
      val sorted = pointsMap.toList.sortBy { case (team, pts) => (-pts, team.value) }
      assignRanks(sorted)

    private def pointsFor(result: GameResult): (Int, Int) =
      val h = result.homeScore.value
      val a = result.awayScore.value
      if h > a then (3, 0)
      else if h < a then (0, 3)
      else (1, 1)

    /** Assigns ranks where tied teams share a rank and the next rank skips accordingly (e.g. two
      * teams at 3rd → next rank is 5th).
      *
      * Runs in O(n) by walking the sorted list once, comparing each team's points to the previous
      * entry.
      */
    private def assignRanks(standings: List[(TeamName, Int)]): List[RankedEntry] =
      standings.zipWithIndex
        .foldLeft(List.empty[RankedEntry]) { case (acc, ((team, pts), idx)) =>
          val rank = acc.headOption match
            case Some(prev) if prev.points == pts => prev.rank
            case _                                => idx + 1
          RankedEntry(rank, team, pts) :: acc
        }
        .reverse
