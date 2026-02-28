package io.github.decarb.rankingtable.input

import cats.ApplicativeThrow
import cats.syntax.all.*
import io.github.decarb.rankingtable.domain.{GameResult, Score, TeamName}

trait InputParser[F[_]: cats.Applicative]:
  def parseLine(line: String): F[GameResult]
  def parseLines(lines: List[String]): F[List[GameResult]] =
    lines.traverse(parseLine)

object InputParser:
  final case class ParseError(message: String) extends RuntimeException(message)

  def make[F[_]: ApplicativeThrow]: InputParser[F] = new Live[F]

  final private class Live[F[_]: ApplicativeThrow] extends InputParser[F]:

    def parseLine(line: String): F[GameResult] =
      parseLineE(line).liftTo[F]

    private def parseLineE(line: String): Either[Throwable, GameResult] =
      line.trim.split(", ", 2).toList match
        case homeStr :: awayStr :: Nil =>
          for
            home <- parseTeamScore(homeStr)
            (homeName, homeScore) = home
            away <- parseTeamScore(awayStr)
            (awayName, awayScore) = away
          yield GameResult(homeName, homeScore, awayName, awayScore)
        case _ =>
          Left(ParseError(s"Expected 'Team score, Team score' but got: '$line'"))

    private def parseTeamScore(s: String): Either[Throwable, (TeamName, Score)] =
      val lastSpace = s.lastIndexOf(' ')
      if lastSpace <= 0 then
        Left(ParseError(s"Expected 'TeamName score' but got: '$s'"))
      else
        val name     = s.substring(0, lastSpace).trim
        val scoreStr = s.substring(lastSpace + 1)
        if name.isEmpty then
          Left(ParseError(s"Expected 'TeamName score' but got: '$s'"))
        else
          scoreStr.toIntOption.filter(_ >= 0) match
            case Some(n) => Right((TeamName(name), Score(n)))
            case None    => Left(ParseError(s"Invalid score '$scoreStr' in: '$s'"))
