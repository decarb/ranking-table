package io.github.decarb.rankingtable.input

import munit.FunSuite
import io.github.decarb.rankingtable.domain.{GameResult, Score, TeamName}

class LineParseableSuite extends FunSuite:

  private val parse = LineParseable[GameResult].parseLine

  private def assertRight(line: String)(f: GameResult => Unit): Unit =
    parse(line) match
      case Right(r) => f(r)
      case Left(e)  => fail(e.getMessage)

  test("parse a win/loss result") {
    assertRight("Lions 3, Snakes 1") { r =>
      assertEquals(r.homeTeam, TeamName("Lions"))
      assertEquals(r.homeScore, Score(3))
      assertEquals(r.awayTeam, TeamName("Snakes"))
      assertEquals(r.awayScore, Score(1))
    }
  }

  test("parse a draw") {
    assertRight("Lions 3, Snakes 3") { r =>
      assertEquals(r.homeScore, Score(3))
      assertEquals(r.awayScore, Score(3))
    }
  }

  test("parse multi-word team names") {
    assertRight("Tarantulas 1, FC Awesome 0") { r =>
      assertEquals(r.homeTeam, TeamName("Tarantulas"))
      assertEquals(r.awayTeam, TeamName("FC Awesome"))
    }
  }

  test("parse a zero-score result") {
    assertRight("Lions 4, Grouches 0") { r =>
      assertEquals(r.awayScore, Score(0))
    }
  }

  test("trim leading and trailing whitespace from line") {
    assertRight("  Lions 3, Snakes 1  ") { r =>
      assertEquals(r.homeTeam, TeamName("Lions"))
      assertEquals(r.awayTeam, TeamName("Snakes"))
    }
  }

  test("trim whitespace from team names") {
    assertRight("  FC Awesome  3,   Snakes 1") { r =>
      assertEquals(r.homeTeam, TeamName("FC Awesome"))
    }
  }

  test("fail on missing comma separator") {
    assert(parse("Lions 3 Snakes 3").isLeft)
  }

  test("fail on non-numeric score") {
    assert(parse("Lions three, Snakes 3").isLeft)
  }

  test("fail on negative score") {
    assert(parse("Lions -1, Snakes 3").isLeft)
  }

  test("fail on whitespace-only team name") {
    assert(parse("   3, Snakes 1").isLeft)
  }
