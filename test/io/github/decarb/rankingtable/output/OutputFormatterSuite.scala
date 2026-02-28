package io.github.decarb.rankingtable.output

import munit.FunSuite
import io.github.decarb.rankingtable.domain.{RankedEntry, TeamName}

class OutputFormatterSuite extends FunSuite:

  val formatter = OutputFormatter.make

  private def entry(rank: Int, name: String, points: Int): RankedEntry =
    RankedEntry(rank, TeamName(name), points)

  test("format entry with plural pts") {
    assertEquals(formatter.format(List(entry(1, "Tarantulas", 6))).head, "1. Tarantulas, 6 pts")
  }

  test("format entry with singular pt") {
    assertEquals(formatter.format(List(entry(3, "FC Awesome", 1))).head, "3. FC Awesome, 1 pt")
  }

  test("format entry with zero pts") {
    assertEquals(formatter.format(List(entry(5, "Grouches", 0))).head, "5. Grouches, 0 pts")
  }

  test("format multi-word team name") {
    assert(formatter.format(List(entry(3, "FC Awesome", 1))).head.contains("FC Awesome"))
  }

  test("preserves ordering of input entries") {
    val lines = formatter.format(List(
      entry(1, "Tarantulas", 6),
      entry(2, "Lions", 5),
      entry(3, "FC Awesome", 1)
    ))
    assertEquals(lines(0), "1. Tarantulas, 6 pts")
    assertEquals(lines(1), "2. Lions, 5 pts")
    assertEquals(lines(2), "3. FC Awesome, 1 pt")
  }
