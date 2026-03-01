package io.github.decarb.rankingtable

import cats.effect.IO
import munit.CatsEffectSuite
import java.nio.file.Files
import io.github.decarb.rankingtable.input.InputParser

class RankingIOSuite extends CatsEffectSuite:

  private val parser = InputParser.make[IO]

  test("readLines reads lines from file") {
    for
      file  <- IO.blocking(Files.createTempFile("input", ".txt"))
      _     <- IO.blocking(Files.writeString(file, "Lions 3, Snakes 3\nTarantulas 1, FC Awesome 0"))
      lines <- RankingIO.readLines(Some(file), parser)
    yield assertEquals(lines, List("Lions 3, Snakes 3", "Tarantulas 1, FC Awesome 0"))
  }

  test("readLines filters empty lines from file") {
    for
      file <- IO.blocking(Files.createTempFile("input", ".txt"))
      _ <- IO.blocking(Files.writeString(file, "Lions 3, Snakes 3\n\nTarantulas 1, FC Awesome 0\n"))
      lines <- RankingIO.readLines(Some(file), parser)
    yield assertEquals(lines, List("Lions 3, Snakes 3", "Tarantulas 1, FC Awesome 0"))
  }

  test("readLines returns empty list for file containing only blank lines") {
    for
      file  <- IO.blocking(Files.createTempFile("input", ".txt"))
      _     <- IO.blocking(Files.writeString(file, "\n\n\n"))
      lines <- RankingIO.readLines(Some(file), parser)
    yield assertEquals(lines, List.empty)
  }

  test("writeOutput writes lines to file") {
    val lines = List("1. Tarantulas, 6 pts", "2. Lions, 5 pts")
    for
      file    <- IO.blocking(Files.createTempFile("output", ".txt"))
      _       <- RankingIO.writeOutput(lines, Some(file))
      written <- IO.blocking(Files.readString(file))
    yield assertEquals(written.linesIterator.toList.filter(_.nonEmpty), lines)
  }

  test("writeOutput with empty list writes nothing to file") {
    for
      file    <- IO.blocking(Files.createTempFile("output", ".txt"))
      _       <- RankingIO.writeOutput(List.empty, Some(file))
      written <- IO.blocking(Files.readString(file))
    yield assertEquals(written, "")
  }
