package interactionPlugins.turtleEnvironment

sealed trait TurtleDragPayload

object TurtleDragPayload {
  case class PaletteBlock(definition: TurtleBlockDefinition) extends TurtleDragPayload
  case class EditorBlockGroup(
    blocks: List[TurtleStructuredBlock],
    path: List[TurtlePathSegment],
    index: Int
  ) extends TurtleDragPayload
}

class TurtleBlockDragContext {
  private var payload: Option[TurtleDragPayload] = None

  def startPaletteDrag(definition: TurtleBlockDefinition): Unit = {
    payload = Some(TurtleDragPayload.PaletteBlock(definition))
  }

  def startEditorDrag(blocks: List[TurtleStructuredBlock], path: List[TurtlePathSegment], index: Int): Unit = {
    payload = Some(TurtleDragPayload.EditorBlockGroup(blocks, path, index))
  }

  def consumePayload(): Option[TurtleDragPayload] = {
    val res = payload
    payload = None
    res
  }

  def cancelDragIfNecessary(): Unit = {
    payload = None
  }

  def peek: Option[TurtleDragPayload] = payload
}
