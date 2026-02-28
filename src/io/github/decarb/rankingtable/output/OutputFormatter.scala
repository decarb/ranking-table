package io.github.decarb.rankingtable.output

import io.github.decarb.rankingtable.domain.RankedEntry

trait OutputFormatter:
  def format(entries: List[RankedEntry]): List[String]

object OutputFormatter:

  def make: OutputFormatter = new Live

  final private class Live extends OutputFormatter:

    def format(entries: List[RankedEntry]): List[String] =
      entries.map(formatEntry)

    private def formatEntry(entry: RankedEntry): String =
      val unit = if entry.points == 1 then "pt" else "pts"
      s"${entry.rank}. ${entry.team.value}, ${entry.points} $unit"
