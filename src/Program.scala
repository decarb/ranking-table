package ranking

import cats.FlatMap
import cats.syntax.all.*
import ranking.algebra.{InputParser, OutputFormatter, RankingCalculator}

/** Composes the three algebras into a single pipeline.
  *
  * The tagless final style keeps this class decoupled from any concrete effect type — the same
  * program wires up with IO in production and with a test double in unit tests.
  */
final class Program[F[_]: FlatMap](
  parser: InputParser[F],
  calculator: RankingCalculator[F],
  formatter: OutputFormatter[F]
):
  def run(lines: List[String]): F[List[String]] =
    for
      results <- parser.parseLines(lines)
      ranked  <- calculator.calculate(results)
      output  <- formatter.format(ranked)
    yield output

object Program:
  def make[F[_]: FlatMap](
    parser: InputParser[F],
    calculator: RankingCalculator[F],
    formatter: OutputFormatter[F]
  ): Program[F] = new Program(parser, calculator, formatter)
