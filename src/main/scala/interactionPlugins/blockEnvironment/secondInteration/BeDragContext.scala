package interactionPlugins.blockEnvironment.secondInteration

import contentmanagement.datastructures.tree.nodeImpl.NodeBasedTreePosition

sealed trait BeDragPayload

object BeDragPayload {
  final case class PaletteBlock(entry: BePaletteEntry) extends BeDragPayload
  final case class WorkspaceBlock(position: NodeBasedTreePosition) extends BeDragPayload
}

class BeDragContext {
  private var payload: Option[BeDragPayload] = None

  def startPaletteDrag(entry: BePaletteEntry): Unit = {
    payload = Some(BeDragPayload.PaletteBlock(entry))
  }

  def startWorkspaceDrag(position: NodeBasedTreePosition): Unit = {
    payload = Some(BeDragPayload.WorkspaceBlock(position))
  }

  def consumePayload(): Option[BeDragPayload] = {
    val res = payload
    payload = None
    res
  }

  def cancelDrag(): Unit = {
    payload = None
  }

  def peek: Option[BeDragPayload] = payload
}
