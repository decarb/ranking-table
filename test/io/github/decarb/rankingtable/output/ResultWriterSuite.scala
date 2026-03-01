package io.github.decarb.rankingtable.output

import cats.effect.IO
import munit.CatsEffectSuite
import java.nio.file.Files

class ResultWriterSuite extends CatsEffectSuite:

  given LineRenderable[String] with
    def renderLine(s: String): String = s

  test("write writes lines to file") {
    val lines = List("1. Tarantulas, 6 pts", "2. Lions, 5 pts")
    for
      file <- IO.blocking(Files.createTempFile("output", ".txt"))
      writer = ResultWriter.make[IO, String](Some(file))
      _       <- writer.write(lines)
      written <- IO.blocking(Files.readString(file))
    yield assertEquals(written.linesIterator.toList.filter(_.nonEmpty), lines)
  }

  test("write with empty list writes nothing to file") {
    for
      file <- IO.blocking(Files.createTempFile("output", ".txt"))
      writer = ResultWriter.make[IO, String](Some(file))
      _       <- writer.write(List.empty)
      written <- IO.blocking(Files.readString(file))
    yield assertEquals(written, "")
  }
