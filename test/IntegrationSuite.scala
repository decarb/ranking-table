package ranking

import cats.effect.IO
import munit.CatsEffectSuite
import ranking.calculator.RankingCalculator
import ranking.input.InputParser
import ranking.output.OutputFormatter

class IntegrationSuite extends CatsEffectSuite:

  val program = Program.make[IO](
    InputParser.make[IO],
    RankingCalculator.make,
    OutputFormatter.make
  )

  test("sample data produces the expected ranking table") {
    val input = List(
      "Lions 3, Snakes 3",
      "Tarantulas 1, FC Awesome 0",
      "Lions 1, FC Awesome 1",
      "Tarantulas 3, Snakes 1",
      "Lions 4, Grouches 0"
    )
    val expected = List(
      "1. Tarantulas, 6 pts",
      "2. Lions, 5 pts",
      "3. FC Awesome, 1 pt",
      "3. Snakes, 1 pt",
      "5. Grouches, 0 pts"
    )
    program.run(input).map { output =>
      assertEquals(output, expected)
    }
  }

  test("single game with a winner") {
    val input    = List("Lions 3, Snakes 0")
    val expected = List("1. Lions, 3 pts", "2. Snakes, 0 pts")
    program.run(input).map(output => assertEquals(output, expected))
  }

  test("all draws gives every team 1 pt at rank 1") {
    val input = List("Lions 1, Snakes 1")
    program.run(input).map { output =>
      assert(output.forall(_.contains("1 pt")))
      assert(output.forall(_.startsWith("1.")))
    }
  }

  test("propagates parse errors") {
    program.run(List("not a valid line")).attempt.map { result =>
      assert(result.isLeft)
    }
  }
