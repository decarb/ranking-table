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

  // --- End-to-end file I/O ---

  test("reads input file and writes correct output to file") {
    val lines = List(
      "Lions 3, Snakes 3",
      "Tarantulas 1, FC Awesome 0",
      "Lions 1, FC Awesome 1",
      "Tarantulas 3, Snakes 1",
      "Lions 4, Grouches 0"
    )
    for
      inputFile  <- IO.blocking(Files.createTempFile("input", ".txt"))
      outputFile <- IO.blocking(Files.createTempFile("output", ".txt"))
      _          <- IO.blocking(Files.writeString(inputFile, lines.mkString("\n")))
      exitCode   <- run(inputFile.toString, "--output-file", outputFile.toString)
      content    <- IO.blocking(Files.readString(outputFile))
    yield
      assertEquals(exitCode, ExitCode.Success)
      assert(content.contains("1. Tarantulas, 6 pts"))
      assert(content.contains("2. Lions, 5 pts"))
      assert(content.contains("3. FC Awesome, 1 pt"))
      assert(content.contains("3. Snakes, 1 pt"))
      assert(content.contains("5. Grouches, 0 pts"))
  }

  test("propagates parse error from input file") {
    for
      inputFile <- IO.blocking(Files.createTempFile("input", ".txt"))
      _         <- IO.blocking(Files.writeString(inputFile, "not a valid line"))
      result    <- run(inputFile.toString).attempt
    yield assert(result.isLeft)
  }
