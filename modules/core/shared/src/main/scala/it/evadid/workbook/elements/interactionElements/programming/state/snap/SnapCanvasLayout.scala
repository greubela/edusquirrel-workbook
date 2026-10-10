package it.evadid.workbook.elements.interactionElements.programming.state.snap

/** One top-level Snap `<script x y>` stack, including whether it starts with a green-flag hat. */
final case class SnapCanvasScript(x: Int, y: Int, callCount: Int, hasReceiveGo: Boolean = true)

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

  def single(x: Int = 156, y: Int = 66, callCount: Int): SnapCanvasLayout =
    if callCount <= 0 then empty else SnapCanvasLayout(List(SnapCanvasScript(x, y, callCount)))
}
