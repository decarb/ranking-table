package ranking.domain

final case class GameResult(
  homeTeam: TeamName,
  homeScore: Score,
  awayTeam: TeamName,
  awayScore: Score
)
