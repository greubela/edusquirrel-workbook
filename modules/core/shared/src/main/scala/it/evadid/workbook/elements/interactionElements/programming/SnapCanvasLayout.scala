package it.evadid.workbook.elements.interactionElements.programming

/** One top-level Snap `<script x y>` stack (hat script or loose orphan). */
final case class SnapCanvasScript(x: Int, y: Int, callCount: Int)

/**
 * Derived Snap canvas layout (script positions / statement counts).
 * Positions live natively in Snap XML; this type is kept for XML→AST derivation
 * and Python-apply writeback.
 */
final case class SnapCanvasLayout(scripts: List[SnapCanvasScript] = Nil) {
  def isEmpty: Boolean = scripts.isEmpty
}

object SnapCanvasLayout {
  val empty: SnapCanvasLayout = SnapCanvasLayout(Nil)

  /** Where a script lands when Python did not record a position. */
  val DefaultX = 156
  val DefaultY = 66

  /** Rough block height of one non-empty Python line, used only to stack new scripts. */
  val LineHeightPx = 24

  /** Gap between a script and the one stacked under it. */
  val ScriptGap = 32

  def single(x: Int = DefaultX, y: Int = DefaultY, callCount: Int): SnapCanvasLayout =
    if callCount <= 0 then empty else SnapCanvasLayout(List(SnapCanvasScript(x, y, callCount)))

  /**
   * Assign canvas positions.
   *
   * Each entry is `(position, callCount, nonEmptyLineCount)`. `None` means the
   * script has no recorded position: the first such script starts at
   * [[DefaultX]], [[DefaultY]], and every later one is left-aligned under its
   * predecessor. Explicit positions are kept. Scripts with `callCount <= 0`
   * are dropped. Overlap with an explicit script further down is accepted.
   */
  def placed(scripts: List[(Option[(Int, Int)], Int, Int)]): SnapCanvasLayout = {
    val placedScripts = List.newBuilder[SnapCanvasScript]
    var previous: Option[(Int, Int, Int)] = None
    scripts.foreach { case (position, callCount, lineCount) =>
      if callCount > 0 then
        val (x, y) = position.getOrElse {
          previous match
            case None =>
              (DefaultX, DefaultY)
            case Some((prevX, prevY, prevLines)) =>
              (prevX, prevY + estimatedHeight(prevLines) + ScriptGap)
        }
        placedScripts += SnapCanvasScript(x, y, callCount)
        previous = Some((x, y, lineCount))
    }
    SnapCanvasLayout(placedScripts.result())
  }

  def estimatedHeight(lineCount: Int): Int =
    math.max(lineCount, 1) * LineHeightPx
}
