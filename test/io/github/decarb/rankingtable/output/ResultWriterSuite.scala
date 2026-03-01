package io.github.decarb.rankingtable.output

import cats.effect.IO
import munit.CatsEffectSuite
import java.nio.file.Files

class ResultWriterSuite extends CatsEffectSuite:

  test("write writes lines to file") {
    val lines = List("1. Tarantulas, 6 pts", "2. Lions, 5 pts")
    for
      file    <- IO.blocking(Files.createTempFile("output", ".txt"))
      _       <- ResultWriter.toFile[IO](file).write(lines)
      written <- IO.blocking(Files.readString(file))
    yield assertEquals(written.linesIterator.toList.filter(_.nonEmpty), lines)
  }

  test("write with empty list writes nothing to file") {
    for
      file    <- IO.blocking(Files.createTempFile("output", ".txt"))
      _       <- ResultWriter.toFile[IO](file).write(List.empty)
      written <- IO.blocking(Files.readString(file))
    yield assertEquals(written, "")
  }

  test("write to path in non-existent directory raises error") {
    val path = java.nio.file.Path.of("/nonexistent/dir/output.txt")
    ResultWriter.toFile[IO](path).write(List("1. Tarantulas, 6 pts")).attempt.map { result =>
      assert(result.isLeft)
    }
  }
