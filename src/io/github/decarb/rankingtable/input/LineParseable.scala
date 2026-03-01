package io.github.decarb.rankingtable.input

import io.github.decarb.rankingtable.domain.{GameResult, Score, TeamName}

trait LineParseable[A]:
  def parseLine(line: String): Either[Throwable, A]

object LineParseable:
  def apply[A](using lp: LineParseable[A]): LineParseable[A] = lp

  final case class ParseError(message: String) extends RuntimeException(message)

  given LineParseable[GameResult] with

    private def invalidFormat(expected: String, got: String) =
      Left(ParseError(s"Invalid format - expected '$expected' but got: '$got'"))

    def parseLine(line: String): Either[Throwable, GameResult] =
      line.trim.split(", ", 2).toList match
        case homeStr :: awayStr :: Nil =>
          for
            home <- parseTeamScore(homeStr)
            (homeName, homeScore) = home
            away <- parseTeamScore(awayStr)
            (awayName, awayScore) = away
          yield GameResult(homeName, homeScore, awayName, awayScore)
        case _ => invalidFormat("TeamName score, TeamName score", line)

    private def parseTeamScore(s: String): Either[Throwable, (TeamName, Score)] =
      val lastSpace = s.lastIndexOf(' ')
      val name      = if lastSpace > 0 then s.substring(0, lastSpace).trim else ""
      if name.isEmpty then invalidFormat("TeamName score", s)
      else
        val scoreStr = s.substring(lastSpace + 1)
        scoreStr.toIntOption.filter(_ >= 0) match
          case Some(n) => Right((TeamName(name), Score(n)))
          case None    => Left(ParseError(s"Invalid score '$scoreStr' in: '$s'"))
