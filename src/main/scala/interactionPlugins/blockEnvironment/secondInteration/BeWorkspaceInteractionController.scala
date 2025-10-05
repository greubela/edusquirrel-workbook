package interactionPlugins.blockEnvironment.secondInteration

import interactionPlugins.blockEnvironment.secondInteration.BeDragPayload.PaletteBlock

class BeWorkspaceInteractionController(
  dragContext: BeDragContext,
  workspaceState: BeWorkspaceState
) {

  def handleWorkspaceDrop(): Unit = {
    dragContext.consumePayload().foreach {
      case PaletteBlock(entry) => workspaceState.insertPaletteEntry(entry)
      case _ => ()
    }
  }
}
