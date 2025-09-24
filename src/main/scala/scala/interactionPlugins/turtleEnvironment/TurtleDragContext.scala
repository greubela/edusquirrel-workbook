package scala.interactionPlugins.turtleEnvironment

sealed trait TurtleDragPayload

object TurtleDragPayload {
  case class PaletteBlock(definition: TurtleBlockDefinition) extends TurtleDragPayload
  case class EditorBlockGroup(blocks: List[TurtleBlock], originalIndex: Int, onCancel: () => Unit) extends TurtleDragPayload
}

class TurtleBlockDragContext {
  private var payload: Option[TurtleDragPayload] = None

  def startPaletteDrag(definition: TurtleBlockDefinition): Unit = {
    payload = Some(TurtleDragPayload.PaletteBlock(definition))
  }

  def startEditorDrag(blocks: List[TurtleBlock], originalIndex: Int, onCancel: () => Unit): Unit = {
    payload = Some(TurtleDragPayload.EditorBlockGroup(blocks, originalIndex, onCancel))
  }

  def consumePayload(): Option[TurtleDragPayload] = {
    val res = payload
    payload = None
    res
  }

  def cancelDragIfNecessary(): Unit = {
    payload match {
      case Some(TurtleDragPayload.EditorBlockGroup(_, _, onCancel)) => onCancel()
      case _                                                        => ()
    }
    payload = None
  }

  def peek: Option[TurtleDragPayload] = payload
}
