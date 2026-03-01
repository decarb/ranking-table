package io.github.decarb.rankingtable.input

import munit.FunSuite
import io.github.decarb.rankingtable.domain.{GameResult, Score, TeamName}

class GameResultLineParseSuite extends FunSuite:

  private val parse = LineParseable[GameResult].parseLine

  private def parsed(line: String): GameResult =
    parse(line).fold(e => fail(e.getMessage), identity)

  test("parse a win/loss result") {
    val r = parsed("Lions 3, Snakes 1")
    assertEquals(r.homeTeam, TeamName("Lions"))
    assertEquals(r.homeScore, Score(3))
    assertEquals(r.awayTeam, TeamName("Snakes"))
    assertEquals(r.awayScore, Score(1))
  }

  test("parse a draw") {
    val r = parsed("Lions 3, Snakes 3")
    assertEquals(r.homeScore, Score(3))
    assertEquals(r.awayScore, Score(3))
  }

  test("parse multi-word team names") {
    val r = parsed("Tarantulas 1, FC Awesome 0")
    assertEquals(r.homeTeam, TeamName("Tarantulas"))
    assertEquals(r.awayTeam, TeamName("FC Awesome"))
  }

  test("parse a zero-score result") {
    val r = parsed("Lions 4, Grouches 0")
    assertEquals(r.awayScore, Score(0))
  }

  test("trim leading and trailing whitespace from line") {
    val r = parsed("  Lions 3, Snakes 1  ")
    assertEquals(r.homeTeam, TeamName("Lions"))
    assertEquals(r.awayTeam, TeamName("Snakes"))
  }

  test("trim whitespace from team names") {
    val r = parsed("  FC Awesome  3,   Snakes 1")
    assertEquals(r.homeTeam, TeamName("FC Awesome"))
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

  test("fail when no space separates team name from score") {
    assert(parse("Lions3, Snakes 1").isLeft)
  }
