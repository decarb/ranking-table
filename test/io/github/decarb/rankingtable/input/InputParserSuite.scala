package io.github.decarb.rankingtable.input

import cats.effect.IO
import munit.CatsEffectSuite
import io.github.decarb.rankingtable.domain.{Score, TeamName}

class InputParserSuite extends CatsEffectSuite:

  val parser = InputParser.make[IO]

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

  test("trim leading and trailing whitespace from line") {
    parser.parseLine("  Lions 3, Snakes 1  ").map { r =>
      assertEquals(r.homeTeam, TeamName("Lions"))
      assertEquals(r.awayTeam, TeamName("Snakes"))
    }
  }

  test("trim whitespace from team names") {
    parser.parseLine("  FC Awesome  3,   Snakes 1").map { r =>
      assertEquals(r.homeTeam, TeamName("FC Awesome"))
    }
  }

  test("fail on negative score") {
    parser.parseLine("Lions -1, Snakes 3").attempt.map { result =>
      assert(result.isLeft)
    }
  }

  test("fail on whitespace-only team name") {
    parser.parseLine("   3, Snakes 1").attempt.map { result =>
      assert(result.isLeft)
    }
  }
