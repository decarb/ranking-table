package io.github.decarb.rankingtable

import cats.Functor
import cats.syntax.all.*
import io.github.decarb.rankingtable.calculator.RankingCalculator
import io.github.decarb.rankingtable.input.InputParser
import io.github.decarb.rankingtable.output.OutputFormatter

final class Program[F[_]: Functor](
  parser: InputParser[F],
  calculator: RankingCalculator,
  formatter: OutputFormatter
):
  def run(lines: List[String]): F[List[String]] =
    parser.parseLines(lines).map(calculator.calculate).map(formatter.format)

object Program:
  def make[F[_]: Functor](
    parser: InputParser[F],
    calculator: RankingCalculator,
    formatter: OutputFormatter
  ): Program[F] = new Program(parser, calculator, formatter)
