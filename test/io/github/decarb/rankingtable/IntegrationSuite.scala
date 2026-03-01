package io.github.decarb.rankingtable

import cats.effect.{ExitCode, IO}
import com.monovore.decline.Command
import munit.CatsEffectSuite
import java.nio.file.Files

class IntegrationSuite extends CatsEffectSuite:

  // Reconstruct a Command from Main.main for test-side parsing
  private val command: Command[IO[ExitCode]] =
    Command("ranking-table", "test")(Main.main)

  private def run(args: String*): IO[ExitCode] =
    command.parse(args.toList, Map.empty) match
      case Right(io)  => io
      case Left(help) => IO.raiseError(new RuntimeException(help.toString))

  // --- CLI argument parsing ---

  test("no args parses successfully") {
    assert(command.parse(Nil, Map.empty).isRight)
  }

  test("input file arg parses successfully") {
    assert(command.parse(List("results.txt"), Map.empty).isRight)
  }

  test("--output-file flag parses successfully") {
    assert(command.parse(List("--output-file", "out.txt"), Map.empty).isRight)
  }

  test("unknown flag is rejected") {
    assert(command.parse(List("--unknown"), Map.empty).isLeft)
  }

  // --- End-to-end pipeline wiring ---

  test("wires input file through the full pipeline to output file") {
    for
      inputFile  <- IO.blocking(Files.createTempFile("input", ".txt"))
      outputFile <- IO.blocking(Files.createTempFile("output", ".txt"))
      _          <-
        IO.blocking(Files.writeString(inputFile, "Tarantulas 1, FC Awesome 0\nLions 3, Snakes 3"))
      exitCode <- run(inputFile.toString, "--output-file", outputFile.toString)
      content  <- IO.blocking(Files.readString(outputFile))
    yield
      assertEquals(exitCode, ExitCode.Success)
      assert(content.contains("1. Tarantulas, 3 pts"))
  }

  test("returns error exit code for invalid input file") {
    for
      inputFile <- IO.blocking(Files.createTempFile("input", ".txt"))
      _         <- IO.blocking(Files.writeString(inputFile, "not a valid line"))
      exitCode  <- run(inputFile.toString)
    yield assertEquals(exitCode, ExitCode.Error)
  }
