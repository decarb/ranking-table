package io.github.decarb.rankingtable.output

import munit.FunSuite
import io.github.decarb.rankingtable.domain.{RankedEntry, TeamName}

class RankedEntryLineRenderSuite extends FunSuite:

  private def entry(rank: Int, name: String, points: Int): RankedEntry =
    RankedEntry(rank, TeamName(name), points)

  private val render = LineRenderable[RankedEntry].renderLine

  test("render entry with plural pts") {
    assertEquals(render(entry(1, "Tarantulas", 6)), "1. Tarantulas, 6 pts")
  }

  test("render entry with singular pt") {
    assertEquals(render(entry(3, "FC Awesome", 1)), "3. FC Awesome, 1 pt")
  }

  test("render entry with zero pts") {
    assertEquals(render(entry(5, "Grouches", 0)), "5. Grouches, 0 pts")
  }
