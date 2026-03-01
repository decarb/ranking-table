package io.github.decarb.rankingtable.output

import io.github.decarb.rankingtable.domain.RankedEntry

trait LineRenderable[A]:
  def renderLine(a: A): String

object LineRenderable:
  def apply[A](using lr: LineRenderable[A]): LineRenderable[A] = lr

  given LineRenderable[RankedEntry] with
    def renderLine(entry: RankedEntry): String =
      val unit = if entry.points == 1 then "pt" else "pts"
      s"${entry.rank}. ${entry.team.value}, ${entry.points} $unit"
