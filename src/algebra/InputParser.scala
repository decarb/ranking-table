package ranking.algebra

import cats.syntax.all.*
import ranking.domain.GameResult

trait InputParser[F[_]: cats.Applicative]:
  def parseLine(line: String): F[GameResult]
  def parseLines(lines: List[String]): F[List[GameResult]] =
    lines.traverse(parseLine)
