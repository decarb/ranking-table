package ranking.interpreter

import cats.ApplicativeThrow
import cats.syntax.all.*
import ranking.algebra.InputParser
import ranking.domain.{GameResult, Score, TeamName}

final case class ParseError(message: String) extends RuntimeException(message)

final class LiveInputParser[F[_]: ApplicativeThrow] extends InputParser[F]:

  def parseLine(line: String): F[GameResult] =
    parseLineE(line).liftTo[F]

  private def parseLineE(line: String): Either[Throwable, GameResult] =
    line.split(", ", 2).toList match
      case homeStr :: awayStr :: Nil =>
        for
          home <- parseTeamScore(homeStr)
          away <- parseTeamScore(awayStr)
        yield GameResult(home._1, home._2, away._1, away._2)
      case _ =>
        Left(ParseError(s"Expected 'Team score, Team score' but got: '$line'"))

  private def parseTeamScore(s: String): Either[Throwable, (TeamName, Score)] =
    val lastSpace = s.lastIndexOf(' ')
    if lastSpace <= 0 then
      Left(ParseError(s"Expected 'TeamName score' but got: '$s'"))
    else
      val name     = s.substring(0, lastSpace)
      val scoreStr = s.substring(lastSpace + 1)
      scoreStr.toIntOption match
        case Some(n) => Right((TeamName(name), Score(n)))
        case None    => Left(ParseError(s"Invalid score '$scoreStr' in: '$s'"))

object LiveInputParser:
  def make[F[_]: ApplicativeThrow]: InputParser[F] = new LiveInputParser[F]
