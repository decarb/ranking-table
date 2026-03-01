package io.github.decarb.rankingtable.input

import cats.effect.IO
import munit.CatsEffectSuite
import java.nio.file.Files

class LineReaderSuite extends CatsEffectSuite:

  // Identity parser: returns each line unchanged, used to test I/O routing in isolation
  given LineParseable[String] with
    def parseLine(line: String): Either[Throwable, String] = Right(line)

  private def reader(file: java.nio.file.Path) =
    LineReader.make[IO, String](Some(file))

  test("read reads lines from file") {
    for
      file  <- IO.blocking(Files.createTempFile("input", ".txt"))
      _     <- IO.blocking(Files.writeString(file, "Lions 3, Snakes 3\nTarantulas 1, FC Awesome 0"))
      lines <- reader(file).read
    yield assertEquals(lines, List("Lions 3, Snakes 3", "Tarantulas 1, FC Awesome 0"))
  }

  test("read filters empty lines from file") {
    for
      file <- IO.blocking(Files.createTempFile("input", ".txt"))
      _ <- IO.blocking(Files.writeString(file, "Lions 3, Snakes 3\n\nTarantulas 1, FC Awesome 0\n"))
      lines <- reader(file).read
    yield assertEquals(lines, List("Lions 3, Snakes 3", "Tarantulas 1, FC Awesome 0"))
  }

  test("read returns empty list for file containing only blank lines") {
    for
      file  <- IO.blocking(Files.createTempFile("input", ".txt"))
      _     <- IO.blocking(Files.writeString(file, "\n\n\n"))
      lines <- reader(file).read
    yield assertEquals(lines, List.empty)
  }
