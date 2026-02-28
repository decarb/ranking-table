package ranking.calculator

import cats.effect.IO
import munit.CatsEffectSuite
import ranking.domain.{GameResult, Score, TeamName}

class RankingCalculatorSuite extends CatsEffectSuite:

  val calculator = RankingCalculator.make[IO]

  private def game(home: String, hs: Int, away: String, as: Int): GameResult =
    GameResult(TeamName(home), Score(hs), TeamName(away), Score(as))

  test("win awards 3 points to winner and 0 to loser") {
    calculator.calculate(List(game("Lions", 3, "Snakes", 1))).map { ranked =>
      val lions  = ranked.find(_.team == TeamName("Lions")).get
      val snakes = ranked.find(_.team == TeamName("Snakes")).get
      assertEquals(lions.points, 3)
      assertEquals(snakes.points, 0)
    }
  }

  test("draw awards 1 point to each team") {
    calculator.calculate(List(game("Lions", 1, "Snakes", 1))).map { ranked =>
      val lions  = ranked.find(_.team == TeamName("Lions")).get
      val snakes = ranked.find(_.team == TeamName("Snakes")).get
      assertEquals(lions.points, 1)
      assertEquals(snakes.points, 1)
    }
  }

  test("teams are ranked highest points first") {
    calculator.calculate(List(game("Lions", 3, "Snakes", 0))).map { ranked =>
      assertEquals(ranked.head.team, TeamName("Lions"))
      assertEquals(ranked.last.team, TeamName("Snakes"))
    }
  }

  test("teams with equal points share the same rank") {
    val results = List(
      game("FC Awesome", 1, "Snakes", 1),
      game("Lions", 4, "Grouches", 0)
    )
    calculator.calculate(results).map { ranked =>
      val fcAwesome = ranked.find(_.team == TeamName("FC Awesome")).get
      val snakes    = ranked.find(_.team == TeamName("Snakes")).get
      assertEquals(fcAwesome.rank, snakes.rank)
    }
  }

  test("rank after a tie skips correctly") {
    val results = List(
      game("FC Awesome", 1, "Snakes", 1),
      game("Lions", 4, "Grouches", 0)
    )
    calculator.calculate(results).map { ranked =>
      val grouches = ranked.find(_.team == TeamName("Grouches")).get
      // Two teams tied at 3rd → Grouches must be 4th (not 3rd)
      // Actually: Lions(3pts)=1st, FC Awesome(1pt)=2nd, Snakes(1pt)=2nd, Grouches(0pts)=4th
      assertEquals(grouches.rank, 4)
    }
  }

  test("tied teams appear in alphabetical order") {
    val results = List(
      game("FC Awesome", 1, "Snakes", 1)
    )
    calculator.calculate(results).map { ranked =>
      // Both have 1pt — FC Awesome comes before Snakes alphabetically
      assertEquals(ranked(0).team, TeamName("FC Awesome"))
      assertEquals(ranked(1).team, TeamName("Snakes"))
    }
  }

  test("accumulates points across multiple games for the same team") {
    val results = List(
      game("Tarantulas", 1, "FC Awesome", 0), // Tarantulas: 3pts
      game("Tarantulas", 3, "Snakes", 1)      // Tarantulas: +3pts = 6pts
    )
    calculator.calculate(results).map { ranked =>
      val tarantulas = ranked.find(_.team == TeamName("Tarantulas")).get
      assertEquals(tarantulas.points, 6)
    }
  }

  test("full sample data produces correct standings") {
    val results = List(
      game("Lions", 3, "Snakes", 3),
      game("Tarantulas", 1, "FC Awesome", 0),
      game("Lions", 1, "FC Awesome", 1),
      game("Tarantulas", 3, "Snakes", 1),
      game("Lions", 4, "Grouches", 0)
    )
    calculator.calculate(results).map { ranked =>
      val byTeam = ranked.map(r => r.team -> r).toMap

      assertEquals(byTeam(TeamName("Tarantulas")).rank, 1)
      assertEquals(byTeam(TeamName("Tarantulas")).points, 6)

      assertEquals(byTeam(TeamName("Lions")).rank, 2)
      assertEquals(byTeam(TeamName("Lions")).points, 5)

      assertEquals(byTeam(TeamName("FC Awesome")).rank, 3)
      assertEquals(byTeam(TeamName("Snakes")).rank, 3)

      assertEquals(byTeam(TeamName("Grouches")).rank, 5)
      assertEquals(byTeam(TeamName("Grouches")).points, 0)
    }
  }
