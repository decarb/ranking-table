package ranking.domain

opaque type TeamName = String
object TeamName:
  def apply(value: String): TeamName        = value
  extension (t: TeamName) def value: String = t

opaque type Score = Int
object Score:
  def apply(value: Int): Score        = value
  extension (s: Score) def value: Int = s

final case class GameResult(
  homeTeam: TeamName,
  homeScore: Score,
  awayTeam: TeamName,
  awayScore: Score
)

final case class RankedEntry(rank: Int, team: TeamName, points: Int)
