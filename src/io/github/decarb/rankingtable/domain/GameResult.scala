package io.github.decarb.rankingtable.domain

final case class GameResult(
  homeTeam: TeamName,
  homeScore: Score,
  awayTeam: TeamName,
  awayScore: Score
)
