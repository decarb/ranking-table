# Proposed: Input Validation Improvements

Identified gaps in `InputParser` and their recommended resolutions. These are ready to implement
in a follow-up session.

## Summary

| # | Issue | Severity | Fix |
|---|-------|----------|-----|
| 1 | Whitespace in team names | Bug | Trim line and extracted name |
| 2 | Negative scores accepted | Bug | Reject scores below zero |
| 3 | Empty team name after trim | Edge case | Guard on empty string post-trim |
| 4 | Case-sensitive team matching | By design | Document, do not normalise |

---

## 1. Whitespace in team names

**Location:** `InputParser.Live` — `parseLineE` and `parseTeamScore`

**Problem:** Leading or trailing whitespace on a line, or extra spaces around the team name, are
silently included in the extracted `TeamName`. Two entries for the same team that differ only by
surrounding whitespace are treated as separate teams by `RankingCalculator`.

Additionally, trailing whitespace on the away-side string causes `lastIndexOf` to find the
trailing space rather than the space before the score, making the extracted `scoreStr` empty —
which produces a confusing parse error rather than a clear message.

**Fix:**

```scala
// parseLineE — trim the full line before splitting
private def parseLineE(line: String): Either[Throwable, GameResult] =
  line.trim.split(", ", 2).toList match
    ...

// parseTeamScore — trim the extracted name
val name = s.substring(0, lastSpace).trim
```

**Tests to add:**

```scala
test("trim leading and trailing whitespace from line") {
  parser.parseLine("  Lions 3, Snakes 1  ").map { r =>
    assertEquals(r.homeTeam, TeamName("Lions"))
    assertEquals(r.awayTeam, TeamName("Snakes"))
  }
}

test("trim whitespace from team names") {
  parser.parseLine("  FC Awesome  3,   Snakes 1").map { r =>
    assertEquals(r.homeTeam, TeamName("FC Awesome"))
  }
}
```

---

## 2. Negative scores accepted silently

**Location:** `InputParser.Live` — `parseTeamScore`

**Problem:** `scoreStr.toIntOption` accepts any integer. `"-3".toIntOption` returns `Some(-3)`,
so `"Lions -1, Snakes 3"` parses successfully and is scored as a Lions loss. A negative score
is nonsensical and should be rejected at the boundary.

**Fix:**

```scala
scoreStr.toIntOption.filter(_ >= 0) match
  case Some(n) => Right((TeamName(name), Score(n)))
  case None    => Left(ParseError(s"Invalid score '$scoreStr' in: '$s'"))
```

**Tests to add:**

```scala
test("fail on negative score") {
  parser.parseLine("Lions -1, Snakes 3").attempt.map { result =>
    assert(result.isLeft)
  }
}
```

---

## 3. Empty team name after trim

**Location:** `InputParser.Live` — `parseTeamScore`

**Problem:** A string like `"   3"` passes the `lastSpace <= 0` guard (lastSpace is 2, not ≤ 0),
producing `name = ""` after `.trim`. `TeamName("")` is currently accepted.

**Fix:** Add an emptiness check after trimming:

```scala
val name = s.substring(0, lastSpace).trim
if name.isEmpty then
  Left(ParseError(s"Expected 'TeamName score' but got: '$s'"))
else
  ...
```

**Tests to add:**

```scala
test("fail on whitespace-only team name") {
  parser.parseLine("   3, Snakes 1").attempt.map { result =>
    assert(result.isLeft)
  }
}
```

---

## 4. Case-sensitive team matching — document, do not normalise

**Location:** `RankingCalculator.Live` — `computeStandings`

**Problem:** `Map[TeamName, Int]` uses case-sensitive string equality. `"Lions"`, `"lions"`, and
`"LIONS"` are three distinct keys. Inconsistently cased input for the same team produces multiple
separate entries, each with partial results.

**Why not to normalise:**

| Approach | Problem |
|---|---|
| `.toLowerCase` | `"FC Awesome"` → `"fc awesome"` in output |
| Title case | `"FC Awesome"` → `"Fc Awesome"` — wrong for abbreviations |
| Normalise for lookup, display first-seen | Correct but adds meaningful complexity |

The test spec is silent on casing and the sample data is consistent throughout. Normalising
correctly for all team name formats (abbreviations, hyphenated names, apostrophes) is non-trivial
and out of scope for the current specification.

**Resolution:** Document the behaviour in `README.md`:

> Team names are matched case-sensitively. `Lions` and `lions` are treated as separate teams.
> Input must use consistent casing for the same team across all lines.

Add to the Input format section of `README.md`.
