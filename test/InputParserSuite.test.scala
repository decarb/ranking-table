package ranking

import cats.effect.IO
import munit.CatsEffectSuite
import ranking.domain.{Score, TeamName}
import ranking.interpreter.LiveInputParser

class InputParserSuite extends CatsEffectSuite:

  val parser = LiveInputParser.make[IO]

  test("parse a win/loss result") {
    parser.parseLine("Lions 3, Snakes 1").map { r =>
      assertEquals(r.homeTeam, TeamName("Lions"))
      assertEquals(r.homeScore, Score(3))
      assertEquals(r.awayTeam, TeamName("Snakes"))
      assertEquals(r.awayScore, Score(1))
    }
  }

  test("parse a draw") {
    parser.parseLine("Lions 3, Snakes 3").map { r =>
      assertEquals(r.homeScore, Score(3))
      assertEquals(r.awayScore, Score(3))
    }
  }

  test("parse multi-word team names") {
    parser.parseLine("Tarantulas 1, FC Awesome 0").map { r =>
      assertEquals(r.homeTeam, TeamName("Tarantulas"))
      assertEquals(r.awayTeam, TeamName("FC Awesome"))
    }
  }

  test("parse a zero-score result") {
    parser.parseLine("Lions 4, Grouches 0").map { r =>
      assertEquals(r.awayScore, Score(0))
    }
  }

  test("parse multiple lines in order") {
    val lines = List(
      "Lions 3, Snakes 3",
      "Tarantulas 1, FC Awesome 0"
    )
    parser.parseLines(lines).map { results =>
      assertEquals(results.length, 2)
      assertEquals(results(0).homeTeam, TeamName("Lions"))
      assertEquals(results(1).homeTeam, TeamName("Tarantulas"))
    }
  }

  test("fail on missing comma separator") {
    parser.parseLine("Lions 3 Snakes 3").attempt.map { result =>
      assert(result.isLeft)
    }
  }

  test("fail on non-numeric score") {
    parser.parseLine("Lions three, Snakes 3").attempt.map { result =>
      assert(result.isLeft)
    }
  }
