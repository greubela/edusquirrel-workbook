package it.evadid.workbook.elements.interactionElements.programming

import upickle.default.{ReadWriter, macroRW, read, write}

/** One top-level Snap `<script x y>` stack (hat script or loose orphan). */
final case class SnapCanvasScript(x: Int, y: Int, callCount: Int)

object SnapCanvasScript {
  given ReadWriter[SnapCanvasScript] = macroRW
}

/**
 * Canvas layout sidecar for ProgrammingExercise persistence.
 * Call counts partition the flat BeProgram call list into separate Snap scripts.
 */
final case class SnapCanvasLayout(scripts: List[SnapCanvasScript] = Nil) {
  def isEmpty: Boolean = scripts.isEmpty

  def toJson: String = write(scripts)

  def fingerprint: String = toJson
}

object SnapCanvasLayout {
  val empty: SnapCanvasLayout = SnapCanvasLayout(Nil)

  given ReadWriter[SnapCanvasLayout] = macroRW

  def fromJson(json: String): SnapCanvasLayout =
    scala.util.Try(SnapCanvasLayout(read[List[SnapCanvasScript]](json))).getOrElse(empty)

  def single(x: Int = 156, y: Int = 66, callCount: Int): SnapCanvasLayout =
    if callCount <= 0 then empty else SnapCanvasLayout(List(SnapCanvasScript(x, y, callCount)))
}
