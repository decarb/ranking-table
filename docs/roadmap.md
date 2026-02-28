# Roadmap — 5 Sequential PRs

Each PR is independently shippable. No PR depends on a later one.

---

## PR 1: Strengthen pre-commit workflow documentation ✅

### Context

The CLAUDE.md workflow says "run fix, fmt, test, then commit" but doesn't mention staging changes produced by fix/fmt. This has led to commits missing reformatted files. The PR template also lacks a staging check.

### Changes

**`CLAUDE.md`** — Rewrite Workflow section to make staging explicit:

```
## Workflow

Before committing, always run in this order:

1. `scala-cli fix --power .` — lint/fix
2. `scala-cli fmt .` — format
3. `scala-cli test .` — all tests must pass
4. Check that `CLAUDE.md` and `docs/` are consistent with any code changes
5. `git add` all modified files (code, docs, formatted sources)
6. `git status` — confirm everything is staged, nothing unexpected
7. `git commit` — only if all steps above succeed
```

**`.github/pull_request_template.md`** — Add staging verification:

```markdown
## Summary

-

## Checklist

- [ ] `scala-cli fix --power .` — passes clean
- [ ] `scala-cli fmt .` — no reformatting needed
- [ ] All changes staged — `git status` shows no unstaged modifications
- [ ] `scala-cli test .` — all tests pass
- [ ] `CLAUDE.md` and `docs/` updated if needed
```

### Verification

- Read both files after editing to confirm clarity
- Run the full workflow on the branch to validate it end-to-end

---

## PR 2: Output file support ✅

### Context

Currently output goes to stdout only. Adding `--output-file <file>` allows users to write results to a file. This also modularises the output side-effect in preparation for PR 3 (interactive stdin).

### Changes

**`src/.../Main.scala`** — Add decline option and output writer:

- Add `outputFileOpt: Opts[Option[Path]]` — optional `--output-file` / `-o` flag
- Combine with `inputFileOpt` in `main` via `(inputFileOpt, outputFileOpt).mapN`
- Extract output writing to a private method:
  ```scala
  private def writeOutput(lines: List[String], maybeFile: Option[Path]): IO[Unit] =
    maybeFile match
      case Some(path) =>
        IO.blocking {
          val pw = new java.io.PrintWriter(path.toFile)
          try lines.foreach(pw.println)
          finally pw.close()
        }
      case None =>
        lines.traverse_(IO.println)
  ```
- Replace `output.traverse_(IO.println)` with `writeOutput(output, maybeOutput)`

**`CLAUDE.md`** — Update Commands section to document `--output-file`:
```bash
# Run with output file
scala-cli run . -- results.txt --output-file rankings.txt
```

### Files modified

- `src/io/github/decarb/rankingtable/Main.scala`
- `CLAUDE.md`

### Verification

- `scala-cli run . -- results.txt` — still prints to stdout
- `scala-cli run . -- results.txt --output-file out.txt` — writes to file, verify contents
- `scala-cli run . -- --help` — shows both args in help text
- `scala-cli test .` — existing tests still pass

---

## PR 3: Interactive stdin prompt loop ✅

### Context

When no input file is given and stdin is a TTY, the user gets no feedback — they just type into a void and hit Ctrl+D. This PR adds a friendly prompt loop: the user enters one game per line, and an empty line finishes input and shows results.

### Changes

**`src/.../Main.scala`** — Modify `readLines(None)` path:

- When `maybeFile` is `None`, detect if stdin is a TTY using `System.console() != null`
- If TTY (interactive): run a prompt loop
  ```scala
  private def readLinesInteractive: IO[List[String]] =
    IO.println("Enter game results (empty line to finish):") *>
      IO.blocking {
        val console = System.console()
        Iterator.continually(console.readLine("> "))
          .takeWhile(line => line != null && line.nonEmpty)
          .toList
      }
  ```
- If not TTY (piped): keep current `Source.stdin` behaviour
- Update `readLines`:
  ```scala
  private def readLines(maybeFile: Option[Path]): IO[List[String]] =
    maybeFile match
      case Some(path) => readLinesFromFile(path)
      case None =>
        IO.blocking(System.console()).flatMap {
          case null    => readLinesFromStdin
          case _       => readLinesInteractive
        }
  ```

**`CLAUDE.md`** — Update Commands section to mention interactive mode:
```bash
# Run (interactive — enter games one at a time)
scala-cli run .
```

### Files modified

- `src/io/github/decarb/rankingtable/Main.scala`
- `CLAUDE.md`

### Verification

- `scala-cli run .` in a terminal — should show prompt, accept lines, empty line finishes
- `echo "Lions 3, Snakes 3" | scala-cli run .` — piped mode still works (no prompts)
- `scala-cli run . -- results.txt` — file mode unaffected
- `scala-cli test .` — existing tests still pass

---

## PR 4: Inline validation in interactive mode ✅

### Context

In interactive mode (PR 3), lines are collected as raw strings and only parsed once the user signals completion with an empty line. A malformed line on entry 3 of 10 is only caught after entry 10 — the user must start over. This PR validates each line immediately on entry, re-prompting with a clear error message so the user can fix mistakes in place.

### Changes

**`src/.../Main.scala`** — Thread the parser into the interactive read loop:

- Change `readLinesInteractive` signature to accept `InputParser[IO]`
- Replace the `IO.blocking` iterator with a recursive `IO` loop using `flatMap`
- On each line: attempt `parser.parseLine(line)`; on `Left` print the error and re-prompt; on `Right` accumulate and continue
- Pass the parser from `main` down through `readLines` to `readLinesInteractive`

```scala
private def readLoop(
  console: java.io.Console,
  parser:  InputParser[IO],
  acc:     List[String]
): IO[List[String]] =
  IO.blocking(console.readLine("> ")).flatMap {
    case null | "" => IO.pure(acc.reverse)
    case line =>
      parser.parseLine(line).attempt.flatMap {
        case Right(_) => readLoop(console, parser, line :: acc)
        case Left(e)  =>
          IO.println(s"  Error: ${e.getMessage}. Try again.") *>
            readLoop(console, parser, acc)
      }
  }
```

### Files modified

- `src/io/github/decarb/rankingtable/Main.scala`

### Verification

- Enter a malformed line in interactive mode — error shown, prompt repeats
- Enter a valid line after a bad one — accepted, loop continues
- Empty line after valid entries — results printed correctly
- Piped and file modes unaffected
- `scala-cli test .` — all tests pass

---

## PR 5: Test improvements (separate session) ✅

### Context

Test coverage gaps exist: Main.scala is untested, IntegrationSuite actually tests Program (not Main), and there's no dedicated ProgramSuite. Parked for a separate session to research munit styles first.

### Changes

**Rename** `test/.../IntegrationSuite.scala` → `test/.../ProgramSuite.scala`
- Keep existing tests (they test `Program.make[IO]` directly)
- Rename the class to `ProgramSuite`

**New file** `test/.../IntegrationSuite.scala`
- Test Main's CLI wiring via `Main.main.parse(args)` (decline provides this)
- Test cases:
  - Valid file arg parses and runs successfully
  - Missing file arg falls back (no error)
  - `--output-file` flag parses correctly (if PR 2 is merged)
  - `--help` produces output without error

**Munit style research** needed before implementation — to be done in that session.

### Files modified

- `test/io/github/decarb/rankingtable/IntegrationSuite.scala` (rename → ProgramSuite)
- `test/io/github/decarb/rankingtable/IntegrationSuite.scala` (new — tests Main)

### Verification

- `scala-cli test .` — all old + new tests pass
- Confirm test names display cleanly in output

---

## PR 6: Dockerfile + release workflow

TODO: Understand tags a little better to ensure that releases cant just randomly be tagged by anyone.
TODO: How do we test the tagging release process?

### Context

Not everyone has scala-cli installed. A Dockerfile provides universal build/run capability. A GitHub Actions release workflow builds a Docker image when a version tag is manually created, avoiding release spam from every merge.

### Changes

**`Dockerfile`** (new) — Multi-stage build:
```dockerfile
FROM virtuslab/scala-cli:latest AS builder
WORKDIR /app
COPY . .
RUN scala-cli package --assembly . -o /app/ranking-table.jar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/ranking-table.jar .
ENTRYPOINT ["java", "-jar", "ranking-table.jar"]
```

**`.github/workflows/release.yml`** (new) — Triggered on tag push:
```yaml
name: Release
on:
  push:
    tags: ['v*']

jobs:
  release:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: coursier/cache-action@v6
      - uses: VirtusLab/scala-cli-setup@v1
        with:
          jvm: temurin:21
      - name: Build fat JAR
        run: scala-cli package --assembly . -o ranking-table.jar
      - name: Create GitHub Release
        uses: softprops/action-gh-release@v2
        with:
          files: ranking-table.jar
```

Versioning: manual tags (`git tag v1.0.0 && git push --tags`) trigger the workflow. No auto-increment — you control when releases happen.

**`CLAUDE.md`** — Add Docker section to Commands:
```bash
# Docker build
docker build -t ranking-table .

# Docker run (file)
docker run --rm -v $(pwd)/results.txt:/app/results.txt ranking-table results.txt

# Docker run (stdin pipe)
cat results.txt | docker run --rm -i ranking-table
```

**`docs/`** — Add `docs/release.md` documenting the release process (tag creation, what the workflow does).

### Files modified/created

- `Dockerfile` (new)
- `.github/workflows/release.yml` (new)
- `docs/release.md` (new)
- `CLAUDE.md`

### Verification

- `docker build -t ranking-table .` — builds successfully
- `docker run --rm -v $(pwd)/results.txt:/app/results.txt ranking-table results.txt` — correct output
- Verify release workflow YAML is valid (act or manual review)

---

## Summary

| PR | Scope | Files | Status |
|----|-------|-------|--------|
| 1  | Workflow docs | `CLAUDE.md`, `.github/pull_request_template.md` | ✅ Done |
| 2  | Output file support | `Main.scala`, `CLAUDE.md` | ✅ Done |
| 3  | Interactive stdin | `Main.scala`, `CLAUDE.md` | ✅ Done |
| 4  | Inline interactive validation | `Main.scala` | ✅ Done |
| 5  | Test improvements | `IntegrationSuite` rename + new *(separate session)* | ✅ Done |
| 6  | Docker + release | `Dockerfile`, `release.yml`, `docs/release.md`, `CLAUDE.md` | Pending |
